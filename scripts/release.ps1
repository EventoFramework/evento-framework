# release.ps1 — cut a new Evento Framework release (Windows twin of release.sh).
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
#   powershell -ExecutionPolicy Bypass -File scripts\release.ps1 [patch|minor|major]
#
# If the release type is omitted it is asked interactively. Nothing is pushed
# until you confirm the computed version.
#
# Keep the behaviour in lockstep with scripts/release.sh — same checks, same
# messages, same single-push semantics.

[CmdletBinding()]
param(
    [ValidateSet('patch', 'minor', 'major', '1', '2', '3')]
    [string]$ReleaseType
)

$ErrorActionPreference = 'Stop'

function Die([string]$Message) {
    Write-Host "error: $Message" -ForegroundColor Red
    exit 1
}
function Info([string]$Message) { Write-Host "==> $Message" -ForegroundColor Cyan }
function Ok([string]$Message)   { Write-Host "  + $Message" -ForegroundColor Green }

# --- locate repo root so the script works from any CWD ----------------------
$RepoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $RepoRoot

$GradleFile    = 'build.gradle'
$VersionJson   = 'evento-server/version.json'
$ReleaseBranch = 'main'

# --- preflight checks -------------------------------------------------------
if (-not (Get-Command git -ErrorAction SilentlyContinue)) { Die 'git is not installed' }
if (-not (Test-Path $GradleFile))  { Die "$GradleFile not found - run from the repo or its scripts/ dir" }
if (-not (Test-Path $VersionJson)) { Die "$VersionJson not found" }

$branch = (git branch --show-current).Trim()
if ($branch -ne $ReleaseBranch) {
    Die "releases must be cut from '$ReleaseBranch' (you are on '$branch')"
}

if ((git status --porcelain | Measure-Object).Count -ne 0) {
    Die 'working tree is dirty - commit or stash changes before releasing'
}

Info "Syncing with origin/$ReleaseBranch"
git fetch --quiet origin $ReleaseBranch
if ($LASTEXITCODE -ne 0) { Die "git fetch failed" }
$localSha  = (git rev-parse '@').Trim()
$remoteSha = (git rev-parse "origin/$ReleaseBranch").Trim()
# We allow local to be AHEAD of origin: any unpushed commits ride along with the
# version-bump commit in the single push below, so the test CI fires once on
# 'main' instead of twice (once for a manual pre-push, once for the bump).
# Being BEHIND/diverged is still fatal - that needs a real pull/rebase.
if ($localSha -eq $remoteSha) {
    Ok "branch '$ReleaseBranch' is in sync with origin"
} else {
    git merge-base --is-ancestor "origin/$ReleaseBranch" HEAD
    if ($LASTEXITCODE -eq 0) {
        $ahead = (git rev-list --count "origin/$ReleaseBranch..HEAD").Trim()
        Ok "branch '$ReleaseBranch' is $ahead commit(s) ahead of origin - they will be pushed with the release"
    } else {
        Die "local '$ReleaseBranch' has diverged from origin (it is behind) - pull/rebase first"
    }
}

# --- read the current version (build.gradle is the source of truth) ---------
$gradleText = [System.IO.File]::ReadAllText((Join-Path $RepoRoot $GradleFile))
$m = [regex]::Match($gradleText, "(?m)^version '(\d+)\.(\d+)\.(\d+)'")
if (-not $m.Success) { Die "could not read a clean MAJOR.MINOR.PATCH version from $GradleFile" }
$major = [int]$m.Groups[1].Value
$minor = [int]$m.Groups[2].Value
$patch = [int]$m.Groups[3].Value
$current = "$major.$minor.$patch"
Info "Current version: $current"

# --- determine release type -------------------------------------------------
if (-not $ReleaseType) {
    Write-Host ("Release type:`n  1) patch  ({0}.{1}.{2})`n  2) minor  ({0}.{3}.0)`n  3) major  ({4}.0.0)" -f `
        $major, $minor, ($patch + 1), ($minor + 1), ($major + 1))
    $ReleaseType = Read-Host 'Choose [1/2/3 or patch/minor/major]'
}

switch ($ReleaseType) {
    { $_ -in '1', 'patch' } { $new = "$major.$minor.$($patch + 1)"; break }
    { $_ -in '2', 'minor' } { $new = "$major.$($minor + 1).0";      break }
    { $_ -in '3', 'major' } { $new = "$($major + 1).0.0";           break }
    default { Die "invalid release type: '$ReleaseType' (expected patch, minor, or major)" }
}

$tag = "v$new"
git rev-parse -q --verify "refs/tags/$tag" *> $null
if ($LASTEXITCODE -eq 0) { Die "tag $tag already exists" }

# --- confirm ----------------------------------------------------------------
Info "About to release: $current -> $new   (tag $tag)"
$pending = [int](git rev-list --count "origin/$ReleaseBranch..HEAD").Trim()
if ($pending -gt 0) {
    Write-Host "    $pending unpushed commit(s) will be pushed together with the version bump:"
    git --no-pager log --oneline "origin/$ReleaseBranch..HEAD" | ForEach-Object { Write-Host "      $_" }
}
Write-Host "    This will commit the version bump, push '$ReleaseBranch' (single push -> one test CI run),"
Write-Host "    push tag $tag, and trigger the GitHub Releases, Docker Hub/GHCR, and Maven Central workflows."
$confirm = Read-Host 'Proceed? [y/N]'
if ($confirm -notmatch '^[Yy]$') { Die 'aborted by user' }

# --- apply the bump ---------------------------------------------------------
Info "Updating version to $new"
# ReadAllText/WriteAllText keep the files' existing line endings intact
# (Get-Content/Set-Content would rewrite them).
# 1) root project version
$gradleText = $gradleText -replace "(?m)^version '$([regex]::Escape($current))'", "version '$new'"
# 2) eventoVersion ext property used by all subprojects
$gradleText = $gradleText -replace "eventoVersion = '$([regex]::Escape($current))'", "eventoVersion = '$new'"
[System.IO.File]::WriteAllText((Join-Path $RepoRoot $GradleFile), $gradleText)
# 3) version.json read by release.yml to name the GitHub Release artifact
$jsonText = [System.IO.File]::ReadAllText((Join-Path $RepoRoot $VersionJson))
$jsonText = $jsonText -replace "`"version`": `"$([regex]::Escape($current))`"", "`"version`": `"$new`""
[System.IO.File]::WriteAllText((Join-Path $RepoRoot $VersionJson), $jsonText)

# sanity: every source of truth now agrees
$gradleText = [System.IO.File]::ReadAllText((Join-Path $RepoRoot $GradleFile))
$jsonText   = [System.IO.File]::ReadAllText((Join-Path $RepoRoot $VersionJson))
if ($gradleText -notmatch "(?m)^version '$([regex]::Escape($new))'")        { Die "failed to bump root version in $GradleFile" }
if ($gradleText -notmatch "eventoVersion = '$([regex]::Escape($new))'")     { Die "failed to bump eventoVersion in $GradleFile" }
if ($jsonText   -notmatch "`"version`": `"$([regex]::Escape($new))`"")      { Die "failed to bump $VersionJson" }
Ok "build.gradle and version.json now at $new"

# --- commit, tag, push ------------------------------------------------------
Info 'Committing and tagging'
git add $GradleFile $VersionJson
$body = @"
Bump version $current -> $new and cut release tag $tag.

Pushing $tag triggers the signed GitHub Release, the multi-arch container
image (GHCR + Docker Hub), and publication to Maven Central / GitHub Packages.
"@
git commit -q -m "chore(release): $new" -m $body
if ($LASTEXITCODE -ne 0) { Die 'git commit failed' }
git tag -a $tag -m "Evento Framework $new"
if ($LASTEXITCODE -ne 0) { Die 'git tag failed' }
Ok "committed and tagged $tag"

Info 'Pushing to origin'
git push --quiet origin "refs/heads/$ReleaseBranch"
if ($LASTEXITCODE -ne 0) { Die "failed to push '$ReleaseBranch'" }
git push --quiet origin "refs/tags/$tag"
if ($LASTEXITCODE -ne 0) { Die "failed to push tag $tag" }
Ok "pushed '$ReleaseBranch' and tag $tag"

Write-Host ''
Ok "Release $new is on its way."
Write-Host '    Watch the workflows: https://github.com/EventoFramework/evento-framework/actions'
