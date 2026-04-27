"""
test_site_config.py — site-level configuration endpoints.

Covers the surface used by:
  - the K8s readiness/liveness probe (GET /xapi/siteConfig/initialized,
    must return 200 without authentication)
  - the build-info endpoint surfaced in admin UI (GET /xapi/siteConfig/buildInfo)
  - admin-only round-trip mutations against the main /xapi/siteConfig bag

The first test was previously living in test_smoke.py (TestHealthCheck);
moved here so all site-config behaviour is in one place.
"""

from __future__ import annotations

import pytest
import requests


# ---------------------------------------------------------------------------
# Read-only checks
# ---------------------------------------------------------------------------

class TestSiteConfigGet:
    def test_site_config_get(
        self, base_url: str, admin_session: requests.Session
    ) -> None:
        """GET /xapi/siteConfig must return 200 with admin auth and a non-empty body."""
        response = admin_session.get(
            f"{base_url}/xapi/siteConfig", timeout=30
        )
        assert response.status_code == 200, (
            f"Expected 200 from /xapi/siteConfig, got {response.status_code}. "
            f"Body: {response.text[:500]}"
        )
        # /xapi/siteConfig returns JSON; must at least have siteId.
        data = response.json()
        assert isinstance(data, dict), (
            f"Expected JSON object from /xapi/siteConfig, got {type(data).__name__}"
        )
        assert "siteId" in data, (
            f"siteConfig response missing 'siteId' key. Keys: {list(data.keys())[:20]}"
        )


class TestBuildInfo:
    def test_site_preferences_buildinfo(
        self, base_url: str, admin_session: requests.Session
    ) -> None:
        """GET /xapi/siteConfig/buildInfo returns build metadata used by the admin UI."""
        response = admin_session.get(
            f"{base_url}/xapi/siteConfig/buildInfo", timeout=30
        )
        assert response.status_code == 200, (
            f"buildInfo failed: HTTP {response.status_code}. "
            f"Body: {response.text[:500]}"
        )
        data = response.json()
        # XNAT build info exposes at least a version field; exact key
        # naming has shifted across releases (`version`, `buildNumber`,
        # `buildDate`), so accept any one of them.
        assert any(
            k in data for k in ("version", "buildNumber", "buildDate", "Version")
        ), f"buildInfo response missing version metadata. Keys: {list(data.keys())[:20]}"


class TestUnauthenticatedHealthEndpoint:
    def test_buildinfo_reachable_unauthenticated(self, base_url: str) -> None:
        """
        GET /xapi/siteConfig/buildInfo must return 200 with no authentication.

        Empirical: in this XNAT release, /xapi/siteConfig/initialized
        actually requires auth (returns 401), even though older docs and
        the original migration plan assumed it was unauthenticated. The
        endpoint that genuinely returns 200 to anonymous callers is
        /xapi/siteConfig/buildInfo — use that as the K8s readiness probe
        in Phase B's Helm chart instead. Test uses a fresh Session,
        explicitly NOT admin_session, to prove the unauth contract.
        """
        anon = requests.Session()
        response = anon.get(
            f"{base_url}/xapi/siteConfig/buildInfo", timeout=30
        )
        assert response.status_code == 200, (
            f"/xapi/siteConfig/buildInfo must be reachable unauthenticated. "
            f"Got HTTP {response.status_code}. Body: {response.text[:200]}"
        )
        # Body is JSON build metadata — must parse and have at least one
        # version-shaped field.
        data = response.json()
        assert isinstance(data, dict), (
            f"Expected JSON object from buildInfo, got {type(data).__name__}"
        )


# ---------------------------------------------------------------------------
# Mutation round-trip — admin email
# ---------------------------------------------------------------------------

class TestSiteConfigUpdate:
    def test_admin_email_roundtrip(
        self, base_url: str, admin_session: requests.Session
    ) -> None:
        """
        PUT a new adminEmail, GET it back, restore original.

        XNAT's siteConfig endpoint accepts partial updates via POST with a
        JSON body containing only the changed fields. The original value is
        always restored in a finally block so a failed assertion doesn't
        leave the instance in a degraded state for later tests.
        """
        # Snapshot the current value
        before = admin_session.get(
            f"{base_url}/xapi/siteConfig/adminEmail", timeout=30
        )
        assert before.status_code == 200, (
            f"Could not read current adminEmail: HTTP {before.status_code}"
        )
        original_email = before.text.strip().strip('"')

        new_email = "smoke-test-admin@example.com"
        try:
            update = admin_session.post(
                f"{base_url}/xapi/siteConfig",
                json={"adminEmail": new_email},
                timeout=30,
            )
            assert update.status_code in (200, 201, 204), (
                f"adminEmail update failed: HTTP {update.status_code}. "
                f"Body: {update.text[:500]}"
            )

            after = admin_session.get(
                f"{base_url}/xapi/siteConfig/adminEmail", timeout=30
            )
            assert after.status_code == 200
            assert after.text.strip().strip('"') == new_email, (
                f"adminEmail did not round-trip. "
                f"Wrote {new_email!r}, read back {after.text.strip()!r}"
            )
        finally:
            # Always restore — even if the assertion above failed.
            admin_session.post(
                f"{base_url}/xapi/siteConfig",
                json={"adminEmail": original_email},
                timeout=30,
            )
