#!/usr/bin/env bash
# Builds the jars (the evento-server of the working tree, the three benchmark apps) and the
# images bench/evento-server, bench/evento-app, bench/axon-app, bench/axon-server-app.
set -euo pipefail
here="$(cd "$(dirname "$0")/.." && pwd)"
root="$(cd "$here/../.." && pwd)"

(cd "$root" && ./gradlew -q :evento-server:bootJar)
(cd "$here" && ./gradlew -q :evento-app:installDist :axon-app:bootJar :axon-server-app:bootJar)

# image name, then a boot jar (copied as app/app.jar) or a distribution lib/ directory
image() {
  local name="$1" source="$2"
  local ctx="$here/docker/context/$name"
  rm -rf "$ctx"; mkdir -p "$ctx/app"
  if [ -d "$source" ]; then cp -r "$source" "$ctx/app/lib"; else cp "$source" "$ctx/app/app.jar"; fi
  docker build -q -f "$here/docker/Dockerfile" -t "bench/$name" "$ctx" >/dev/null
  echo "built bench/$name"
}

# The jar just built (build/libs may still hold jars of older versions).
image evento-server "$(ls -t "$root"/evento-server/build/libs/evento-server-*.jar | grep -v plain | head -1)"
image evento-app "$here/evento-app/build/install/evento-app/lib"
image axon-app "$here/axon-app/build/libs/axon-app.jar"
image axon-server-app "$here/axon-server-app/build/libs/axon-server-app.jar"
