"""
test_resources.py — project-level resource collections + file IO.

Resources are XNAT's generic file-storage primitive (used for everything
from QC reports to derived data). The REST surface here is small but
critical: it's the same endpoints used by external tooling and by the
container service for output ingestion.
"""

from __future__ import annotations

import uuid

import requests


def _short_id(prefix: str) -> str:
    return f"{prefix}_{uuid.uuid4().hex[:6].upper()}"


# ---------------------------------------------------------------------------
# Collection creation
# ---------------------------------------------------------------------------

class TestResourceCollection:
    def test_create_resource_collection(
        self,
        base_url: str,
        admin_session: requests.Session,
        isolated_project: str,
    ) -> None:
        label = _short_id("RES")
        response = admin_session.put(
            f"{base_url}/data/projects/{isolated_project}/resources/{label}",
            timeout=30,
        )
        assert response.status_code in (200, 201), (
            f"Resource collection creation failed: HTTP {response.status_code}. "
            f"Body: {response.text[:500]}"
        )


# ---------------------------------------------------------------------------
# File upload + download
# ---------------------------------------------------------------------------

# Mix of binary content (zeros, ones, ASCII range) so a flawed
# transcoder can't sneak past on text-only data.
_BINARY_PAYLOAD = bytes(range(256)) * 4  # 1024 bytes, every byte value


class TestResourceFileUpload:
    def test_upload_resource_file(
        self,
        base_url: str,
        admin_session: requests.Session,
        isolated_project: str,
    ) -> None:
        label = _short_id("RES")
        filename = "uploaded.bin"
        admin_session.put(
            f"{base_url}/data/projects/{isolated_project}/resources/{label}",
            timeout=30,
        ).raise_for_status()

        upload = admin_session.put(
            f"{base_url}/data/projects/{isolated_project}/resources/{label}/files/{filename}",
            data=_BINARY_PAYLOAD,
            headers={"Content-Type": "application/octet-stream"},
            timeout=60,
        )
        assert upload.status_code in (200, 201), (
            f"File upload failed: HTTP {upload.status_code}. "
            f"Body: {upload.text[:500]}"
        )


class TestResourceFileDownload:
    def test_download_resource_file_byte_identical(
        self,
        base_url: str,
        admin_session: requests.Session,
        isolated_project: str,
    ) -> None:
        label = _short_id("RES")
        filename = "roundtrip.bin"
        admin_session.put(
            f"{base_url}/data/projects/{isolated_project}/resources/{label}",
            timeout=30,
        ).raise_for_status()
        admin_session.put(
            f"{base_url}/data/projects/{isolated_project}/resources/{label}/files/{filename}",
            data=_BINARY_PAYLOAD,
            headers={"Content-Type": "application/octet-stream"},
            timeout=60,
        ).raise_for_status()

        download = admin_session.get(
            f"{base_url}/data/projects/{isolated_project}/resources/{label}/files/{filename}",
            timeout=60,
        )
        assert download.status_code == 200, (
            f"File download failed: HTTP {download.status_code}"
        )
        assert download.content == _BINARY_PAYLOAD, (
            "Downloaded bytes do not match upload — content corruption."
        )


# ---------------------------------------------------------------------------
# Listing
# ---------------------------------------------------------------------------

class TestResourceFileListing:
    def test_uploaded_file_appears_in_listing(
        self,
        base_url: str,
        admin_session: requests.Session,
        isolated_project: str,
    ) -> None:
        label = _short_id("RES")
        filename = "list-me.txt"
        admin_session.put(
            f"{base_url}/data/projects/{isolated_project}/resources/{label}",
            timeout=30,
        ).raise_for_status()
        admin_session.put(
            f"{base_url}/data/projects/{isolated_project}/resources/{label}/files/{filename}",
            data=b"hello",
            timeout=30,
        ).raise_for_status()

        listing = admin_session.get(
            f"{base_url}/data/projects/{isolated_project}/resources/{label}/files",
            params={"format": "json"},
            timeout=30,
        )
        assert listing.status_code == 200, (
            f"Listing failed: HTTP {listing.status_code}"
        )
        results = listing.json().get("ResultSet", {}).get("Result", [])
        names = [r.get("Name", "") or r.get("name", "") for r in results]
        assert filename in names, (
            f"Uploaded file {filename!r} not in listing. Found: {names[:20]}"
        )


# ---------------------------------------------------------------------------
# Delete
# ---------------------------------------------------------------------------

class TestResourceFileDelete:
    def test_delete_then_404(
        self,
        base_url: str,
        admin_session: requests.Session,
        isolated_project: str,
    ) -> None:
        label = _short_id("RES")
        filename = "delete-me.bin"
        admin_session.put(
            f"{base_url}/data/projects/{isolated_project}/resources/{label}",
            timeout=30,
        ).raise_for_status()
        admin_session.put(
            f"{base_url}/data/projects/{isolated_project}/resources/{label}/files/{filename}",
            data=b"transient",
            timeout=30,
        ).raise_for_status()

        delete = admin_session.delete(
            f"{base_url}/data/projects/{isolated_project}/resources/{label}/files/{filename}",
            timeout=30,
        )
        assert delete.status_code in (200, 204), (
            f"File DELETE failed: HTTP {delete.status_code}"
        )

        follow_up = admin_session.get(
            f"{base_url}/data/projects/{isolated_project}/resources/{label}/files/{filename}",
            timeout=30,
        )
        assert follow_up.status_code in (403, 404), (
            f"Expected 403/404 after file delete, got {follow_up.status_code}"
        )
