# XNAT Smoke Tests

End-to-end smoke tests for XNAT, implemented with [pytest](https://pytest.org) and [requests](https://requests.readthedocs.io).

The suite covers:

1. Health check — `GET /xapi/siteConfig` returns 200
2. Admin login — `POST /data/services/auth`
3. User creation + verification
4. New user login
5. Project creation + verification
6. Project listing
7. Cleanup — delete test user and project, verify removal

---

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
# From the smoke-tests/ directory (uses pytest.ini defaults):
pytest

# With an HTML report:
pytest --html=build/test-results/smoke-report.html

# Run a single test class:
pytest test_smoke.py::TestHealthCheck -v
```

JUnit XML output is written automatically to `build/test-results/smoke-tests.xml`.

### 6 — Tear down

```bash
cd deploy/docker-compose
docker compose down -v    # -v removes volumes
```

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

The smoke tests are environment-variable–driven and make no assumptions about
the underlying infrastructure. Point them at any accessible XNAT server:

```bash
export XNAT_HOST=https://xnat.myinstitution.org
export XNAT_PORT=443
export XNAT_ADMIN_USER=admin
export XNAT_ADMIN_PASS=<password>

cd smoke-tests
pytest -v
```

> **Warning — cleanup tests delete data.**
> Tests 7 (cleanup) will delete the user and project created during the run.
> Always use a dedicated test XNAT instance, never production.

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

- name: Run smoke tests
  working-directory: smoke-tests
  run: |
    pip install -r requirements.txt
    pytest

- name: Publish test results
  uses: mikepenz/action-junit-report@v4
  if: always()
  with:
    report_paths: smoke-tests/build/test-results/smoke-tests.xml
```
