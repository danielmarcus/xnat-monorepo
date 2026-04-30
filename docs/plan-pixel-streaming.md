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
2. **Phase B — Test infrastructure** (fixture factory, heap-bound assertions, JMH harness, byte-checksum helpers, lint rule). Phases C–E depend on these helpers being in place.
3. **Phase C — Switch metadata-only paths to `IncludeBulkData.URI`** (was Phase B in the v1 plan)
4. **Phase D — Refactor pixel-touching paths to per-frame streaming** (was Phase C)
5. **Phase E — Retire `NativeDicomPreCompressor`** (was Phase D)
6. **Phase F — Verification + benchmarks publishing** (was Phase E)

Each phase has a verification gate. Phase C alone delivers most of the heap and concurrency wins; Phase D is needed to make Phase E possible. Phases C and D can land as separate PRs; do not bundle.

## Testing requirements (read before starting any phase)

The v1 of this plan was light on testing — leaned on "existing tests still pass" plus a few hand-described smoke checks. The expanded plan below specifies, **per phase**, the exact new test classes / methods required to pass each verification gate. The categories of test obligation:

- **Unit tests** — for new helper APIs (`DicomReader`, etc.) covering happy paths, edge cases, error modes.
- **Reference-lifecycle tests** — for `BulkData` references that outlive their parsing context.
- **Memory-bound tests** — assert heap-allocation deltas using snapshot-around-operation patterns. Helpers in Phase B.
- **Byte-identity tests** — assert post-archive on-disk file is byte-for-byte (or pixel-for-pixel) identical to input. SHA-256 based.
- **Concurrency / load tests** — assert peak-heap stays bounded under N parallel ingests.
- **Behaviour-parity tests** — pre-refactor outputs (captured as SHA-256) must match post-refactor outputs for the same inputs.
- **External-tool compatibility** — files written post-refactor still readable by tools other than dcm4che.
- **Backward-compat tests** — files in the archive that pre-date the refactor (some pre-compressed by the old `NativeDicomPreCompressor`, some not) remain readable.
- **Regression gates** — re-enabled `@Ignore`d tests count as part of the gate; the gate must explicitly check they were un-ignored, not just that the build is green.

**Implementing agent: do NOT mark a phase's gate satisfied unless every test class listed under that phase exists, has the methods specified, and is in the green test report.**

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

# Phase B — Test infrastructure

## Goal

Build the helpers, fixtures, and CI hooks that Phases C–E will depend on. Pure additive — no behaviour change to production code. This phase exists because Option 2 of the testing strategy requires every later phase to provide concrete memory and byte-identity assertions, and those need shared scaffolding.

## Deliverables

### B.1 — `LargeDicomFixtureFactory` (in `libs/test`)

Synthetic-DICOM generator so tests don't have to ship multi-GB binary fixtures via Git LFS.

**File:** `libs/test/src/main/java/org/nrg/test/dicom/LargeDicomFixtureFactory.java`

API surface:

```java
public final class LargeDicomFixtureFactory {

    /** Native (Implicit VR LE) multiframe DICOM. Frame count and dims controllable. */
    public static File createNativeMultiframe(Path outDir, String name,
                                              int frameCount, int width, int height,
                                              int bitsAllocated, int samplesPerPixel) throws IOException;

    /** Convenience: 100 MB native multiframe. */
    public static File create100MbNative(Path outDir) throws IOException;

    /** Convenience: 2.5 GB native multiframe (exceeds dcm4che's 2 GB byte[] ceiling). */
    public static File createOver2GbNative(Path outDir) throws IOException;

    /** Encapsulated (JPEG 2000 Lossless) multiframe — for legacy-readback tests. */
    public static File createJpeg2000Multiframe(Path outDir, String name,
                                                int frameCount, int width, int height) throws IOException;
}
```

Tests for the factory itself:

**File:** `libs/test/src/test/java/org/nrg/test/dicom/LargeDicomFixtureFactoryTest.java`

| Method | Asserts |
|---|---|
| `nativeMultiframe_isReadableByDcm4che5()` | factory output parses with `DicomInputStream`; PatientName/StudyInstanceUID/etc. round-trip |
| `nativeMultiframe_pixelDataMatchesProducedPattern()` | factory writes a known checksum into pixels (e.g. frame N filled with `(byte)(N % 256)`); reading back via dcm4che produces the same bytes |
| `over2Gb_actuallyExceedsCeiling()` | `Files.size(out) > 2_147_483_647L` |
| `jpeg2000_isFragmented()` | `Attributes.getValue(Tag.PixelData) instanceof Fragments` |

### B.2 — `HeapBounds` test helper (in `libs/test`)

**File:** `libs/test/src/main/java/org/nrg/test/memory/HeapBounds.java`

```java
public final class HeapBounds {

    /**
     * Runs the operation and asserts the resulting heap allocation delta
     * stays under maxAllocBytes. Forces a System.gc() before and after.
     * Reads ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().
     * Throws AssertionError if the delta exceeds the limit.
     */
    public static void assertHeapDeltaUnder(long maxAllocBytes, ThrowingRunnable op) throws Exception;

    /** Lambda variant returning a value. */
    public static <T> T assertHeapDeltaUnderReturning(long maxAllocBytes, ThrowingSupplier<T> op) throws Exception;

    /** For load tests: assert peak heap during op stays under maxPeakBytes (samples every 50 ms). */
    public static void assertHeapPeakUnder(long maxPeakBytes, ThrowingRunnable op) throws Exception;
}
```

Tests:

**File:** `libs/test/src/test/java/org/nrg/test/memory/HeapBoundsTest.java`

| Method | Asserts |
|---|---|
| `holdsBoundedAllocations()` | allocating `byte[1MB]` passes a 5 MB bound |
| `triggersFailureOnOverallocation()` | allocating `byte[100MB]` fails a 5 MB bound with a clear message |
| `peakSamplerCatchesShortlivedSpikes()` | a 200ms-lived `byte[200MB]` triggers `assertHeapPeakUnder(50_000_000)` |
| `gcReturnsBaseline()` | after `op` discards its only reference, heap delta returns within 5 MB of baseline |

### B.3 — `Sha256Checksum` helper (in `libs/test`)

For byte-identity assertions on archived DICOMs.

**File:** `libs/test/src/main/java/org/nrg/test/io/Sha256Checksum.java`

```java
public final class Sha256Checksum {

    /** SHA-256 of the entire file's bytes. */
    public static String of(File file) throws IOException;

    /** SHA-256 of just the PixelData bytes (every frame, in order). Used for
     *  pixel-fidelity assertions when headers may have changed (anon, etc). */
    public static String ofPixelData(File dicomFile) throws IOException;
}
```

Tests:

**File:** `libs/test/src/test/java/org/nrg/test/io/Sha256ChecksumTest.java`

| Method | Asserts |
|---|---|
| `identicalFilesProduceIdenticalChecksums()` | `of(a) == of(b)` when `Files.copy(a, b)` |
| `pixelDataIsolated()` | header changes don't change `ofPixelData` if pixels untouched |

### B.4 — JMH benchmark harness

**File:** `apps/web/src/jmh/java/org/nrg/xnat/bench/DicomIngestBenchmark.java`

Gradle plugin: add `id("me.champeau.jmh") version "0.7.2"` to `build-logic/src/main/kotlin/xnat-war-application.gradle.kts`.

Required benchmark methods:

| Method | Measures |
|---|---|
| `ingestSmallNative()` | 500 KB native CT slice; avg-time + heap allocation |
| `ingest100MbNative()` | 100 MB native multiframe; avg-time + heap allocation |
| `ingest1GbNative()` | 1 GB native multiframe; avg-time + heap allocation |
| `ingestOver2GbNative()` | 2.5 GB native (Phase B baseline: throws OOM; Phases C–E baseline: succeeds) |
| `ingestJpeg2000Multiframe()` | encapsulated multiframe, for compression-already-done baseline |

Each benchmark runs the actual `XnatDicomIngestService.ingest(...)` path (or whichever entry point Phase A audit identifies as the primary). Captures heap allocation via `org.openjdk.jmh.profile.GCProfiler`.

Output: `apps/web/build/reports/jmh/results.txt`. Goes into PR descriptions for Phases C/D/E.

### B.5 — Lint rule preventing eager-load reintroduction

After Phase C lands, every `new DicomInputStream(...)` outside `DicomReader` is a regression. Codify that.

**File:** `build-logic/src/main/resources/forbidden-apis-dicom-reader.txt`

```
# DICOM read paths must use org.nrg.dicom.streaming.DicomReader
# (added in Phase C of plan-pixel-streaming.md). Direct construction
# of DicomInputStream re-introduces the eager-load 2GB ceiling.
@defaultMessage Use org.nrg.dicom.streaming.DicomReader.readMetadata or readHeaderOnly
org.dcm4che3.io.DicomInputStream <init>(**)
```

Apply via `de.thetaphi.forbiddenapis` plugin in `xnat-java-library.gradle.kts`. Allow-list `DicomReader.java` itself + benchmark code.

Tests (the lint check itself is enforced as a Gradle task):

| Gate | Asserts |
|---|---|
| `./gradlew forbiddenApisMain` passes on a fresh checkout | no `new DicomInputStream` outside the allow-list |
| Synthetic `Foo.java` with `new DicomInputStream(file)` fails the check | regression-detector works |

### B.6 — CI wiring for memory-regression gate

In `.github/workflows/pr-validation.yml`:

- Add a `Run JMH benchmarks` job (separate from the unit-test job; benchmarks take 5–10 min). Runs `./gradlew jmh`, uploads results as an artifact.
- Add a comparison step that fails the build if heap allocation per ingest regresses > 20% vs the baseline stored at `apps/web/jmh/baseline.json`. Baseline updated explicitly by an annotated PR; no auto-update.

## Phase B verification gate

- [ ] `libs/test` module exports `LargeDicomFixtureFactory`, `HeapBounds`, `Sha256Checksum` with the API shapes specified.
- [ ] All four `*Test.java` files for B.1–B.3 exist and pass under `./gradlew :libs:test:test`.
- [ ] `apps/web/src/jmh/` is wired up; `./gradlew :apps:web:jmh` runs and produces a results file.
- [ ] `forbiddenApisMain` task exists and runs (will pass trivially since no calls have been migrated yet — that's expected; the rule activates downstream).
- [ ] CI workflow has the JMH job and uploads artifacts on PR validation.
- [ ] PR title: `test infra: fixture factory, heap-bound helpers, JMH harness for streaming refactor`.

---

# Phase C — Switch metadata-only paths to `IncludeBulkData.URI`

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

## Required tests for Phase C

### C.test.1 — Unit tests for the `DicomReader` helper

**File:** `libs/dicom-xnat/dicom-xnat-util/src/test/java/org/nrg/dicom/streaming/DicomReaderTest.java` (or wherever `DicomReader` lands per Phase A)

Required test methods:

| Method | Setup | Asserts |
|---|---|---|
| `readMetadata_returnsBulkDataPlaceholderForPixelData()` | 100 MB native multiframe via `LargeDicomFixtureFactory` | `attrs.getValue(Tag.PixelData) instanceof BulkData` |
| `readMetadata_doesNotMaterializePixelBytes()` | same | `HeapBounds.assertHeapDeltaUnder(5_000_000, () -> readMetadata(file))` (5 MB cap on a 100 MB file) |
| `readMetadata_pixelDataIsLazilyResolvable()` | same | after `readMetadata` returns, `((BulkData) attrs.getValue(PixelData)).toBytes(VR.OB, false)` returns the original bytes |
| `readMetadata_workOnEncapsulatedFile()` | factory's JPEG 2000 multiframe | `attrs.getValue(PixelData) instanceof Fragments` (already encapsulated; no degradation) |
| `readMetadata_throwsIOExceptionOnTruncatedFile()` | truncate a real DICOM at 1 KB | throws `IOException` with message containing the file path |
| `readMetadata_throwsOnMalformedFileMetaInformation()` | corrupt FMI header | throws specific dcm4che exception (document which) |
| `readMetadata_handlesMissingTransferSyntax()` | DICOM with no TS UID in FMI | reads using implicit VR LE default per dcm4che behaviour; no crash |
| `readHeaderOnly_omitsPixelDataEntirely()` | 100 MB native multiframe | `attrs.getValue(PixelData) == null && !attrs.contains(PixelData)` |
| `readHeaderOnly_isFasterThanReadMetadata()` | same; both in JMH-style timing | header-only avg latency < URI-mode avg latency (sanity check; not a correctness gate, just a regression alarm) |

### C.test.2 — `BulkData` reference lifecycle

**File:** `libs/dicom-xnat/dicom-xnat-util/src/test/java/org/nrg/dicom/streaming/BulkDataLifecycleTest.java`

| Method | Asserts |
|---|---|
| `bulkDataResolves_afterAttributesPassesAcrossMethodBoundary()` | parse in method A, return Attributes, call `getValue(PixelData)` in method B → bytes match |
| `bulkDataFailsCleanly_whenSourceFileDeleted()` | parse, delete source file, then `getValue(PixelData)` → throws `IOException` (not `NullPointerException`) |
| `bulkDataFailsCleanly_whenSourceFileTruncated()` | parse, truncate file from underneath, then `getValue` → exception with offset/length context |
| `bulkDataIsStable_acrossMultipleGetValueCalls()` | call `getValue(PixelData)` three times → returns equal bytes each time |
| `bulkDataSurvivesAttributesSerialization()` | serialize the `Attributes` (Java serialization) and deserialize → BulkData URI still resolves |
| `bulkDataConcurrentAccess_isSafe()` | 16 threads × 100 iterations each call `getValue(PixelData)` on the same `Attributes` instance → no `ConcurrentModificationException`, all return identical bytes |

### C.test.3 — Heap-bound assertions on real ingest paths

**File:** `apps/web/src/test/java/org/nrg/xnat/archive/IngestHeapBoundsTest.java`

| Method | Asserts |
|---|---|
| `ingest500kCt_heapDeltaUnder2Mb()` | `HeapBounds.assertHeapDeltaUnder(2_000_000, () -> archiveService.ingest(file))` |
| `ingest100MbMultiframe_heapDeltaUnder20Mb()` | same with 100 MB file, 20 MB cap (was: ~120 MB) |
| `ingest1GbMultiframe_heapDeltaUnder50Mb()` | same with 1 GB file, 50 MB cap (was: would OOM in pre-refactor 2g heap) |
| `ingestConcurrent_4parallelTimes100Mb_peakUnder200Mb()` | `HeapBounds.assertHeapPeakUnder(200_000_000, () -> ingestParallel(4, files))` |
| `ingestConcurrent_10parallelTimes100Mb_peakUnder400Mb()` | same with 10 concurrent (was: ~1 GB peak) |
| `ingestStress_100SequentialFiles_noLeak()` | ingest 100 × 50 MB files in a loop; after final `System.gc()`, heap usage is within 50 MB of pre-loop baseline (catches BulkData reference leaks) |

### C.test.4 — Per-module migration verification

For each module that gets a `new DicomInputStream` → `DicomReader` migration in Phase C, the module's existing test suite must remain green AND a new memory-bound test must be added.

| Module | New test class | Asserts |
|---|---|---|
| `apps/web` | `XnatDicomIngestServiceTest.ingestUriModeRegression()` | full archive flow on a 100 MB native multiframe stays within 20 MB heap delta |
| `libs/session-builders` | `SessionBuilderHeapBoundsTest.groupSessionUriMode()` | building a 1000-file session stays within 5 MB heap delta total |
| `libs/prearc-importer` | `PrearcImporterHeapBoundsTest.importLargeStudyUriMode()` | importing a 1 GB study stays within 50 MB heap delta |
| `libs/dicom-xnat/dicom-xnat-mx` | `DicomXnatMxHeapBoundsTest.processStudyUriMode()` | similar |
| `libs/dicomtools` | `DicomToolsHeapBoundsTest.utilityCallsUriMode()` | covering each utility entry point |

If a module is migrated but doesn't get a new heap-bound test, the gate fails.

### C.test.5 — Smoke test against live stack

**File:** `smoke-tests/test_streaming_ingest.py` (extends the existing pytest smoke suite)

| Method | Asserts |
|---|---|
| `test_ingest_100mb_native_under_uri_mode()` | upload via `/data/services/import`; archive succeeds; assertion via `/xapi/admin/heap` (or similar) that pod heap stayed bounded |
| `test_ingest_concurrent_4parallel()` | 4 parallel uploads via threadpool; all archive, none crash |

These smoke tests run against the docker-compose stack (and EKS for cloud-deploy validation). Mark with `@pytest.mark.streaming` so they can be selected separately.

## Phase C verification gate

- [ ] `./gradlew build` clean
- [ ] `./gradlew test` passes (no new failures vs main)
- [ ] All M-classified sites in the audit are migrated; audit doc updated to mark them ✅
- [ ] **C.test.1** (`DicomReaderTest`) exists and all methods green.
- [ ] **C.test.2** (`BulkDataLifecycleTest`) exists and all methods green.
- [ ] **C.test.3** (`IngestHeapBoundsTest`) exists and all methods green; the `1Gb` and `10parallel` cases were pre-refactor red.
- [ ] **C.test.4** every migrated module has its named heap-bound test class; all green.
- [ ] **C.test.5** `smoke-tests/test_streaming_ingest.py` passes against `docker compose up -d`.
- [ ] JMH benchmarks (from B.4) re-run; `ingest100MbNative` and `ingest1GbNative` show ≥ 5× heap-allocation reduction vs the Phase A baseline.
- [ ] `forbiddenApisMain` allow-list still only contains `DicomReader.java` (no escape hatches added).

---

# Phase D — Refactor pixel-touching paths to per-frame streaming

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

### D.3 — Catalog-validation paths that read pixel offsets

`apps/web/.../catalog` may have code that touches pixel data for checksum validation or frame-count verification. Audit and switch to per-frame.

## Required tests for Phase D

### D.test.1 — Per-frame redaction unit tests

**File:** `libs/dicom-edit6/src/test/java/org/nrg/dicom/dicomedit/pixeledit/streaming/PerFrameRedactionTest.java`

| Method | Setup | Asserts |
|---|---|---|
| `redactSingleFrame_inMultiframeStudy()` | 50-frame multiframe, redact 100×100 region in frame 5 only | frame 5 has redaction in the region; frames 0–4, 6–49 unchanged |
| `redactAllFrames_byteIdenticalAcrossFrames()` | redact same region in every frame | every redacted region matches reference color exactly; non-redacted regions byte-identical to input |
| `streamingDoesNotMaterializeWholeStudy()` | 1 GB multiframe, redact one frame | `HeapBounds.assertHeapDeltaUnder(50_000_000, ...)` — heap stays within ~one frame's worth (was: full study) |
| `streamingHandlesEncapsulatedSource()` | JPEG 2000 multiframe input | per-frame access via `Transcoder.readFrame()` works without re-encoding non-redacted frames |
| `streamingHandlesNativeSource()` | native multiframe input | per-frame slicing via BulkData byte ranges produces correct per-frame buffers (correct `rows × cols × bytesPerPixel × samplesPerPixel` math) |

### D.test.2 — Anonymization output-parity tests

**File:** `libs/dicom-edit6/src/test/java/org/nrg/dicom/dicomedit/mizer/AnonymizationOutputParityTest.java`

The pre-refactor outputs are captured as SHA-256 fixtures committed to `libs/dicom-edit6/src/test/resources/parity/`. The test runs the same scripts on the same inputs post-refactor and asserts identical SHA-256 of pixel bytes (header bytes may diverge legitimately due to anon rewrites; pixel bytes must match).

| Method | Asserts |
|---|---|
| `siteAnonScript_pixelsByteIdentical()` | Run `SCRIPT_SITE` (no pixel-touching rules) on `dcm/multi-frame/us-evle-rgb-8bit.dcm`; output PixelData SHA-256 matches captured baseline |
| `projectAnonScript_pixelsByteIdentical()` | same with `SCRIPT_PROJ` |
| `redactionScript_pixelsMatchPreRefactorOutput()` | Run a pixel-redaction script captured as a fixture; output PixelData SHA-256 matches captured pre-refactor baseline |
| `parityFixtures_existAndAreUpToDate()` | Boilerplate: assert each parity-fixture file has a corresponding test method, and vice versa, so missing fixtures fail loudly |

### D.test.3 — Re-enabled `@Ignore`d tests as gate

The four `PixelmedPixelEditHandlerTest.multiframe_*` tests un-`@Ignore`'d as part of Phase D's PR. The PR cannot merge unless all four pass.

| Test | Re-enable | Expected pass condition |
|---|---|---|
| `multiframe_evle_rgb_8bit` | remove `@Ignore` | passes — validator now streams per frame, no OOM |
| `multiframe_rle_8bit` | remove `@Ignore` | **may still need fixing** — root cause was ImageJ codec, not memory; if streaming exposes a different blocker, document and possibly leave `@Ignore` with updated comment |
| `multiframe_rle_pal_8bit` | same | same |
| `multiframe_jpeg1` | same | same |

**Implementing agent:** at minimum `multiframe_evle_rgb_8bit` MUST come off `@Ignore`. The other three may stay ignored ONLY if the streaming refactor truly didn't address their root cause; in that case the `@Ignore` comment must be updated to remove the false-but-now-dated "validator can't keep up" framing.

A guard test ensures the un-`@Ignore`-ing was real:

**File:** `libs/dicom-edit6/src/test/java/org/nrg/dicom/dicomedit/pixeledit/impl/PixelmedPixelEditHandlerTest.java`

Add a meta-test:

```java
@Test
public void evleRgbMultiframeMustNotBeIgnored() throws NoSuchMethodException {
    Method m = PixelmedPixelEditHandlerTest.class.getMethod("multiframe_evle_rgb_8bit");
    assertNull("Phase D regression: multiframe_evle_rgb_8bit was re-@Ignore'd",
               m.getAnnotation(Ignore.class));
}
```

This one-line guard prevents future agents from silently re-`@Ignore`-ing the test if it starts failing.

### D.test.4 — Live-stack smoke

`smoke-tests/test_streaming_redaction.py`:

| Method | Asserts |
|---|---|
| `test_redact_large_multiframe_e2e()` | upload 200 MB multiframe → run a redaction script via XNAT REST → download → confirm bytes in the redacted region match expected, others unchanged, heap stable on server |

## Phase D verification gate

- [ ] `./gradlew build` clean
- [ ] `./gradlew test` passes
- [ ] **D.test.1** (`PerFrameRedactionTest`) exists and all methods green.
- [ ] **D.test.2** (`AnonymizationOutputParityTest`) exists and all methods green; parity-fixture corpus committed to `libs/dicom-edit6/src/test/resources/parity/`.
- [ ] **D.test.3** at minimum `multiframe_evle_rgb_8bit` is un-`@Ignore`'d AND green; the meta-guard test (`evleRgbMultiframeMustNotBeIgnored`) passes.
- [ ] **D.test.4** `smoke-tests/test_streaming_redaction.py` passes.
- [ ] JMH `ingestOver2GbNative` benchmark from B.4 now succeeds (was: would have OOM'd pre-refactor).
- [ ] `forbiddenApisMain` allow-list updated only to allow the new per-frame-streaming entry points (no escape hatches re-introduced for general `DicomInputStream` usage).

---

# Phase E — Retire `NativeDicomPreCompressor`

## Goal

Once Phases B and C land, no XNAT code path materializes the full pixel `byte[]` for any file size. The pre-compressor's reason for existing is gone; remove it.

## Tasks

1. Remove every call site of `NativeDicomPreCompressor.preCompressIfNeeded(...)`.
2. Delete `apps/web/src/main/java/org/nrg/xnat/archive/NativeDicomPreCompressor.java`.
3. Delete the corresponding test class if one exists.
4. Remove `runtimeOnly("com.sun.media:jai_imageio:1.2-pre-dr-b04")` from `apps/web/build.gradle.kts:279` if no other code path needs it (likely safe — Phase A audit will confirm).
5. Add an ADR `docs/adr/0009-pixel-streaming-and-precompressor-retirement.md` documenting the refactor.

## Required tests for Phase E

### E.test.1 — Byte-identical roundtrip

**File:** `apps/web/src/test/java/org/nrg/xnat/archive/ByteIdenticalRoundTripTest.java`

| Method | Asserts |
|---|---|
| `roundtrip_100MbNative_pixelsByteIdentical()` | ingest 100 MB native multiframe → archive → download → `Sha256Checksum.ofPixelData(downloaded) == Sha256Checksum.ofPixelData(original)` |
| `roundtrip_over2GbNative_pixelsByteIdentical()` | same with > 2 GB fixture (was impossible pre-refactor; pre-compressor would have lossless-but-not-byte-identical-converted to JPEG 2000) |
| `roundtrip_native_transferSyntaxPreserved()` | post-archive file's `(0002,0010) Transfer Syntax UID` equals input's (was: native input got rewritten to `1.2.840.10008.1.2.4.90` JPEG 2000 by the pre-compressor) |
| `roundtrip_jpeg2000Native_pixelsByteIdentical()` | input that's already JPEG 2000 — passthrough behaviour preserved |

### E.test.2 — Legacy pre-compressed readback

Files in existing archives that were already pre-compressed by the (now-deleted) `NativeDicomPreCompressor` must remain readable. The test commits a fixture that mimics a "legacy pre-compressed" archive entry (ingest one, capture the post-compression file as a test resource) and asserts it still reads correctly post-refactor.

**File:** `apps/web/src/test/java/org/nrg/xnat/archive/LegacyPreCompressedReadbackTest.java`

| Method | Asserts |
|---|---|
| `legacyJpeg2000File_readsCorrectly()` | a fixture file in JPEG 2000 Lossless transfer syntax (created via the old `NativeDicomPreCompressor` and committed to test resources) reads cleanly with `DicomReader.readMetadata` and the pixel data resolves via `BulkData` |
| `legacyJpeg2000File_pixelsRoundtrip()` | reading + re-writing the legacy file produces byte-identical pixel data (no double-encoding) |
| `legacyJpeg2000File_archiveDownload_succeeds()` | end-to-end: legacy file in archive → download via XNAT REST → succeeds, bytes match |

### E.test.3 — External-tool compatibility

**File:** `apps/web/src/test/java/org/nrg/xnat/archive/ExternalToolReadbackTest.java`

Uses an external DICOM library (a test-scope dep on `pixelmed` — already in the build for production use, so no new dependency) to validate that files written by the post-refactor archive flow are decodable outside dcm4che.

| Method | Asserts |
|---|---|
| `pixelmedReadsArchivedFile()` | after archive, `com.pixelmed.dicom.AttributeList` opens the archived file, finds expected tags, decodes pixel data |
| `headerTagsByteIdenticalAcrossDcm4cheAndPixelmed()` | both libraries return the same patient/study/series IDs |
| `pixelDataDecodesIdenticallyAcrossLibraries()` | both libraries return identical pixel arrays for a fixture |

(Optional, Python-side verification: a `smoke-tests/test_external_pydicom.py` that calls `pydicom.dcmread` on archived files via the live XNAT instance. Adds runtime dependency on pydicom, which `smoke-tests/requirements.txt` already has.)

### E.test.4 — Build / packaging assertions

| Gate | How |
|---|---|
| WAR is smaller post-refactor | `du -sh apps/web/build/libs/*.war` pre-refactor stored in PR description; post-refactor at least 5 MB smaller (jai_imageio + JJ2000 dropped) |
| `NativeDicomPreCompressor` truly gone | `find . -name 'NativeDicomPreCompressor*' \| wc -l` returns 0 |
| `jai_imageio` no longer in runtime classpath | `unzip -l apps/web/build/libs/*.war | grep -i jai_imageio` returns nothing |
| All references removed from code + docs | `grep -r "NativeDicomPreCompressor\|jai_imageio" --include="*.java" --include="*.md" --include="*.kts"` returns only the ADR explaining the deletion |

## Phase E verification gate

- [ ] Pre-compressor source file is deleted.
- [ ] No references to `NativeDicomPreCompressor` remain in the codebase (`grep -r "NativeDicomPreCompressor" .` only matches the ADR).
- [ ] `./gradlew :apps:web:war` produces a smaller WAR (J2K codec gone, ~5–10 MB lighter — verify via `du -sh apps/web/build/libs/*.war`).
- [ ] **E.test.1** (`ByteIdenticalRoundTripTest`) exists and all methods green.
- [ ] **E.test.2** (`LegacyPreCompressedReadbackTest`) exists and all methods green; legacy fixture committed.
- [ ] **E.test.3** (`ExternalToolReadbackTest`) exists and all methods green.
- [ ] **E.test.4** packaging assertions pass.
- [ ] Smoke test: ingest a > 2 GB native DICOM → succeeds without pre-compression. **Critically: file on disk after archive is byte-identical to the original** (modulo header rewrites from anonymization).
- [ ] Smoke test: ingest a 100 MB native DICOM, then download → bytes match the original input file.
- [ ] ADR 0009 written and reviewed.

---

# Phase F — Verification + benchmarks publishing

## Goal

Publish the win, lock it in with regression detection. The benchmark *harness* was built in Phase B; here we run it pre vs post and document the result.

## Tasks

### F.1 — Capture and publish benchmark numbers

Run the JMH harness from B.4 against:

1. `main` at the head of Phase A's audit commit (pre-refactor baseline)
2. `main` at the head of Phase E's merge commit (post-refactor)

Commit both result files to `apps/web/jmh/baseline.json` and `apps/web/jmh/post-refactor.json`. The CI regression gate from B.6 uses `baseline.json` as the watermark; future PRs that regress > 20% on heap-allocation per ingest fail the build.

### F.2 — README + ADR updates

- `README.md`: brief mention under "DICOM ingest" / "Tomcat 10" section that XNAT now streams pixel data; cite ADR 0009.
- `docs/adr/0009-pixel-streaming-and-precompressor-retirement.md`: full architectural narrative — what the pre-compressor was, why it existed, what replaced it, byte-fidelity preservation, before/after benchmark numbers.
- `docs/plan-tomcat10-eks-tests.md`: if it referenced the pre-compressor, update.
- `docs/adr/0006-eks-deployment-target.md`: the "Bring-up Notes" section may need a small update if anything about the deploy assumed pre-compression behaviour.

### F.3 — Confirm CI regression gate is wired

The lint rule (B.5) and JMH-comparison gate (B.6) were built in Phase B but only became *meaningful* after Phase C migrations landed. Verify in F:

- A synthetic test PR that adds `new DicomInputStream(...)` outside the allow-list **fails** `./gradlew forbiddenApisMain`.
- A synthetic test PR that increases heap allocation per ingest by 50% **fails** the CI benchmark comparison step.

Document the synthetic-PR test results in the ADR.

### F.4 — Cross-cutting test sanity

Re-run the full test corpus once Phases B–E have all merged:

- `./gradlew test` — all module unit tests green.
- `./gradlew :apps:web:jmh` — full JMH suite, results captured.
- `cd smoke-tests && pytest -v` — local stack smoke suite green.
- Cloud Deploy (`workflow_dispatch`) — single-EC2 smoke suite green.
- EKS Deploy (`workflow_dispatch`) — EKS smoke suite green.

## Required tests for Phase F

Phase F itself doesn't add new test classes — it *exercises* what B–E built. The verification gate is empirical: run the benchmarks, capture numbers, lock the gate.

## Phase F verification gate

- [ ] `apps/web/jmh/baseline.json` and `apps/web/jmh/post-refactor.json` both committed.
- [ ] Heap-allocation reduction documented in ADR 0009 — table form, all five JMH scenarios from B.4.
- [ ] OOM-elimination documented for > 2 GB case (the `ingestOver2GbNative` benchmark went from "throws" to "succeeds with ~5 MB heap delta").
- [ ] Synthetic-regression-PR test confirms `forbiddenApisMain` catches re-introduced eager `DicomInputStream`.
- [ ] Synthetic-regression-PR test confirms CI benchmark gate catches a 50% allocation regression.
- [ ] `./gradlew test`, `./gradlew :apps:web:jmh`, `pytest smoke-tests/`, Cloud Deploy, EKS Deploy — all green on the post-refactor commit.
- [ ] ADR 0009 merged.

---

# Cross-phase summary: test deliverables

For quick reference, the test files that must exist by the end of Phase E (Phase F adds none of its own):

| Phase | Test class / file | Purpose |
|---|---|---|
| B | `LargeDicomFixtureFactoryTest` | Synthetic-DICOM generator self-test |
| B | `HeapBoundsTest` | Heap-bound assertion helper self-test |
| B | `Sha256ChecksumTest` | Checksum helper self-test |
| B | `apps/web/src/jmh/.../DicomIngestBenchmark` | JMH harness, 5 ingest scenarios |
| B | `forbiddenApisMain` Gradle task | Lint rule preventing eager `DicomInputStream` |
| C | `DicomReaderTest` | URI-mode helper unit tests (9 methods) |
| C | `BulkDataLifecycleTest` | BulkData reference lifecycle (6 methods) |
| C | `IngestHeapBoundsTest` | Heap-bound assertions on ingest paths (6 methods) |
| C | `XnatDicomIngestServiceTest.ingestUriModeRegression` + 4 module-specific heap-bound tests | Per-module regression coverage |
| C | `smoke-tests/test_streaming_ingest.py` | Live-stack smoke (2 methods) |
| D | `PerFrameRedactionTest` | Per-frame streaming unit tests (5 methods) |
| D | `AnonymizationOutputParityTest` | Pre/post pixel-byte parity (4 methods) + parity-fixture corpus in `libs/dicom-edit6/src/test/resources/parity/` |
| D | `PixelmedPixelEditHandlerTest.evleRgbMultiframeMustNotBeIgnored` | Meta-guard against re-`@Ignore`-ing the canary |
| D | `smoke-tests/test_streaming_redaction.py` | Live-stack redaction smoke |
| E | `ByteIdenticalRoundTripTest` | Pixel-byte fidelity across archive + retrieval (4 methods) |
| E | `LegacyPreCompressedReadbackTest` | Pre-existing JPEG-2000 archives still readable (3 methods) |
| E | `ExternalToolReadbackTest` | Pixelmed-side roundtrip parity (3 methods) |

Total: ~15 new test classes, ~50 new test methods, plus the JMH benchmark, lint rule, smoke-test additions, and parity-fixture corpus.

---

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
