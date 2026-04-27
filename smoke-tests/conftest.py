"""
conftest.py — pytest fixtures for the XNAT smoke test suite.

Configuration is driven entirely by environment variables so the same
test suite can run against a local Docker Compose stack or any remote
XNAT instance without code changes.

Environment variables
---------------------
XNAT_HOST           Base URL scheme + hostname   (default: http://localhost)
XNAT_PORT           Port                         (default: 80)
XNAT_ADMIN_USER     Site admin username          (default: admin)
XNAT_ADMIN_PASS     Site admin password          (default: admin)
"""

from __future__ import annotations

import os
import time
import uuid
import zipfile
from collections import namedtuple
from pathlib import Path
from typing import Generator

import pytest
import requests
from requests import Session


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _build_base_url() -> str:
    host = os.environ.get("XNAT_HOST", "http://localhost").rstrip("/")
    port = os.environ.get("XNAT_PORT", "80")

    # Avoid double-specifying the port when the host already includes it.
    if ":" in host.split("//", 1)[-1]:
        return host

    default_port = "443" if host.startswith("https") else "80"
    if port == default_port:
        return host

    return f"{host}:{port}"


# ---------------------------------------------------------------------------
# Session-scoped fixtures (created once per test run)
# ---------------------------------------------------------------------------

@pytest.fixture(scope="session")
def base_url() -> str:
    """Fully qualified base URL for the XNAT instance under test."""
    return _build_base_url()


@pytest.fixture(scope="session")
def admin_credentials() -> dict[str, str]:
    """Admin username / password as a dict."""
    return {
        "username": os.environ.get("XNAT_ADMIN_USER", "admin"),
        "password": os.environ.get("XNAT_ADMIN_PASS", "admin"),
    }


@pytest.fixture(scope="session")
def admin_session(base_url: str, admin_credentials: dict[str, str]) -> Session:
    """
    Authenticated requests.Session for the admin account.

    Logs in via POST /data/services/auth and stores the JSESSIONID cookie.
    The session is shared across all tests in the run.
    """
    session = requests.Session()
    # NOTE: Do NOT set Accept: application/json globally.
    # XNAT's Restlet-based /data/ endpoints return 406 with that header.
    # Use ?format=json query param instead for those endpoints.

    login_url = f"{base_url}/data/services/auth"
    response = session.post(
        login_url,
        data={
            "username": admin_credentials["username"],
            "password": admin_credentials["password"],
        },
        params={"CSRF": "true"},  # Request CSRF token in response
        timeout=30,
    )
    response.raise_for_status()

    # XNAT returns "JSESSIONID; XNAT_CSRF=<token>" when CSRF=true is passed.
    body = response.text.strip()
    jsessionid = body
    xnat_csrf = None

    if ";" in body:
        parts = body.split(";")
        jsessionid = parts[0].strip()
        for part in parts[1:]:
            part = part.strip()
            if part.startswith("XNAT_CSRF="):
                xnat_csrf = part.split("=", 1)[1]

    session.cookies.set("JSESSIONID", jsessionid)
    if xnat_csrf:
        session.cookies.set("XNAT_CSRF", xnat_csrf)

    # Complete XNAT first-time initialization if not already done.
    # On a fresh database, XNAT requires POST /xapi/siteConfig with
    # initialized=true before the API is fully functional.
    import time

    init_check = session.get(f"{base_url}/xapi/siteConfig/initialized", timeout=30)
    if init_check.status_code == 200 and init_check.text.strip().lower() == "false":
        init_payload = {
            "siteId": "XNAT",
            "siteUrl": base_url,
            "adminEmail": "admin@example.com",
            "archivePath": "/data/xnat/archive",
            "prearchivePath": "/data/xnat/prearchive",
            "cachePath": "/data/xnat/cache",
            "buildPath": "/data/xnat/build",
            "ftpPath": "/data/xnat/ftp",
            "pipelinePath": "/data/xnat/pipeline",
            "initialized": True,
        }
        session.post(
            f"{base_url}/xapi/siteConfig",
            json=init_payload,
            timeout=30,
        )
        # Wait for XNAT to finish background initialization (schema setup, etc.)
        for _ in range(30):
            time.sleep(5)
            check = session.get(f"{base_url}/xapi/users", timeout=30)
            if check.status_code == 200:
                break

    yield session

    # Logout on teardown
    try:
        session.delete(f"{base_url}/data/services/auth", timeout=10)
    except Exception:
        pass


# ---------------------------------------------------------------------------
# Session-scoped fixtures (shared across all tests in the run)
# ---------------------------------------------------------------------------

@pytest.fixture(scope="session")
def unique_id() -> str:
    """Short unique identifier (8 hex chars) for resource names."""
    return uuid.uuid4().hex[:8]


@pytest.fixture(scope="session")
def test_username(unique_id: str) -> str:
    """Username for a temporary test user (e.g. 'smoketest_a1b2c3d4')."""
    return f"smoketest_{unique_id}"


@pytest.fixture(scope="session")
def test_project_id(unique_id: str) -> str:
    """Project ID for a temporary test project (e.g. 'SMOKE_A1B2C3D4')."""
    return f"SMOKE_{unique_id.upper()}"


# ---------------------------------------------------------------------------
# Function-scoped helpers for tests that need a fresh, isolated project
# ---------------------------------------------------------------------------

@pytest.fixture
def isolated_project(
    base_url: str, admin_session: Session
) -> Generator[str, None, None]:
    """
    Create a unique project for a single test, delete it on teardown.

    Use this when a test needs to mutate project-scoped state without
    interfering with the long-lived `test_project_id` shared by the
    test_smoke.py sequence. Project IDs are namespaced with an `ISO_`
    prefix so they're easy to spot if cleanup fails.
    """
    project_id = f"ISO_{uuid.uuid4().hex[:8].upper()}"
    response = admin_session.put(
        f"{base_url}/data/projects/{project_id}",
        params={
            "name": f"Isolated Test {project_id}",
            "description": "Auto-created by smoke tests; safe to delete.",
        },
        timeout=30,
    )
    response.raise_for_status()
    yield project_id

    # Best-effort teardown — never fail the test on cleanup error.
    try:
        admin_session.delete(
            f"{base_url}/data/projects/{project_id}", timeout=30
        )
    except Exception:
        pass


# ---------------------------------------------------------------------------
# Non-admin user session
# ---------------------------------------------------------------------------

UserSession = namedtuple("UserSession", ["session", "username", "password"])


@pytest.fixture(scope="session")
def user_session(
    base_url: str, admin_session: Session
) -> Generator[UserSession, None, None]:
    """
    Authenticated session for a non-admin user.

    Creates a fresh standard user via POST /xapi/users (admin-authed),
    waits for XNAT's async user provisioning to settle, logs in as that
    user, and yields a tuple of (session, username, password). On
    teardown the user is disabled (XNAT does not support hard delete).

    Used by `test_permissions.py` to assert that role enforcement is
    actually applied — admin-only assertions miss the most common
    regression class.
    """
    username = f"smoke_user_{uuid.uuid4().hex[:8]}"
    password = "Smoke$User1!"

    create_resp = admin_session.post(
        f"{base_url}/xapi/users",
        json={
            "username": username,
            "password": password,
            "firstName": "Smoke",
            "lastName": "User",
            "email": f"{username}@example.com",
            "enabled": True,
            "verified": True,
        },
        timeout=30,
    )
    create_resp.raise_for_status()

    # XNAT provisions users asynchronously — poll until the user is
    # visible via GET before attempting to log in as them.
    for _ in range(6):
        check = admin_session.get(
            f"{base_url}/xapi/users/{username}", timeout=30
        )
        if check.status_code == 200:
            break
        time.sleep(5)

    session = requests.Session()
    login = session.post(
        f"{base_url}/data/services/auth",
        data={"username": username, "password": password},
        timeout=30,
    )
    login.raise_for_status()
    jsessionid = login.text.strip().split(";", 1)[0]
    session.cookies.set("JSESSIONID", jsessionid)

    yield UserSession(session=session, username=username, password=password)

    # Teardown: log out + disable the user. Both best-effort.
    try:
        session.delete(f"{base_url}/data/services/auth", timeout=10)
    except Exception:
        pass
    try:
        admin_session.put(
            f"{base_url}/xapi/users/{username}/enabled/false", timeout=30
        )
    except Exception:
        pass


# ---------------------------------------------------------------------------
# Container service feature-flag fixture
# ---------------------------------------------------------------------------

@pytest.fixture(scope="session")
def container_service_available(
    base_url: str, admin_session: Session
) -> bool:
    """
    True iff the XNAT container service is configured and reachable.

    Tests in `test_container_service.py` skip the entire module when this
    is False so a stack without Docker socket access doesn't produce a
    flood of red. The default Compose stack does NOT enable container
    service — these tests are opt-in.
    """
    try:
        resp = admin_session.get(
            f"{base_url}/xapi/docker/server", timeout=10
        )
    except requests.RequestException:
        return False

    if resp.status_code != 200:
        return False

    # Endpoint returns the configured docker host; absence of the `host`
    # field (or 404 wrapped in 200) means "not configured".
    try:
        data = resp.json()
    except ValueError:
        return False
    return bool(data.get("host"))


# ---------------------------------------------------------------------------
# Synthetic DICOM fixture
# ---------------------------------------------------------------------------

DicomFixture = namedtuple(
    "DicomFixture",
    ["dir", "zip_path", "study_uid", "series_uid", "slice_count"],
)


def _make_synthetic_ct_slice(
    out_path: Path,
    *,
    study_uid: str,
    series_uid: str,
    instance_number: int,
    patient_id: str,
) -> None:
    """
    Write a single minimally-valid CT DICOM file using pydicom.

    Pixel data is a 16x16 zero-filled MONOCHROME2 array. Just enough to
    parse and route through XNAT's session builder; not visually useful.
    """
    import datetime

    from pydicom.dataset import Dataset, FileMetaDataset
    from pydicom.uid import ExplicitVRLittleEndian, generate_uid

    ds = Dataset()
    ds.PatientName = "Smoke^Test"
    ds.PatientID = patient_id
    ds.PatientBirthDate = "19700101"
    ds.PatientSex = "O"

    ds.StudyInstanceUID = study_uid
    ds.SeriesInstanceUID = series_uid
    ds.SOPInstanceUID = generate_uid()
    ds.SOPClassUID = "1.2.840.10008.5.1.4.1.1.2"  # CT Image Storage

    ds.Modality = "CT"
    ds.StudyDate = datetime.date.today().strftime("%Y%m%d")
    ds.StudyTime = "120000"
    ds.AccessionNumber = f"SMOKE{instance_number:03d}"
    ds.SeriesNumber = 1
    ds.InstanceNumber = instance_number
    ds.StudyID = "1"

    ds.Rows = 16
    ds.Columns = 16
    ds.BitsAllocated = 16
    ds.BitsStored = 16
    ds.HighBit = 15
    ds.PixelRepresentation = 0
    ds.SamplesPerPixel = 1
    ds.PhotometricInterpretation = "MONOCHROME2"
    # 16x16 pixels * 2 bytes per pixel = 512 zero bytes
    ds.PixelData = b"\x00" * (16 * 16 * 2)

    file_meta = FileMetaDataset()
    file_meta.MediaStorageSOPClassUID = ds.SOPClassUID
    file_meta.MediaStorageSOPInstanceUID = ds.SOPInstanceUID
    file_meta.TransferSyntaxUID = ExplicitVRLittleEndian
    ds.file_meta = file_meta
    ds.is_little_endian = True
    ds.is_implicit_VR = False

    ds.save_as(str(out_path), write_like_original=False)


@pytest.fixture(scope="session")
def dicom_fixture_dir(
    tmp_path_factory: pytest.TempPathFactory, unique_id: str
) -> DicomFixture:
    """
    Generate a small synthetic CT study + zipped archive once per session.

    Returns a `DicomFixture` namedtuple with:
      * `dir`         — directory containing N `.dcm` files
      * `zip_path`    — `tiny_ct_study.zip` containing those files
      * `study_uid`   — the StudyInstanceUID used in every slice
      * `series_uid`  — the SeriesInstanceUID
      * `slice_count` — number of slices generated

    Generation is preferred over committing a binary fixture so the
    suite stays self-contained. If the session-builder ever rejects
    synthetic data, drop a real anonymized zip at
    `smoke-tests/fixtures/tiny_ct_study.zip` and short-circuit this
    fixture to return that path.
    """
    from pydicom.uid import generate_uid

    fixture_dir = tmp_path_factory.mktemp("dicom-fixture")
    study_uid = generate_uid()
    series_uid = generate_uid()
    patient_id = f"SMOKE_{unique_id.upper()}"
    slice_count = 5

    for i in range(1, slice_count + 1):
        _make_synthetic_ct_slice(
            fixture_dir / f"slice_{i:03d}.dcm",
            study_uid=study_uid,
            series_uid=series_uid,
            instance_number=i,
            patient_id=patient_id,
        )

    zip_path = fixture_dir / "tiny_ct_study.zip"
    with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as zf:
        for dcm in sorted(fixture_dir.glob("*.dcm")):
            zf.write(dcm, arcname=dcm.name)

    return DicomFixture(
        dir=fixture_dir,
        zip_path=zip_path,
        study_uid=study_uid,
        series_uid=series_uid,
        slice_count=slice_count,
    )
