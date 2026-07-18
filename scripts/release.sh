#!/usr/bin/env bash
#
# release.sh — cut a new Evento Framework release.
#
# Bumps the version (patch / minor / major), commits the bump, creates an
# annotated semver tag, and pushes both. Pushing the tag is the single trigger
# that fans out to every release workflow:
#
#   * .github/workflows/release.yml ........... signed boot jar -> GitHub Release,
#                                               multi-arch image -> GHCR + Docker Hub,
#                                               libraries -> GitHub Packages
#   * .github/workflows/maven-build-and-push-repository.yaml .. libraries -> Maven Central
#
# Both workflows fire on tags matching 'v[0-9]+.[0-9]+.[0-9]*'.
#
# Usage:
#   scripts/release.sh [patch|minor|major]
#
# If the release type is omitted it is asked interactively. Nothing is pushed
# until you confirm the computed version.
#
# Windows: scripts/release.ps1 is the PowerShell twin — keep both in lockstep.
#
set -euo pipefail

# --- locate repo root so the script works from any CWD ----------------------
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

GRADLE_FILE="build.gradle"
VERSION_JSON="evento-server/version.json"
RELEASE_BRANCH="main"

die()  { printf '\033[31merror:\033[0m %s\n' "$*" >&2; exit 1; }
info() { printf '\033[36m==>\033[0m %s\n' "$*"; }
ok()   { printf '\033[32m  ✓\033[0m %s\n' "$*"; }

# --- preflight checks -------------------------------------------------------
command -v git >/dev/null   || die "git is not installed"
[[ -f "$GRADLE_FILE"   ]]   || die "$GRADLE_FILE not found — run from the repo or its scripts/ dir"
[[ -f "$VERSION_JSON"  ]]   || die "$VERSION_JSON not found"

branch="$(git branch --show-current)"
[[ "$branch" == "$RELEASE_BRANCH" ]] \
  || die "releases must be cut from '$RELEASE_BRANCH' (you are on '$branch')"

[[ -z "$(git status --porcelain)" ]] \
  || die "working tree is dirty — commit or stash changes before releasing"

info "Syncing with origin/$RELEASE_BRANCH"
git fetch --quiet origin "$RELEASE_BRANCH"
local_sha="$(git rev-parse @)"
remote_sha="$(git rev-parse "origin/$RELEASE_BRANCH")"
# We allow local to be AHEAD of origin: any unpushed commits ride along with the
# version-bump commit in the single push below, so the test CI fires once on
# 'main' instead of twice (once for a manual pre-push, once for the bump).
# Being BEHIND/diverged is still fatal — that needs a real pull/rebase.
if [[ "$local_sha" == "$remote_sha" ]]; then
  ok "branch '$RELEASE_BRANCH' is in sync with origin"
elif git merge-base --is-ancestor "origin/$RELEASE_BRANCH" HEAD; then
  ahead="$(git rev-list --count "origin/$RELEASE_BRANCH"..HEAD)"
  ok "branch '$RELEASE_BRANCH' is $ahead commit(s) ahead of origin — they will be pushed with the release"
else
  die "local '$RELEASE_BRANCH' has diverged from origin (it is behind) — pull/rebase first"
fi

# --- read the current version (build.gradle is the source of truth) ---------
current="$(sed -n "s/^version '\([0-9][^']*\)'.*/\1/p" "$GRADLE_FILE" | head -n1)"
[[ -n "$current" ]] || die "could not read current version from $GRADLE_FILE"
[[ "$current" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]] \
  || die "current version '$current' is not a clean MAJOR.MINOR.PATCH"
major="${BASH_REMATCH[1]}"; minor="${BASH_REMATCH[2]}"; patch="${BASH_REMATCH[3]}"
info "Current version: $current"

# --- determine release type -------------------------------------------------
release_type="${1:-}"
if [[ -z "$release_type" ]]; then
  printf 'Release type:\n  1) patch  (%d.%d.%d)\n  2) minor  (%d.%d.0)\n  3) major  (%d.0.0)\n' \
    "$major" "$minor" "$((patch + 1))" \
    "$major" "$((minor + 1))" \
    "$((major + 1))"
  read -rp "Choose [1/2/3 or patch/minor/major]: " release_type
fi

case "$release_type" in
  1|patch) new="$major.$minor.$((patch + 1))" ;;
  2|minor) new="$major.$((minor + 1)).0" ;;
  3|major) new="$((major + 1)).0.0" ;;
  *) die "invalid release type: '$release_type' (expected patch, minor, or major)" ;;
esac

tag="v$new"
git rev-parse -q --verify "refs/tags/$tag" >/dev/null \
  && die "tag $tag already exists"

# --- confirm ----------------------------------------------------------------
info "About to release: $current -> $new   (tag $tag)"
pending="$(git rev-list --count "origin/$RELEASE_BRANCH"..HEAD)"
if (( pending > 0 )); then
  echo "    $pending unpushed commit(s) will be pushed together with the version bump:"
  git --no-pager log --oneline "origin/$RELEASE_BRANCH"..HEAD | sed 's/^/      /'
fi
echo  "    This will commit the version bump, push '$RELEASE_BRANCH' (single push -> one test CI run),"
echo  "    push tag $tag, and trigger the GitHub Releases, Docker Hub/GHCR, and Maven Central workflows."
read -rp "Proceed? [y/N]: " confirm
[[ "$confirm" =~ ^[Yy]$ ]] || die "aborted by user"

# --- apply the bump ---------------------------------------------------------
info "Updating version to $new"
# 1) root project version
sed -i.bak "s/^version '$current'/version '$new'/" "$GRADLE_FILE"
# 2) eventoVersion ext property used by all subprojects
sed -i.bak "s/eventoVersion = '$current'/eventoVersion = '$new'/" "$GRADLE_FILE"
rm -f "$GRADLE_FILE.bak"
# 3) version.json read by release.yml to name the GitHub Release artifact
sed -i.bak "s/\"version\": \"$current\"/\"version\": \"$new\"/" "$VERSION_JSON"
rm -f "$VERSION_JSON.bak"

# sanity: every source of truth now agrees
grep -q "^version '$new'"          "$GRADLE_FILE"  || die "failed to bump root version in $GRADLE_FILE"
grep -q "eventoVersion = '$new'"   "$GRADLE_FILE"  || die "failed to bump eventoVersion in $GRADLE_FILE"
grep -q "\"version\": \"$new\""    "$VERSION_JSON" || die "failed to bump $VERSION_JSON"
ok "build.gradle and version.json now at $new"

# --- commit, tag, push ------------------------------------------------------
info "Committing and tagging"
git add "$GRADLE_FILE" "$VERSION_JSON"
git commit -q -m "chore(release): $new" -m "Bump version $current -> $new and cut release tag $tag.

Pushing $tag triggers the signed GitHub Release, the multi-arch container
image (GHCR + Docker Hub), and publication to Maven Central / GitHub Packages."
git tag -a "$tag" -m "Evento Framework $new"
ok "committed and tagged $tag"

info "Pushing to origin"
git push --quiet origin "refs/heads/$RELEASE_BRANCH"
git push --quiet origin "refs/tags/$tag"
ok "pushed '$RELEASE_BRANCH' and tag $tag"

echo
ok "Release $new is on its way."
echo  "    Watch the workflows: https://github.com/EventoFramework/evento-framework/actions"
