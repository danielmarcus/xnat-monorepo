"""
test_search.py — XNAT stored + inline search APIs.

XNAT search lives at /data/search and /data/search/saved. Both accept a
fixed XML grammar (xdat:bundle / xdat:search) — there is no JSON body
form. These tests exercise the wire format that the XNAT web UI itself
uses to drive the data tables.

If any of these tests fail with HTTP 500, look at the server log: the
search engine raises checked exceptions that surface as plain stack
traces in /usr/local/tomcat/logs/catalina.out.

Marked `slow` because search execution touches every row of the
indicated xsiType in the database.
"""

from __future__ import annotations

import uuid

import pytest
import requests


# ---------------------------------------------------------------------------
# XML payload builders
# ---------------------------------------------------------------------------

def _stored_search_bundle(search_id: str, root_xsi_type: str) -> str:
    """
    Build the XML body for `PUT /data/search/saved/{id}`.

    Two columns (PROJECT, LABEL) is the minimum that returns a useful
    ResultSet — single-column searches sometimes get optimized away.
    """
    return f"""<?xml version="1.0" encoding="UTF-8"?>
<xdat:bundle xmlns:xdat="http://nrg.wustl.edu/security"
             ID="{search_id}"
             brief-description="Smoke-test stored search"
             description="Auto-created by the XNAT smoke suite. Safe to delete.">
  <xdat:root_element_name>{root_xsi_type}</xdat:root_element_name>
  <xdat:search_field>
    <xdat:element_name>{root_xsi_type}</xdat:element_name>
    <xdat:field_ID>PROJECT</xdat:field_ID>
    <xdat:sequence>0</xdat:sequence>
    <xdat:type>string</xdat:type>
    <xdat:header>Project</xdat:header>
  </xdat:search_field>
  <xdat:search_field>
    <xdat:element_name>{root_xsi_type}</xdat:element_name>
    <xdat:field_ID>LABEL</xdat:field_ID>
    <xdat:sequence>1</xdat:sequence>
    <xdat:type>string</xdat:type>
    <xdat:header>Label</xdat:header>
  </xdat:search_field>
</xdat:bundle>
""".strip()


def _inline_search_xml(root_xsi_type: str) -> str:
    """Body for `POST /data/search` — one-shot search, not stored."""
    return f"""<?xml version="1.0" encoding="UTF-8"?>
<xdat:search xmlns:xdat="http://nrg.wustl.edu/security"
             allow-diff-columns="0"
             secure="false">
  <xdat:root_element_name>{root_xsi_type}</xdat:root_element_name>
  <xdat:search_field>
    <xdat:element_name>{root_xsi_type}</xdat:element_name>
    <xdat:field_ID>PROJECT</xdat:field_ID>
    <xdat:sequence>0</xdat:sequence>
    <xdat:type>string</xdat:type>
    <xdat:header>Project</xdat:header>
  </xdat:search_field>
  <xdat:search_field>
    <xdat:element_name>{root_xsi_type}</xdat:element_name>
    <xdat:field_ID>LABEL</xdat:field_ID>
    <xdat:sequence>1</xdat:sequence>
    <xdat:type>string</xdat:type>
    <xdat:header>Label</xdat:header>
  </xdat:search_field>
</xdat:search>
""".strip()


def _short_id(prefix: str) -> str:
    return f"{prefix}_{uuid.uuid4().hex[:8].upper()}"


# ---------------------------------------------------------------------------
# Stored search create + execute
# ---------------------------------------------------------------------------

@pytest.mark.slow
class TestStoredSearch:
    def test_stored_search_create(
        self, base_url: str, admin_session: requests.Session
    ) -> None:
        search_id = _short_id("SMOKE")
        payload = _stored_search_bundle(search_id, "xnat:mrSessionData")

        try:
            response = admin_session.put(
                f"{base_url}/data/search/saved/{search_id}",
                data=payload,
                headers={"Content-Type": "text/xml"},
                timeout=30,
            )
            assert response.status_code in (200, 201), (
                f"Stored search creation failed: HTTP {response.status_code}. "
                f"Body: {response.text[:500]}"
            )
        finally:
            # Best-effort cleanup so a failed assertion doesn't leave
            # garbage stored searches behind.
            try:
                admin_session.delete(
                    f"{base_url}/data/search/saved/{search_id}", timeout=15
                )
            except Exception:
                pass

    def test_stored_search_execute(
        self, base_url: str, admin_session: requests.Session
    ) -> None:
        search_id = _short_id("SMOKE")
        payload = _stored_search_bundle(search_id, "xnat:mrSessionData")
        admin_session.put(
            f"{base_url}/data/search/saved/{search_id}",
            data=payload,
            headers={"Content-Type": "text/xml"},
            timeout=30,
        ).raise_for_status()

        try:
            execute = admin_session.get(
                f"{base_url}/data/search/saved/{search_id}/results",
                params={"format": "json"},
                timeout=60,
            )
            assert execute.status_code == 200, (
                f"Stored search execution failed: HTTP {execute.status_code}. "
                f"Body: {execute.text[:500]}"
            )
            body = execute.json()
            # ResultSet always present — Result list may be empty on a
            # fresh DB. We're testing the *plumbing*, not the data.
            assert "ResultSet" in body, (
                f"Search response missing ResultSet wrapper. Keys: {list(body.keys())}"
            )
            assert isinstance(
                body["ResultSet"].get("Result", []), list
            ), "ResultSet.Result must be a list"
        finally:
            try:
                admin_session.delete(
                    f"{base_url}/data/search/saved/{search_id}", timeout=15
                )
            except Exception:
                pass


# ---------------------------------------------------------------------------
# Inline search (no save)
# ---------------------------------------------------------------------------

@pytest.mark.slow
class TestInlineSearch:
    def test_search_by_xsi_type(
        self, base_url: str, admin_session: requests.Session
    ) -> None:
        """POST a one-shot xdat:search bundle for MR sessions."""
        payload = _inline_search_xml("xnat:mrSessionData")
        response = admin_session.post(
            f"{base_url}/data/search",
            data=payload,
            params={"format": "json"},
            headers={"Content-Type": "text/xml"},
            timeout=60,
        )
        assert response.status_code == 200, (
            f"Inline search failed: HTTP {response.status_code}. "
            f"Body: {response.text[:500]}"
        )
        body = response.json()
        assert "ResultSet" in body, (
            f"Inline search response missing ResultSet. "
            f"Keys: {list(body.keys())}"
        )
        assert isinstance(
            body["ResultSet"].get("Result", []), list
        ), "ResultSet.Result must be a list"
