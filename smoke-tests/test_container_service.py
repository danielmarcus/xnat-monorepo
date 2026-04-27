"""
test_container_service.py — XNAT Container Service (XCS) smoke tests.

The XCS exposes Docker-backed pipelines as XNAT-native APIs. These tests
exercise the small surface needed to confirm the integration is alive:
docker server config, image pull, command registration + launch, and
container status polling.

The whole module is skipped via `container_service_available` when the
docker host is not configured. In Phase B's EKS deploy XCS is NOT
enabled (it expects a Docker socket and that's a separate design
problem) — these tests only run on the EC2 / local Compose stack with
the docker socket bind-mounted in.

Marked `container_service` so the fast suite skips them. Also marked
`slow` because the hello-world pull + run takes ~30s end-to-end.
"""

from __future__ import annotations

import time
import uuid
from typing import Generator

import pytest
import requests


pytestmark = [pytest.mark.container_service, pytest.mark.slow]


@pytest.fixture(autouse=True)
def _skip_if_unavailable(container_service_available: bool) -> None:
    """Apply the available-or-skip gate to every test in this module."""
    if not container_service_available:
        pytest.skip(
            "Container service not configured (GET /xapi/docker/server "
            "returned no host). Skipping the entire module."
        )


# ---------------------------------------------------------------------------
# 1. Docker server reachable
# ---------------------------------------------------------------------------

class TestDockerServer:
    def test_docker_server_configured(
        self, base_url: str, admin_session: requests.Session
    ) -> None:
        response = admin_session.get(
            f"{base_url}/xapi/docker/server", timeout=15
        )
        assert response.status_code == 200, (
            f"GET /xapi/docker/server failed: HTTP {response.status_code}"
        )
        data = response.json()
        assert data.get("host"), (
            f"Docker server has no host configured. Body: {data}"
        )


# ---------------------------------------------------------------------------
# 2. Pull hello-world (no-op if cached, ~5s otherwise)
# ---------------------------------------------------------------------------

class TestImagePull:
    def test_pull_hello_world(
        self, base_url: str, admin_session: requests.Session
    ) -> None:
        # Hub ID is conventionally 0 (Docker Hub) — XNAT seeds it.
        response = admin_session.post(
            f"{base_url}/xapi/docker/hubs/0/pull",
            params={"image": "hello-world:latest"},
            timeout=120,
        )
        assert response.status_code in (200, 201, 204), (
            f"hello-world pull failed: HTTP {response.status_code}. "
            f"Body: {response.text[:500]}"
        )


# ---------------------------------------------------------------------------
# 3. Command registration + launch + status poll
#
# These three live together because a command with no wrappers is only
# meaningful in the context of being launched, and a launch is only
# verifiable by polling its status. Splitting them across module-scoped
# fixtures the way DICOM does is overkill for a single hello-world run.
# ---------------------------------------------------------------------------

@pytest.fixture
def registered_command(
    base_url: str, admin_session: requests.Session
) -> Generator[int, None, None]:
    """Register a minimal hello-world command, yield its ID, delete after."""
    payload = {
        "name": f"smoke-hello-{uuid.uuid4().hex[:6]}",
        "description": "Smoke test command — runs hello-world to completion.",
        "type": "docker",
        "image": "hello-world:latest",
        "version": "1.0",
        "command-line": "",
        "mounts": [],
        "environment-variables": {},
        "ports": {},
        "inputs": [],
        "outputs": [],
        "xnat": [],
    }
    create = admin_session.post(
        f"{base_url}/xapi/commands", json=payload, timeout=30
    )
    assert create.status_code in (200, 201), (
        f"Command registration failed: HTTP {create.status_code}. "
        f"Body: {create.text[:500]}"
    )
    # XNAT returns either the ID as plain text or a JSON object with
    # `id`. Handle both.
    body = create.text.strip()
    try:
        command_id = int(body)
    except ValueError:
        command_id = create.json().get("id")
        assert command_id, f"Could not parse command ID from: {body!r}"

    yield int(command_id)

    try:
        admin_session.delete(
            f"{base_url}/xapi/commands/{command_id}", timeout=15
        )
    except Exception:
        pass


class TestCommandRegistration:
    def test_register_command(self, registered_command: int) -> None:
        assert registered_command > 0, (
            f"Expected positive command ID, got {registered_command}"
        )


class TestCommandLaunch:
    def test_launch_command_returns_container(
        self,
        base_url: str,
        admin_session: requests.Session,
        registered_command: int,
    ) -> None:
        launch = admin_session.post(
            f"{base_url}/xapi/commands/{registered_command}/launch",
            timeout=60,
        )
        assert launch.status_code in (200, 201), (
            f"Launch failed: HTTP {launch.status_code}. "
            f"Body: {launch.text[:500]}"
        )
        body = launch.json() if launch.content else {}
        # Older XCS returns {"container-id": "..."}, newer
        # {"containerId": "..."} or `{"id": ...}`.
        container_ref = (
            body.get("container-id")
            or body.get("containerId")
            or body.get("id")
        )
        assert container_ref, (
            f"Launch response missing container reference. Body: {body}"
        )


class TestContainerCompletes:
    def test_container_runs_to_completion(
        self,
        base_url: str,
        admin_session: requests.Session,
        registered_command: int,
    ) -> None:
        launch = admin_session.post(
            f"{base_url}/xapi/commands/{registered_command}/launch",
            timeout=60,
        )
        launch.raise_for_status()
        body = launch.json()
        container_ref = (
            body.get("container-id")
            or body.get("containerId")
            or body.get("id")
        )
        assert container_ref, "no container reference returned by launch"

        # Poll up to 60s for terminal status. hello-world exits in <2s,
        # but the wrapper plus container-event ingestion is async.
        terminal = {"Complete", "Failed", "Killed"}
        for _ in range(12):
            status_resp = admin_session.get(
                f"{base_url}/xapi/containers/{container_ref}",
                timeout=15,
            )
            if status_resp.status_code == 200:
                status = status_resp.json().get("status", "")
                if status in terminal:
                    assert status == "Complete", (
                        f"hello-world container ended in non-Complete state: "
                        f"{status}. Body: {status_resp.text[:500]}"
                    )
                    return
            time.sleep(5)

        pytest.fail(
            f"Container {container_ref} did not reach a terminal state "
            f"within 60s. Last status response: {status_resp.text[:500]}"
        )
