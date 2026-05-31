import base64
import os
import sys
import urllib.error
import urllib.request

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


def request(method, url, auth):
    """Issue an HTTP request with Basic auth, returning the response body text.

    Raises on a non-2xx status (mirrors requests' raise_for_status()).
    """
    username, password = auth
    token = base64.b64encode(f"{username}:{password}".encode("utf-8")).decode("ascii")
    req = urllib.request.Request(url, method=method)
    req.add_header("Authorization", f"Basic {token}")
    try:
        with urllib.request.urlopen(req) as resp:
            return resp.read().decode("utf-8")
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8", errors="replace")
        sys.exit(f"HTTP {e.code} for {method} {url}:\n{body}")


if __name__ == "__main__":
    auth = resolve_credentials()

    print("Staging repositories before promotion:")
    print(request("GET", f"{BASE}/manual/search/repositories", auth))

    print(f"\nPromoting namespace {NAMESPACE} (publishing_type=automatic)...")
    print(request(
        "POST",
        f"{BASE}/manual/upload/defaultRepository/{NAMESPACE}?publishing_type=automatic",
        auth,
    ))

    print("\nStaging repositories after promotion:")
    print(request("GET", f"{BASE}/manual/search/repositories", auth))
