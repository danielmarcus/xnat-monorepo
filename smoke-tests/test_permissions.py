"""
test_permissions.py — XNAT project role enforcement.

Two-actor tests: admin (owner) creates a project, then asserts what a
non-admin user (the `user_session` fixture) can and cannot do depending
on their group membership.

XNAT projects have three built-in groups: Owners, Members,
Collaborators. Owners have full control; Members can read + write
within the project; Collaborators are read-only. These tests are the
canary for any future change to that role logic — easy to break
silently because the failure mode is "tests still pass against admin
but normal users get over-/under-privileged in production."
"""

from __future__ import annotations

import requests


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _add_to_group(
    admin: requests.Session,
    base_url: str,
    project: str,
    group: str,
    username: str,
) -> requests.Response:
    """PUT a user into a project group (Owners | Members | Collaborators)."""
    return admin.put(
        f"{base_url}/data/projects/{project}/users/{group}/{username}",
        timeout=30,
    )


def _project_visible_as(
    user_sess: requests.Session,
    base_url: str,
    project: str,
) -> int:
    """Return the HTTP status the given user gets when GETing the project."""
    return user_sess.get(
        f"{base_url}/data/projects/{project}",
        params={"format": "json"},
        timeout=30,
    ).status_code


# ---------------------------------------------------------------------------
# Membership operations
# ---------------------------------------------------------------------------

class TestAddMember:
    def test_owner_can_add_member(
        self,
        base_url: str,
        admin_session: requests.Session,
        isolated_project: str,
        user_session,
    ) -> None:
        """Admin (project owner) PUTs the user into the Members group."""
        response = _add_to_group(
            admin_session,
            base_url,
            isolated_project,
            "Members",
            user_session.username,
        )
        assert response.status_code in (200, 201), (
            f"Add member failed: HTTP {response.status_code}. "
            f"Body: {response.text[:500]}"
        )


# ---------------------------------------------------------------------------
# Read access by role
# ---------------------------------------------------------------------------

class TestMemberCanRead:
    def test_member_can_read_owned_project(
        self,
        base_url: str,
        admin_session: requests.Session,
        isolated_project: str,
        user_session,
    ) -> None:
        _add_to_group(
            admin_session,
            base_url,
            isolated_project,
            "Members",
            user_session.username,
        ).raise_for_status()

        status = _project_visible_as(
            user_session.session, base_url, isolated_project
        )
        assert status == 200, (
            f"Member should see project (200), got {status}"
        )


class TestNonMemberCannotRead:
    def test_nonmember_cannot_read_private_project(
        self,
        base_url: str,
        isolated_project: str,
        user_session,
    ) -> None:
        """
        With no group membership and project access_level=private (the
        XNAT default), the user must be denied. Older XNAT releases
        returned 403; newer ones return 404 (info hiding). Accept both.
        """
        status = _project_visible_as(
            user_session.session, base_url, isolated_project
        )
        assert status in (403, 404), (
            f"Non-member should be denied (403/404), got {status}"
        )


# ---------------------------------------------------------------------------
# Write access by role — the actual enforcement contract
# ---------------------------------------------------------------------------

class TestMemberCannotDelete:
    def test_member_cannot_delete_project(
        self,
        base_url: str,
        admin_session: requests.Session,
        isolated_project: str,
        user_session,
    ) -> None:
        """Members may write within a project but not delete it — only Owners can."""
        _add_to_group(
            admin_session,
            base_url,
            isolated_project,
            "Members",
            user_session.username,
        ).raise_for_status()

        delete = user_session.session.delete(
            f"{base_url}/data/projects/{isolated_project}",
            timeout=30,
        )
        assert delete.status_code in (403, 401), (
            f"Member should NOT be able to delete project. "
            f"Expected 403/401, got {delete.status_code}"
        )


class TestCollaboratorReadButNotWrite:
    def test_collaborator_can_read_but_cannot_write(
        self,
        base_url: str,
        admin_session: requests.Session,
        isolated_project: str,
        user_session,
    ) -> None:
        """
        Collaborator role: GET project → 200, PUT new subject → 403.

        This single assertion pair is the most load-bearing role check in
        the suite. Any regression in the auth interceptors during the
        Spring 6 / Tomcat 10 migration will surface here first.
        """
        _add_to_group(
            admin_session,
            base_url,
            isolated_project,
            "Collaborators",
            user_session.username,
        ).raise_for_status()

        # Read should succeed
        read_status = _project_visible_as(
            user_session.session, base_url, isolated_project
        )
        assert read_status == 200, (
            f"Collaborator should be able to GET project (200), got {read_status}"
        )

        # Write should fail
        subject_label = f"BLOCKED_{user_session.username[-6:]}"
        write_resp = user_session.session.put(
            f"{base_url}/data/projects/{isolated_project}/subjects/{subject_label}",
            timeout=30,
        )
        assert write_resp.status_code in (403, 401), (
            f"Collaborator should NOT create subjects. "
            f"Expected 403/401, got {write_resp.status_code}. "
            f"Body: {write_resp.text[:500]}"
        )
