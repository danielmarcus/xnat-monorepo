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
import uuid

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
    session.headers.update({"Accept": "application/json"})

    login_url = f"{base_url}/data/services/auth"
    response = session.post(
        login_url,
        data={
            "username": admin_credentials["username"],
            "password": admin_credentials["password"],
        },
        timeout=30,
    )
    response.raise_for_status()

    # XNAT returns the session token in the response body (plain text) and as
    # a JSESSIONID cookie.  Either works; requests stores the cookie automatically.
    token = response.text.strip()
    if token:
        session.cookies.set("JSESSIONID", token)

    yield session

    # Logout on teardown
    try:
        session.delete(f"{base_url}/data/services/auth", timeout=10)
    except Exception:
        pass


# ---------------------------------------------------------------------------
# Function-scoped fixtures (fresh per test)
# ---------------------------------------------------------------------------

@pytest.fixture()
def unique_id() -> str:
    """Short unique identifier (8 hex chars) for resource names."""
    return uuid.uuid4().hex[:8]


@pytest.fixture()
def test_username(unique_id: str) -> str:
    """Username for a temporary test user (e.g. 'smoketest_a1b2c3d4')."""
    return f"smoketest_{unique_id}"


@pytest.fixture()
def test_project_id(unique_id: str) -> str:
    """Project ID for a temporary test project (e.g. 'SMOKE_A1B2C3D4')."""
    return f"SMOKE_{unique_id.upper()}"
