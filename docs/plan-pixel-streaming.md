# Plan: Stream DICOM pixel data instead of loading into heap

**Repository:** `danielmarcus/xnat-monorepo`
**Base:** `main`
**Branch:** `feat/pixel-streaming` (recommended; create new)
**Scope:** Refactor XNAT's DICOM read paths to use `dcm4che`'s `IncludeBulkData.URI` mode by default, retire the `NativeDicomPreCompressor` workaround, switch pixel-touching paths to per-frame streaming.

This is the "right fix" referenced in `apps/web/.../NativeDicomPreCompressor.java` — the existing pre-compressor is a workaround for the 2 GB `byte[]` limit that dcm4che hits when parsing native DICOMs eagerly. The proper solution is to never materialize the full pixel buffer in the first place, which dcm4che already supports via `IncludeBulkData.URI` — XNAT just doesn't use it consistently.

---

## Context (read this first)

### The problem

`DicomInputStream(file).readDataset()` (the dcm4che 5 default) loads every Value, including PixelData, into a Java `byte[]`. Java arrays have a 31-bit signed length, so any single Value > ~2.15 GB throws. For typical clinical DICOMs the Value is well under that, but multi-frame native ultrasound and large CT/MR studies routinely cross it.

Beyond the > 2 GB crash, the eager loading is **wasteful** for the dominant code paths. Most XNAT operations on DICOM (ingest, prearchive→archive flush, manifest rebuild, header anonymization, session-builder grouping) only need header tags. Materializing several GB of pixel `byte[]` to read 200 bytes of header is what makes XNAT's heap requirements scale with the largest file ever ingested rather than with concurrency.

### Today's workaround

`apps/web/src/main/java/org/nrg/xnat/archive/NativeDicomPreCompressor.java` re-encodes any native-format DICOM > 2 GB to JPEG 2000 Lossless on disk before downstream code reads it. JPEG 2000 stores pixel data as encapsulated fragments, which dcm4che reads frame-by-frame, never building a single oversized `byte[]`. Tradeoffs:

- **Irreversible on-disk transformation** — original bytes are gone. Lossless pixels but not byte-exact.
- **Per-file CPU cost** for the J2K encode (jai_imageio's pure-Java JJ2000 codec, ~20-60 s per multi-GB file).
- **Only fires above 2 GB** — files between 100 MB and 2 GB still load fully into heap and slow down ingest, just don't crash.
- **Adds JPEG 2000 codec dependency** to the WAR.

The pre-compressor is a working surgical patch but it's a workaround, not a fix.

### What dcm4che 5 already supports

`org.dcm4che3.io.DicomInputStream` exposes `setIncludeBulkData()` with three modes:

| Mode | Behaviour |
|---|---|
| `IncludeBulkData.YES` (default) | Load every Value into `byte[]` — the eager path that hits the 2 GB ceiling. |
| `IncludeBulkData.URI` | Replace bulk attributes (PixelData, OB/OW > threshold) with a `BulkData(uri, offset, length)` placeholder. The in-memory `Attributes` object holds the reference; consumers call `attribute.getValue()` to materialize on demand. |
| `IncludeBulkData.NO` | Skip bulk attributes entirely — they don't appear in the `Attributes` object at all. |

`URI` is the right default for XNAT — header reads stay header reads, pixel-touching code can still get pixels (per-frame, not all at once), and large files stop crashing.

### Read paths that need refactoring

Roughly, every `new DicomInputStream(...)` site in the codebase. Inventory will be the first phase. Expected categories:

- **`apps/web` archive / prearchive ingest** — header-only reads (~80% of paths).
- **`libs/dicom-xnat/*` (sop, util, mx)** — DICOM ingest plumbing, mix of header and pixel paths.
- **`libs/dicomtools`** — utility wrappers, mostly header.
- **`libs/session-builders`** — groups files into series; header-only.
- **`libs/prearc-importer`** — prearchive ingest; header-only for its own logic.
- **`libs/dicom-edit4`, `libs/dicom-edit6`** — anonymization scripts; mix. Most rules touch header tags only; some redaction rules touch pixels.
- **`libs/dicom-image-utils`** — pixel-touching by definition.
- **`libs/ecat4xnat`** — PET-format conversion; can probably avoid pixel materialization.

The tricky paths are the ones in `dicom-edit6` and `dicom-image-utils` that currently call `attribute.getBytes(Tag.PixelData)` directly. Those need to switch to per-frame access via dcm4che's `Transcoder.readFrame()` or a `BulkData`-aware iterator.

---

## Phase ordering and rationale

1. **Phase A — Audit** (gates everything; no code changes)
2. **Phase B — Switch metadata-only paths to `IncludeBulkData.URI`** (high-impact, low-risk)
3. **Phase C — Refactor pixel-touching paths to per-frame streaming**
4. **Phase D — Retire `NativeDicomPreCompressor`** (depends on B + C being complete)
5. **Phase E — Verification + benchmarks**

Each phase has a verification gate. Phase B alone delivers most of the heap and concurrency wins; Phase C is needed to make Phase D possible. Phases B and C can land as separate PRs; do not bundle.

---

# Phase A — Audit

## Goal

Inventory every `DicomInputStream` instantiation in the codebase and classify by usage. This is required input for both Phase B and Phase C planning. **No code changes.**

## Tasks

```bash
# Find every read-side DICOM entry point
grep -rn "new DicomInputStream" --include="*.java" \
  apps/ libs/ build-tools/ > /tmp/dis-sites.txt

grep -rn "DicomFiles.readMetadata\|readDataset" --include="*.java" \
  apps/ libs/ build-tools/ >> /tmp/dis-sites.txt

# Classify each site:
#   M = metadata-only (only calls getString/getInt/contains/getDate)
#   P = pixel-touching (calls getBytes/getValue on PixelData/OW/OB/OF)
#   ? = unclear, needs reading
```

For each site, document in a spreadsheet (`docs/dicom-stream-audit.md`) with columns:

| File:line | Caller class | Category (M/P/?) | Notes |
|---|---|---|---|
| ... | ... | ... | ... |

Aim for ~80% confidence on M-vs-P classification. The unsure ones go to Phase C planning.

## Phase A verification gate

- [ ] `docs/dicom-stream-audit.md` exists and lists every `DicomInputStream` site.
- [ ] Every site is classified M, P, or ?.
- [ ] Total count agrees with `grep -c` against the source.
- [ ] PR title: `docs: audit DICOM read paths for streaming refactor`.

---

# Phase B — Switch metadata-only paths to `IncludeBulkData.URI`

## Goal

Every M-classified site from Phase A switches to URI mode. Header-only XNAT operations stop allocating multi-GB pixel buffers. Heap footprint per file drops from `O(file_size)` to `O(header_size)` ≈ 5 KB.

## Approach

**Add a helper in `libs/dicom-xnat-util` (or wherever the natural common location is — confirm in Phase A):**

```java
public final class DicomReader {
    /**
     * Reads a DICOM file's metadata without materializing pixel data.
     * BulkData references are URI-backed; consumers can call getValue()
     * to lazily load specific Values when needed.
     */
    public static Attributes readMetadata(File file) throws IOException {
        try (DicomInputStream dis = new DicomInputStream(file)) {
            dis.setIncludeBulkData(IncludeBulkData.URI);
            return dis.readDataset(-1, -1);
        }
    }

    /**
     * Reads a DICOM file's metadata, EXCLUDING bulk attributes entirely.
     * Use when you don't need pixel data and don't want even the URI
     * placeholder. Cheaper than URI mode but consumers cannot retrieve
     * pixels later from the returned Attributes.
     */
    public static Attributes readHeaderOnly(File file) throws IOException {
        try (DicomInputStream dis = new DicomInputStream(file)) {
            dis.setIncludeBulkData(IncludeBulkData.NO);
            return dis.readDataset(-1, -1);
        }
    }
}
```

**Replace each M-classified `new DicomInputStream(...)` site** with `DicomReader.readMetadata(file)` or `DicomReader.readHeaderOnly(file)` per the site's needs.

## Tasks

For each M site from the Phase A audit:

1. Confirm classification by inspecting the consumer code — does anything downstream reach into `attrs.getBytes(Tag.PixelData)`? If yes, it's actually P; reclassify.
2. Replace the constructor with the helper.
3. Run `:<module>:test` for that module.
4. Spot-check the replaced site with a 100 MB native DICOM in a unit test (assert heap stays flat).

Group commits by module for clean history (`apps/web: switch ingest to URI mode`, `libs/session-builders: switch to URI mode`, etc.).

## Files (likely; confirm via Phase A)

- `apps/web/src/main/java/org/nrg/xnat/archive/*.java`
- `apps/web/src/main/java/org/nrg/xnat/services/archive/*.java`
- `libs/session-builders/src/main/java/.../SessionBuilder.java`
- `libs/prearc-importer/src/main/java/.../*.java`
- `libs/dicom-xnat-mx/src/main/java/.../*.java`
- `libs/dicomtools/src/main/java/.../*.java`

## Phase B verification gate

- [ ] `./gradlew build` clean
- [ ] `./gradlew test` passes (no new failures vs main)
- [ ] All M-classified sites in the audit are migrated; audit doc updated to mark them ✅
- [ ] Smoke test: ingest a 100 MB native multiframe DICOM via `docker compose up -d` → archive succeeds, archive logs show no `byte[]` allocations near the file size (use `-XX:+PrintGCDetails` or just `jstat`).
- [ ] Smoke test: ingest a 50 MB study of 100 small DICOMs concurrently (4 parallel uploads) without OOM. Pre-Phase-B baseline should also work for context; the win here is the heap headroom remaining.
- [ ] No regression in DICOM ingest correctness — JUnit tests in `libs/dicom-xnat-*`, `libs/session-builders`, `libs/prearc-importer` all green.

---

# Phase C — Refactor pixel-touching paths to per-frame streaming

## Goal

Every P-classified site stops loading PixelData as a single `byte[]`. Either:

- Uses `dcm4che3.imageio.codec.Transcoder` to iterate frames (`readFrame()` / `compressFrame()`), or
- Memory-maps the file via `BulkData.toFileChannel()` and reads the relevant frame's byte range only.

This unblocks Phase D (pre-compressor retirement) and removes the heap-bound on anonymization that touches pixels.

## Sub-phases

### C.1 — Anonymization scripts that don't touch pixels

These should work unchanged after Phase B (their `Attributes` object holds a `BulkData` reference for PixelData, but the script never reads it). Verify by running the existing dicom-edit4 and dicom-edit6 test suites — should pass without changes if Phase B was clean.

### C.2 — Anonymization scripts that DO touch pixels (pixel redaction)

The mizer's pixel-edit handler (`PixelmedPixelEditHandler` and its dcm4che counterpart) currently expects to receive a fully-loaded pixel array. Refactor to:

1. Accept a `BulkData` reference or `File` reference.
2. Open a per-frame iterator.
3. For each frame: read into a frame-sized buffer, run the redaction logic, write back to a new file.

Source files in scope:
- `libs/dicom-edit6/src/main/java/.../pixeledit/impl/PixelmedPixelEditHandler.java`
- `libs/dicom-edit6/src/main/java/.../pixeledit/impl/Dcm4cheEditHandler.java` (if present)
- `libs/dicom-image-utils/src/main/java/.../*.java` — anything that materializes a frame

The reference upstream pattern is dcm4che's `Transcoder.transcode(...)` — it's already frame-by-frame. We don't need to copy that mechanism wholesale; we just need to ensure our handlers walk it that way too.

### C.3 — Catalog-validation paths that read pixel offsets

`apps/web/.../catalog` may have code that touches pixel data for checksum validation or frame-count verification. Audit and switch to per-frame.

## Phase C verification gate

- [ ] `./gradlew build` clean
- [ ] `./gradlew test` passes
- [ ] **All four currently-`@Ignore`d `PixelmedPixelEditHandlerTest.multiframe_*` tests can be re-enabled** — their underlying root cause was always memory pressure from up-front pixel loading. (If `multiframe_evle_rgb_8bit` still OOMs after C.2, a heap or ImageJ-validator issue remains that's separate from this refactor; surface it.)
- [ ] Smoke test: ingest the 475 MB `us-evle-rgb-8bit.dcm` fixture as a real archive operation → succeeds with stable heap.
- [ ] Smoke test: anonymize a > 2 GB native DICOM with a script that includes pixel redaction → succeeds with stable heap.

---

# Phase D — Retire `NativeDicomPreCompressor`

## Goal

Once Phases B and C land, no XNAT code path materializes the full pixel `byte[]` for any file size. The pre-compressor's reason for existing is gone; remove it.

## Tasks

1. Remove every call site of `NativeDicomPreCompressor.preCompressIfNeeded(...)`.
2. Delete `apps/web/src/main/java/org/nrg/xnat/archive/NativeDicomPreCompressor.java`.
3. Delete the corresponding test class if one exists.
4. Remove `runtimeOnly("com.sun.media:jai_imageio:1.2-pre-dr-b04")` from `apps/web/build.gradle.kts:279` if no other code path needs it (likely safe — Phase A audit will confirm).
5. Add an ADR `docs/adr/0009-pixel-streaming-and-precompressor-retirement.md` documenting the refactor.

## Phase D verification gate

- [ ] Pre-compressor source file is deleted.
- [ ] No references to `NativeDicomPreCompressor` remain in the codebase (`grep -r "NativeDicomPreCompressor" .`).
- [ ] `./gradlew :apps:web:war` produces a smaller WAR (J2K codec gone, ~5-10 MB lighter — verify via `du -sh apps/web/build/libs/*.war`).
- [ ] Smoke test: ingest a > 2 GB native DICOM → succeeds without pre-compression. **Critically: file on disk after archive is byte-identical to the original** (modulo any header rewrites from anonymization).
- [ ] Smoke test: ingest a 100 MB native DICOM, then download → bytes match the original input file.
- [ ] ADR 0009 written and reviewed.

---

# Phase E — Verification + benchmarks

## Goal

Quantify the win, document it, set up regression detection.

## Tasks

### E.1 — Benchmark harness

Add `apps/web/src/test/.../IngestBenchmarkTest.java` (or similar) that measures heap-allocated bytes during a single-file ingest, parameterized by file size. Use JMH or a simpler `Runtime.getRuntime().totalMemory() - freeMemory()` snapshot before/after.

Required scenarios:
- Single 500 KB CT ingest
- Single 100 MB multiframe US ingest
- Single 1 GB native ingest (assert heap stays under, say, 50 MB attributable to the operation)
- 10 concurrent 100 MB ingests (assert peak heap doesn't exceed (10 + a few) × header_size)

Capture pre-refactor numbers from `main` for comparison. Land alongside Phase B/C work; this is what justifies the change.

### E.2 — README + ADR updates

- `README.md`: brief mention under "DICOM ingest" or "Tomcat 10" section that XNAT now streams pixel data; cite ADR 0009.
- `docs/adr/0009-...`: full architectural narrative — what the pre-compressor was, why it existed, what replaced it, byte-fidelity preservation.
- `docs/plan-tomcat10-eks-tests.md`: if it referenced the pre-compressor, update.

### E.3 — Lint rule (optional)

Add a custom checkstyle / PMD rule that flags new uses of `new DicomInputStream(...)` outside `DicomReader` or other approved locations. Prevents the eager-load pattern from creeping back in.

## Phase E verification gate

- [ ] Benchmark harness runs in CI (or as a manual `./gradlew benchmark` task) and produces a JUnit-style report.
- [ ] Heap-allocation reduction is documented (~100×) for typical sizes; OOM-elimination is documented for > 2 GB.
- [ ] ADR 0009 merged.

---

# Notes for Claude Code execution

- **Read this whole file before starting.** Phase A's audit determines the scope of B and C. Don't skip it.
- **Phase A audit lives in `docs/dicom-stream-audit.md`** — a separate file, not in this plan. Update it as you go through B and C.
- **Each phase is its own PR (or set of PRs).** Don't bundle B and C — too much surface area to review at once.
- **Run gates explicitly.** After each phase, run the gate commands and report results before starting the next phase.
- **Don't delete `NativeDicomPreCompressor` until Phase D verification passes.** It's the only thing keeping > 2 GB ingests alive today; if Phase B/C are subtly broken, you want the safety net.
- **Mind dcm4che 2 vs dcm4che 5.** The codebase uses both (`dcm4che2 = "2.0.29"` and `dcm4che5 = "5.33.1"` per `gradle/libs.versions.toml`). `IncludeBulkData` exists on **dcm4che 5's `org.dcm4che3.io.DicomInputStream` only**. Sites still using `org.dcm4che2.io.DicomInputStream` need to migrate to dcm4che 5's API as part of this work — or at least be flagged in Phase A. Some of `libs/dicomtools` is on the dcm4che 2 API; expect ~10-20 sites needing API migration alongside the URI-mode switch.
- **Backward compatibility.** External tools that read XNAT's archive don't see any change — files on disk are unaltered (this is the whole point). Internal callers of `NativeDicomPreCompressor` see a removed class — find them via `grep -r "NativeDicomPreCompressor"` before deletion.

## Test environment expectations

- Local: `cd deploy/docker-compose && docker compose up -d` — the standard local stack.
- Smoke tests: `cd smoke-tests && pytest -v` — should pass at every phase gate.
- Cloud-deploy: not affected; same WAR artifact.
- EKS-deploy: not affected; same WAR artifact.

## What's out of scope

- **Adding OpenCV bindings.** Separate decision; would speed up the rare cases where pixel data is genuinely re-encoded (only Phase C.2 redaction paths). Not required for the streaming refactor itself.
- **Replacing dcm4che 2 with dcm4che 5 across the codebase.** Big enough to warrant its own plan. Phase A may flag dcm4che-2-using sites; migrate the minimum required to land Phase B without breaking those modules.
- **Refactoring the dcm4che 2-era APIs in `libs/dicom-xnat-sop`.** Touch only what blocks Phase B/C.
- **Per-frame anonymization optimisation.** Phase C achieves correctness (works without OOM); a future PR could parallelize frame redaction across cores.
- **Bringing back the four `@Ignore`d `PixelmedPixelEditHandlerTest` tests in this PR.** Verifying they pass after Phase C is the gate; un-`@Ignore`ing them can be a follow-up cleanup.

## Why this isn't already done

The pre-compressor was a smaller, surgical fix for an immediate symptom (> 2 GB ingest crashes). Refactoring every read path is multi-week work spanning ~10 modules. It just hadn't found a sponsor. This plan is the sponsor.

## Expected impact

| Property | Before | After |
|---|---|---|
| Heap per file ingest, typical sizes | O(file_size), 100 MB - 1 GB | O(header_size), ~5 KB |
| Heap per file ingest, > 2 GB native | OOM (or pre-compress to J2K, ~30 s + irreversible re-encode) | ~5 KB; original bytes preserved |
| Concurrent ingest scaling | Bounded by `XMX / largest_file` | Bounded by core count |
| Tomcat heap requirement | ~3 GB recommended | 1-1.5 GB sufficient |
| Archive byte fidelity | Lost on > 2 GB native files | Preserved always |
| Pre-compressor + JPEG 2000 codec dependency | Required | Removed |
| `@Ignore`'d `multiframe_*` tests in `dicom-edit6` | 4 (3 codec, 1 OOM) | 3 (codec only) — the OOM one comes back |
