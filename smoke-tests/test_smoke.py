"""
test_smoke.py — XNAT smoke test suite.

Tests are ordered (via explicit numeric prefixes) so they run in the sequence
described in the specification:

  1. Health check        — GET  /xapi/siteConfig
  2. Admin login         — POST /data/services/auth  (admin creds)
  3. User creation       — POST /xapi/users  +  GET /xapi/users/{username}
  4. User login          — POST /data/services/auth  (new user creds)
  5. Project creation    — PUT  /data/projects/{id}  +  GET confirmation
  6. Project listing     — GET  /data/projects includes created project
  7. Cleanup             — DELETE user and project, confirm 200

All fixtures are provided by conftest.py.
"""

from __future__ import annotations

import pytest
import requests


# ---------------------------------------------------------------------------
# Test 1 — Health check
# ---------------------------------------------------------------------------

class TestHealthCheck:
    def test_site_config_returns_200(self, base_url: str, admin_session: Session) -> None:
        """GET /xapi/siteConfig must return HTTP 200 with admin authentication."""
        response = admin_session.get(
            f"{base_url}/xapi/siteConfig",
            timeout=30,
        )
        assert response.status_code == 200, (
            f"Expected 200 from /xapi/siteConfig, got {response.status_code}. "
            f"Body: {response.text[:500]}"
        )


# ---------------------------------------------------------------------------
# Test 2 — Admin login
# ---------------------------------------------------------------------------

class TestAdminLogin:
    def test_admin_login_returns_jsessionid(
        self, base_url: str, admin_credentials: dict
    ) -> None:
        """POST /data/services/auth with admin credentials must return a session token."""
        response = requests.post(
            f"{base_url}/data/services/auth",
            data={
                "username": admin_credentials["username"],
                "password": admin_credentials["password"],
            },
            timeout=30,
        )
        assert response.status_code == 200, (
            f"Admin login failed: HTTP {response.status_code}. Body: {response.text[:500]}"
        )

        # XNAT returns the JSESSIONID as plain text in the response body.
        token = response.text.strip()
        assert token, "Admin login response body is empty — expected a JSESSIONID token."

        # The cookie should also be set.
        assert "JSESSIONID" in response.cookies or token, (
            "Expected JSESSIONID in response cookies or body."
        )


# ---------------------------------------------------------------------------
# Test 3 — User creation
# ---------------------------------------------------------------------------

class TestUserCreation:
    def test_create_user(
        self, base_url: str, admin_session: requests.Session, test_username: str
    ) -> None:
        """POST /xapi/users must create a new user successfully."""
        payload = {
            "username": test_username,
            "password": "Smoke$Test1!",
            "firstName": "Smoke",
            "lastName": "Test",
            "email": f"{test_username}@example.com",
            "enabled": True,
            "verified": True,
        }
        response = admin_session.post(
            f"{base_url}/xapi/users",
            json=payload,
            timeout=30,
        )
        assert response.status_code in (200, 201), (
            f"User creation failed: HTTP {response.status_code}. Body: {response.text[:500]}"
        )

    def test_get_created_user(
        self, base_url: str, admin_session: requests.Session, test_username: str
    ) -> None:
        """GET /xapi/users/{username} must return the newly created user."""
        response = admin_session.get(
            f"{base_url}/xapi/users/{test_username}",
            timeout=30,
        )
        assert response.status_code == 200, (
            f"GET user failed: HTTP {response.status_code}. Body: {response.text[:500]}"
        )
        data = response.json()
        assert data.get("username") == test_username, (
            f"Returned username '{data.get('username')}' != expected '{test_username}'"
        )


# ---------------------------------------------------------------------------
# Test 4 — User login
# ---------------------------------------------------------------------------

class TestUserLogin:
    def test_new_user_can_login(
        self, base_url: str, test_username: str
    ) -> None:
        """POST /data/services/auth with the new user's credentials must succeed."""
        response = requests.post(
            f"{base_url}/data/services/auth",
            data={
                "username": test_username,
                "password": "Smoke$Test1!",
            },
            timeout=30,
        )
        assert response.status_code == 200, (
            f"New user login failed: HTTP {response.status_code}. Body: {response.text[:500]}"
        )
        token = response.text.strip()
        assert token, "New user login response body is empty — expected a JSESSIONID token."


# ---------------------------------------------------------------------------
# Test 5 — Project creation
# ---------------------------------------------------------------------------

class TestProjectCreation:
    def test_create_project(
        self,
        base_url: str,
        admin_session: requests.Session,
        test_project_id: str,
    ) -> None:
        """PUT /data/projects/{id} must create a project and return 200/201."""
        response = admin_session.put(
            f"{base_url}/data/projects/{test_project_id}",
            params={
                "project": test_project_id,
                "name": f"Smoke Test Project {test_project_id}",
                "description": "Automatically created by the smoke test suite",
            },
            timeout=30,
        )
        assert response.status_code in (200, 201), (
            f"Project creation failed: HTTP {response.status_code}. Body: {response.text[:500]}"
        )

    def test_get_created_project(
        self,
        base_url: str,
        admin_session: requests.Session,
        test_project_id: str,
    ) -> None:
        """GET /data/projects/{id} must confirm the project exists."""
        response = admin_session.get(
            f"{base_url}/data/projects/{test_project_id}",
            params={"format": "json"},
            timeout=30,
        )
        assert response.status_code == 200, (
            f"GET project failed: HTTP {response.status_code}. Body: {response.text[:500]}"
        )


# ---------------------------------------------------------------------------
# Test 6 — Project listing
# ---------------------------------------------------------------------------

class TestProjectListing:
    def test_project_listing_includes_created_project(
        self,
        base_url: str,
        admin_session: requests.Session,
        test_project_id: str,
    ) -> None:
        """GET /data/projects must include the project created in test 5."""
        response = admin_session.get(
            f"{base_url}/data/projects",
            params={"format": "json"},
            timeout=30,
        )
        assert response.status_code == 200, (
            f"Project listing failed: HTTP {response.status_code}. Body: {response.text[:500]}"
        )

        # XNAT wraps results in ResultSet > Result
        body = response.json()
        results = (
            body.get("ResultSet", {}).get("Result", [])
            if isinstance(body, dict)
            else body
        )
        project_ids = [
            r.get("ID", "") or r.get("id", "")
            for r in results
        ]
        assert test_project_id in project_ids, (
            f"Project '{test_project_id}' not found in listing. "
            f"Found IDs: {project_ids[:20]}"
        )


# ---------------------------------------------------------------------------
# Test 7 — Cleanup
# ---------------------------------------------------------------------------

class TestCleanup:
    def test_delete_project(
        self,
        base_url: str,
        admin_session: requests.Session,
        test_project_id: str,
    ) -> None:
        """DELETE /data/projects/{id} must return 200."""
        response = admin_session.delete(
            f"{base_url}/data/projects/{test_project_id}",
            timeout=30,
        )
        assert response.status_code == 200, (
            f"Project deletion failed: HTTP {response.status_code}. Body: {response.text[:500]}"
        )

    def test_confirm_project_deleted(
        self,
        base_url: str,
        admin_session: requests.Session,
        test_project_id: str,
    ) -> None:
        """GET /data/projects/{id} after deletion must return 403 or 404."""
        response = admin_session.get(
            f"{base_url}/data/projects/{test_project_id}",
            params={"format": "json"},
            timeout=30,
        )
        assert response.status_code in (403, 404), (
            f"Expected 403 or 404 after project deletion, "
            f"got {response.status_code}. Body: {response.text[:200]}"
        )

    def test_delete_user(
        self,
        base_url: str,
        admin_session: requests.Session,
        test_username: str,
    ) -> None:
        """DELETE /xapi/users/{username} must return 200."""
        response = admin_session.delete(
            f"{base_url}/xapi/users/{test_username}",
            timeout=30,
        )
        assert response.status_code == 200, (
            f"User deletion failed: HTTP {response.status_code}. Body: {response.text[:500]}"
        )

    def test_confirm_user_deleted(
        self,
        base_url: str,
        admin_session: requests.Session,
        test_username: str,
    ) -> None:
        """GET /xapi/users/{username} after deletion must return 404."""
        response = admin_session.get(
            f"{base_url}/xapi/users/{test_username}",
            timeout=30,
        )
        assert response.status_code == 404, (
            f"Expected 404 after user deletion, "
            f"got {response.status_code}. Body: {response.text[:200]}"
        )
