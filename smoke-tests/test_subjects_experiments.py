"""
test_subjects_experiments.py — XNAT subject + imaging session CRUD.

Each test runs against a freshly-created `isolated_project` (see
conftest.py) so subject/session state never leaks between tests. This
trades a little speed for full isolation — important because the
session-builder hits the persistence layer hard and is the canary for
Phase C (Tomcat 10 / Jakarta) regressions.

Coverage map:
  TestSubjectCRUD          create, list, metadata roundtrip
  TestSessionCRUD          create MR + CT sessions
  TestSessionInSubject     session appears in subject's experiment listing
  TestCascadeDelete        delete session, delete subject -> children gone
"""

from __future__ import annotations

import uuid

import requests


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _put_subject(
    session: requests.Session,
    base_url: str,
    project: str,
    label: str,
    *,
    group: str | None = None,
) -> requests.Response:
    """PUT a new subject under the given project."""
    params: dict[str, str] = {}
    if group is not None:
        params["group"] = group
    return session.put(
        f"{base_url}/data/projects/{project}/subjects/{label}",
        params=params,
        timeout=30,
    )


def _put_experiment(
    session: requests.Session,
    base_url: str,
    project: str,
    subject: str,
    label: str,
    *,
    xsi_type: str,
) -> requests.Response:
    """PUT a new imaging session (experiment) under the given subject."""
    return session.put(
        f"{base_url}/data/projects/{project}/subjects/{subject}/experiments/{label}",
        params={"xsiType": xsi_type},
        timeout=30,
    )


def _short_id(prefix: str) -> str:
    return f"{prefix}_{uuid.uuid4().hex[:6].upper()}"


# ---------------------------------------------------------------------------
# Subject CRUD
# ---------------------------------------------------------------------------

class TestSubjectCRUD:
    def test_create_subject(
        self,
        base_url: str,
        admin_session: requests.Session,
        isolated_project: str,
    ) -> None:
        subject_label = _short_id("S")
        response = _put_subject(
            admin_session, base_url, isolated_project, subject_label
        )
        assert response.status_code in (200, 201), (
            f"Subject creation failed: HTTP {response.status_code}. "
            f"Body: {response.text[:500]}"
        )

    def test_list_subjects(
        self,
        base_url: str,
        admin_session: requests.Session,
        isolated_project: str,
    ) -> None:
        subject_label = _short_id("S")
        _put_subject(
            admin_session, base_url, isolated_project, subject_label
        ).raise_for_status()

        listing = admin_session.get(
            f"{base_url}/data/projects/{isolated_project}/subjects",
            params={"format": "json"},
            timeout=30,
        )
        assert listing.status_code == 200, (
            f"Subject listing failed: HTTP {listing.status_code}"
        )
        results = listing.json().get("ResultSet", {}).get("Result", [])
        labels = [r.get("label", "") for r in results]
        assert subject_label in labels, (
            f"Subject {subject_label!r} not found in listing. "
            f"Found: {labels[:20]}"
        )

    def test_subject_metadata_roundtrip(
        self,
        base_url: str,
        admin_session: requests.Session,
        isolated_project: str,
    ) -> None:
        """Set 'group' on a subject via PUT, GET it back."""
        subject_label = _short_id("S")
        group_value = "smoke-test-group"
        _put_subject(
            admin_session,
            base_url,
            isolated_project,
            subject_label,
            group=group_value,
        ).raise_for_status()

        get_resp = admin_session.get(
            f"{base_url}/data/projects/{isolated_project}/subjects/{subject_label}",
            params={"format": "json"},
            timeout=30,
        )
        assert get_resp.status_code == 200, (
            f"GET subject failed: HTTP {get_resp.status_code}"
        )
        body = get_resp.json()
        # XNAT wraps the subject record in items[0].data_fields. Be tolerant:
        # some deployments return a flatter structure under ResultSet.
        data_fields = (
            body.get("items", [{}])[0].get("data_fields", {})
            if "items" in body
            else body.get("ResultSet", {}).get("Result", [{}])[0]
        )
        assert data_fields.get("group") == group_value, (
            f"Expected group={group_value!r}, got {data_fields.get('group')!r}. "
            f"Full body keys: {list(body.keys())}"
        )


# ---------------------------------------------------------------------------
# Session CRUD
# ---------------------------------------------------------------------------

class TestSessionCRUD:
    def test_create_mr_session(
        self,
        base_url: str,
        admin_session: requests.Session,
        isolated_project: str,
    ) -> None:
        subject = _short_id("S")
        experiment = _short_id("MR")
        _put_subject(
            admin_session, base_url, isolated_project, subject
        ).raise_for_status()

        response = _put_experiment(
            admin_session,
            base_url,
            isolated_project,
            subject,
            experiment,
            xsi_type="xnat:mrSessionData",
        )
        assert response.status_code in (200, 201), (
            f"MR session creation failed: HTTP {response.status_code}. "
            f"Body: {response.text[:500]}"
        )

    def test_create_ct_session(
        self,
        base_url: str,
        admin_session: requests.Session,
        isolated_project: str,
    ) -> None:
        subject = _short_id("S")
        experiment = _short_id("CT")
        _put_subject(
            admin_session, base_url, isolated_project, subject
        ).raise_for_status()

        response = _put_experiment(
            admin_session,
            base_url,
            isolated_project,
            subject,
            experiment,
            xsi_type="xnat:ctSessionData",
        )
        assert response.status_code in (200, 201), (
            f"CT session creation failed: HTTP {response.status_code}. "
            f"Body: {response.text[:500]}"
        )


# ---------------------------------------------------------------------------
# Session-in-subject listing
# ---------------------------------------------------------------------------

class TestSessionInSubject:
    def test_session_appears_in_subject_experiments(
        self,
        base_url: str,
        admin_session: requests.Session,
        isolated_project: str,
    ) -> None:
        subject = _short_id("S")
        experiment = _short_id("MR")
        _put_subject(
            admin_session, base_url, isolated_project, subject
        ).raise_for_status()
        _put_experiment(
            admin_session,
            base_url,
            isolated_project,
            subject,
            experiment,
            xsi_type="xnat:mrSessionData",
        ).raise_for_status()

        listing = admin_session.get(
            f"{base_url}/data/projects/{isolated_project}/subjects/{subject}/experiments",
            params={"format": "json"},
            timeout=30,
        )
        assert listing.status_code == 200, (
            f"Experiment listing failed: HTTP {listing.status_code}"
        )
        results = listing.json().get("ResultSet", {}).get("Result", [])
        labels = [r.get("label", "") for r in results]
        assert experiment in labels, (
            f"Experiment {experiment!r} not found under subject {subject!r}. "
            f"Found: {labels[:20]}"
        )


# ---------------------------------------------------------------------------
# Cascade deletes
# ---------------------------------------------------------------------------

class TestCascadeDelete:
    def test_delete_session_then_404(
        self,
        base_url: str,
        admin_session: requests.Session,
        isolated_project: str,
    ) -> None:
        subject = _short_id("S")
        experiment = _short_id("MR")
        _put_subject(
            admin_session, base_url, isolated_project, subject
        ).raise_for_status()
        _put_experiment(
            admin_session,
            base_url,
            isolated_project,
            subject,
            experiment,
            xsi_type="xnat:mrSessionData",
        ).raise_for_status()

        delete = admin_session.delete(
            f"{base_url}/data/projects/{isolated_project}/subjects/{subject}/experiments/{experiment}",
            timeout=30,
        )
        assert delete.status_code in (200, 204), (
            f"Session DELETE failed: HTTP {delete.status_code}"
        )

        follow_up = admin_session.get(
            f"{base_url}/data/projects/{isolated_project}/subjects/{subject}/experiments/{experiment}",
            params={"format": "json"},
            timeout=30,
        )
        assert follow_up.status_code in (403, 404), (
            f"Expected 403/404 after session delete, got {follow_up.status_code}"
        )

    def test_delete_subject_removes_sessions(
        self,
        base_url: str,
        admin_session: requests.Session,
        isolated_project: str,
    ) -> None:
        subject = _short_id("S")
        experiment = _short_id("CT")
        _put_subject(
            admin_session, base_url, isolated_project, subject
        ).raise_for_status()
        _put_experiment(
            admin_session,
            base_url,
            isolated_project,
            subject,
            experiment,
            xsi_type="xnat:ctSessionData",
        ).raise_for_status()

        # Cascading subject delete needs ?removeFiles=true on some XNAT
        # versions; pass it for portability.
        delete = admin_session.delete(
            f"{base_url}/data/projects/{isolated_project}/subjects/{subject}",
            params={"removeFiles": "true"},
            timeout=30,
        )
        assert delete.status_code in (200, 204), (
            f"Subject DELETE failed: HTTP {delete.status_code}"
        )

        # The child experiment should also be gone.
        follow_up = admin_session.get(
            f"{base_url}/data/projects/{isolated_project}/subjects/{subject}/experiments/{experiment}",
            params={"format": "json"},
            timeout=30,
        )
        assert follow_up.status_code in (403, 404), (
            f"Expected 403/404 for child session after subject delete, "
            f"got {follow_up.status_code}"
        )
