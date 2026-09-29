#!/usr/bin/env bash
# Shared helpers for the evento-lab-cluster scripts. Sourced, not executed.
# Works under Git Bash (Windows), macOS and Linux.

# pwd -W (Git Bash) yields D:/... paths that docker.exe understands; elsewhere plain pwd.
LAB_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && { pwd -W 2>/dev/null || pwd; })"
REPO_DIR="$(cd "$LAB_DIR/.." && { pwd -W 2>/dev/null || pwd; })"
DOCKER_DIR="$LAB_DIR/docker"
COMPOSE_PROJECT="evento-lab-cluster"
# LAB_TAG: explicit value, else the tag of the running stack (so run-scenario.sh / repair and the
# chaos driver's 'compose up' of a removed service never fall back to a different image), else dev.
if [ -z "${LAB_TAG:-}" ]; then
  LAB_TAG="$(docker inspect -f '{{.Config.Image}}' evento-eu 2>/dev/null | sed -n 's#^evento-lab-cluster/server:##p')"
fi
export LAB_TAG="${LAB_TAG:-dev}"

# (Output redirection is done with the shell, never curl -o /dev/null: a native Windows
# curl cannot open /dev/null once conversion is off.)
# Git Bash rewrites arguments that look like POSIX paths (/app/..., /pg/...) before they
# reach docker.exe; the scripts only ever pass container-side paths, so turn that off.
export MSYS_NO_PATHCONV=1
export MSYS2_ARG_CONV_EXCL="*"

compose() {
  docker compose -p "$COMPOSE_PROJECT" -f "$DOCKER_DIR/compose.yaml" "$@"
}

# JDK 25 for Gradle: honour an explicit JAVA_HOME that is already 25, otherwise look in the
# usual places (the Windows lab host, macOS java_home, SDKMAN-style ~/.jdks).
ensure_jdk25() {
  if [ -n "${JAVA_HOME:-}" ] && "$JAVA_HOME/bin/java" -version 2>&1 | grep -q 'version "25'; then
    return 0
  fi
  local c
  for c in "C:/Users/Gaborando/.jdks/openjdk-25.0.1" "$HOME/.jdks/openjdk-25.0.1" "$HOME"/.jdks/*25*; do
    if [ -x "$c/bin/java" ] || [ -x "$c/bin/java.exe" ]; then export JAVA_HOME="$c"; return 0; fi
  done
  if [ -x /usr/libexec/java_home ]; then
    JAVA_HOME="$(/usr/libexec/java_home -v 25 2>/dev/null)" && export JAVA_HOME && return 0
  fi
  echo "warning: no JDK 25 found; relying on Gradle toolchain auto-detection (JAVA_HOME=${JAVA_HOME:-unset})" >&2
}

gradlew() {
  (cd "$REPO_DIR" && ./gradlew "$@")
}

log() { printf '[lab] %s\n' "$*" >&2; }
