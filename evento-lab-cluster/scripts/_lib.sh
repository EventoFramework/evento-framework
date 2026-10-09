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

# JDK 25 for Gradle, update 3 or later: 25.0.1 and 25.0.2 have JDK-8370887 (a cancelled timed
# wait corrupts the virtual-thread timer heap, so a 150 ms sleep can take ~20 s and bundles and
# ITs stall). Honour an explicit JAVA_HOME only when it is a fixed 25, otherwise look in the
# usual places (SDKMAN/IntelliJ-style ~/.jdks newest first, macOS java_home).
jdk25_ok() {
  local v
  v="$("$1/bin/java" -version 2>&1 | sed -n 's/.*version "\(25[^"]*\)".*/\1/p' | head -1)"
  [ -n "$v" ] || return 1
  case "$v" in 25|25.0|25.0.0*|25.0.1|25.0.1.*|25.0.2|25.0.2.*) return 1 ;; esac
  return 0
}

ensure_jdk25() {
  if [ -n "${JAVA_HOME:-}" ] && jdk25_ok "$JAVA_HOME"; then
    return 0
  fi
  local c
  for c in $(ls -d "$HOME"/.jdks/*25* 2>/dev/null | sort -rV); do
    if jdk25_ok "$c"; then export JAVA_HOME="$c"; return 0; fi
  done
  if [ -x /usr/libexec/java_home ]; then
    c="$(/usr/libexec/java_home -v 25 2>/dev/null)" && jdk25_ok "$c" && export JAVA_HOME="$c" && return 0
  fi
  echo "warning: no JDK 25.0.3+ found (25.0.1/25.0.2 have JDK-8370887); relying on Gradle toolchain auto-detection (JAVA_HOME=${JAVA_HOME:-unset})" >&2
}

gradlew() {
  (cd "$REPO_DIR" && ./gradlew "$@")
}

log() { printf '[lab] %s\n' "$*" >&2; }
