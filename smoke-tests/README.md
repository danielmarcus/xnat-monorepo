# XNAT Smoke Tests

End-to-end smoke tests for XNAT, implemented with [pytest](https://pytest.org)
and [requests](https://requests.readthedocs.io).

The suite is split into a **fast** subset (gates CI deploys) and a **full**
subset (post-deploy validation, including DICOM upload). Tests are tagged
with pytest markers — `slow`, `dicom`, `container_service` — so the two
subsets are selected by `-m` filters at the pytest invocation, not by
duplicated test files.

## Coverage

| File | Tests | Markers | Covers |
|---|---|---|---|
| `test_smoke.py` | 11 | (none) | admin login, user CRUD, project CRUD, cleanup |
| `test_site_config.py` | 4 | (none) | `/xapi/siteConfig` GET/PUT, `/buildInfo`, `/initialized` (unauth) |
| `test_subjects_experiments.py` | 7 | (none) | subject + MR/CT session CRUD + cascade deletes |
| `test_resources.py` | 5 | (none) | resource collection + binary file upload/download |
| `test_search.py` | 3 | `slow` | stored search + inline `xdat:search` |
| `test_permissions.py` | 5 | (none) | Owners / Members / Collaborators role enforcement |
| `test_dicom_upload.py` | 7 | `slow`, `dicom` | DICOM ingest pipeline (Phase C canary) |
| `test_container_service.py` | 5 | `slow`, `container_service` | XCS docker server, pull, command launch |

The DICOM tests use synthetic CT data generated at fixture-creation time
via `pydicom` — nothing committed under `fixtures/`.

## Quick start: run against Docker Compose

### 1 — Build the WAR (if not already built)

```bash
# From the monorepo root
./gradlew :apps:web:war
```

### 2 — Start the stack

```bash
cd deploy/docker-compose

# Standard (persistent volumes):
docker compose up -d

# CI mode (ephemeral, faster health checks):
docker compose -f docker-compose.yml -f docker-compose.ci.yml up -d
```

### 3 — Wait for XNAT to be ready

```bash
# From deploy/docker-compose/
./wait-for-xnat.sh

# Custom host/port/timeout:
./wait-for-xnat.sh -h http://localhost -p 80 -t 300
```

### 4 — Install test dependencies

```bash
cd smoke-tests
python -m venv .venv
source .venv/bin/activate      # Windows: .venv\Scripts\activate
pip install -r requirements.txt
```

### 5 — Run the tests

```bash
# Everything (default):
pytest

# Fast subset only (matches CI gate):
pytest -m "not slow and not container_service"

# Long-running suite (DICOM, search):
pytest -m "slow or dicom"

# Container service only (requires the docker socket bind-mount):
pytest -m container_service

# A single test file or class:
pytest test_smoke.py::TestAdminLogin -v

# Parallelize the fast suite:
pytest -m "not slow and not container_service" -n auto
```

JUnit XML output is written automatically to `build/test-results/smoke-tests.xml`.
HTML reports add `--html=build/test-results/smoke-report.html`.

### 6 — Tear down

```bash
cd deploy/docker-compose
docker compose down -v    # -v removes volumes
```

---

## Markers

Markers are declared in `pytest.ini` and enforced via `--strict-markers`,
so a typo'd marker fails the run instead of silently ignoring the
filter.

| Marker | Purpose |
|---|---|
| `slow` | Long-running tests (>5s). Skipped by the fast suite. |
| `dicom` | Requires the DICOM fixture (synthetic via pydicom). |
| `container_service` | Requires XCS configured (entire module skips when not). |
| `smoke` | Catch-all for "this is a smoke test" — historical, not load-bearing. |

---

## Environment variable reference

All variables have sensible defaults for a local Docker Compose stack.

| Variable           | Default              | Description                              |
|--------------------|----------------------|------------------------------------------|
| `XNAT_HOST`        | `http://localhost`   | Base URL (scheme + hostname, no path)    |
| `XNAT_PORT`        | `80`                 | Port XNAT is listening on                |
| `XNAT_ADMIN_USER`  | `admin`              | Site administrator username              |
| `XNAT_ADMIN_PASS`  | `admin`              | Site administrator password              |

Set them in your shell before running pytest:

```bash
export XNAT_HOST=http://xnat.example.com
export XNAT_PORT=443
export XNAT_ADMIN_USER=admin
export XNAT_ADMIN_PASS=s3cr3t
pytest
```

Or inline:

```bash
XNAT_HOST=https://xnat.example.com XNAT_PORT=443 pytest
```

---

## Run against a remote XNAT instance

The smoke tests are environment-variable–driven and make no assumptions
about the underlying infrastructure. Point them at any accessible XNAT
server:

```bash
export XNAT_HOST=https://xnat.myinstitution.org
export XNAT_PORT=443
export XNAT_ADMIN_USER=admin
export XNAT_ADMIN_PASS=<password>

cd smoke-tests
pytest -v
```

> **Warning — these tests create and delete data.**
> Tests create projects, subjects, sessions, resources, and uploaded
> DICOM. Cleanup runs in fixture teardowns but a hard kill (Ctrl-C, OOM)
> will leave `SMOKE_*`, `ISO_*`, and `DCM_*` projects behind. Always use
> a dedicated test XNAT instance, never production.

---

## CI integration

In a GitHub Actions workflow:

```yaml
- name: Start XNAT
  working-directory: deploy/docker-compose
  run: docker compose -f docker-compose.yml -f docker-compose.ci.yml up -d

- name: Wait for XNAT
  working-directory: deploy/docker-compose
  run: ./wait-for-xnat.sh -t 300

- name: Run smoke tests (fast)
  working-directory: smoke-tests
  run: |
    pip install -r requirements.txt
    pytest -m "not slow and not container_service" --reruns 2

- name: Run smoke tests (full)
  working-directory: smoke-tests
  run: pytest -m "slow or dicom" --reruns 2

- name: Publish test results
  uses: mikepenz/action-junit-report@v4
  if: always()
  with:
    report_paths: smoke-tests/build/test-results/smoke-tests.xml
```

The repository's `.github/workflows/smoke-test.yml` runs the fast +
full split as a matrix; `cloud-deploy.yml` runs them sequentially
(fast gates the deploy, full validates post-deploy).
