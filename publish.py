import os
import sys

import requests

NAMESPACE = "com.eventoframework"
BASE = "https://ossrh-staging-api.central.sonatype.com"


def get_gradle_properties():
    """Best-effort read of Sonatype creds from a gradle.properties file.

    Looks in ~/.gradle/gradle.properties first, then ./gradle.properties.
    Returns an empty dict if neither exists (e.g. on CI, where the
    credentials come from the environment instead).
    """
    for path in (
        os.path.expanduser("~/.gradle/gradle.properties"),
        os.path.join(os.path.dirname(os.path.abspath(__file__)), "gradle.properties"),
    ):
        if not os.path.exists(path):
            continue
        properties = {}
        with open(path, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line or line.startswith("#") or "=" not in line:
                    continue
                key, value = line.split("=", 1)
                properties[key.strip()] = value.strip()
        return properties
    return {}


def resolve_credentials():
    """Environment wins (CI); gradle.properties is the local fallback."""
    props = get_gradle_properties()
    username = os.environ.get("MAVEN_CENTRAL_USERNAME") or props.get("mavenCentralUsername")
    password = os.environ.get("MAVEN_CENTRAL_PASSWORD") or props.get("mavenCentralPassword")
    if not username or not password:
        sys.exit(
            "Missing Sonatype credentials: set MAVEN_CENTRAL_USERNAME/"
            "MAVEN_CENTRAL_PASSWORD or mavenCentralUsername/mavenCentralPassword "
            "in ~/.gradle/gradle.properties."
        )
    return username, password


if __name__ == "__main__":
    auth = resolve_credentials()

    print("Staging repositories before promotion:")
    resp = requests.get(f"{BASE}/manual/search/repositories", auth=auth)
    resp.raise_for_status()
    print(resp.text)

    print(f"\nPromoting namespace {NAMESPACE} (publishing_type=automatic)...")
    resp = requests.post(
        f"{BASE}/manual/upload/defaultRepository/{NAMESPACE}?publishing_type=automatic",
        auth=auth,
    )
    resp.raise_for_status()
    print(resp.text)

    print("\nStaging repositories after promotion:")
    resp = requests.get(f"{BASE}/manual/search/repositories", auth=auth)
    resp.raise_for_status()
    print(resp.text)
