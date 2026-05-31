# OpenSSF Scorecard — improvement plan

Live report: https://securityscorecards.dev/viewer/?uri=github.com/EventoFramework/evento-framework
API: https://api.securityscorecards.dev/projects/github.com/EventoFramework/evento-framework

## Current state (scan 2026-05-31 10:20 UTC): **6.9 / 10** (was 5.4 before this work)

P0+P1 merged (PR #99) and re-scanned. Overall stayed 6.9 because Branch-Protection
flipped from an *excluded* `-1` to a **counted** High-weight `5`, which offsets the
CI-Tests gain — the aggregate is now honest, measuring checks it couldn't before.

| Check | Score | Weight | Status / next action |
|---|---|---|---|
| Dangerous-Workflow | 10 | Critical | ✅ hold |
| Token-Permissions | 10 | High | ✅ hold |
| Dependency-Update-Tool | 10 | High | ✅ hold |
| Maintained | 10 | High | ✅ hold |
| SAST | 10 | Medium | ✅ hold |
| Security-Policy | 10 | Medium | ✅ hold |
| Packaging | 10 | Medium | ✅ hold |
| CI-Tests | 10 | Low | ✅ **P2 done** — "1/1 merged PRs checked by CI" (was -1) |
| License | 9 | Low | ⏸️ maintainer chose to leave (custom dual-license) |
| Pinned-Dependencies | 9 | Medium | ◑ **P1 partial** — pip dep gone (8→9); 2 unpinned npm left: `npm install -g @ionic/cli@7.2.1` in `release.yml:61,141`. → 10 needs ionic CLI as a lockfile devDep (see P1b) |
| Signed-Releases | 8 | High | ◑ improves once a real `v*` tag runs `release.yml` (provenance) |
| Binary-Artifacts | 8 | High | ⏸️ `gradle-wrapper.jar` unavoidable; `graphvizlib.wasm` optional |
| Branch-Protection | 5 | High | ◑ **P0 done (now counted)** — "not maximal"; →higher needs enforce-admins / signed-commits, which conflicts with solo admin-bypass |
| Code-Review | 0 | High | ⏸️ **P2 capped** — "0/28 approved changesets"; admin-bypass merges have no approval. Needs a 2nd reviewer/account |
| CII-Best-Practices | 0 | Low | ▶ **P3** — register the badge (manual) |
| Vulnerabilities | 0 | High | ⏸️ deferred — GUI major upgrades (separate project) |
| Fuzzing | 0 | Medium | ⏸️ deferred — would need a CBOR/framing harness |
| Contributors | 0 | Low | ❌ structural (solo project) — ignore |

`-1` = check errored/inconclusive and is excluded from the average. Both former
`-1`s (Branch-Protection, CI-Tests) are now real scores.

## P1b — Pinned-Dependencies 9 → 10 (optional, moderate)
The 2 remaining unpinned deps are `npm install -g @ionic/cli@7.2.1` (global, not
lockfile-backed) at `release.yml:61` and `:141`. To eliminate: add `@ionic/cli`
to `evento-gui` devDependencies, regenerate `package-lock.json`, drop both global
install steps, and invoke the build via `npx ionic build --prod`. Requires a local
npm lockfile regen + a GUI build verification. Reward: +1 on a Medium check
(≈ +0.07 overall). **Skipped by maintainer (2026-05-31)** — marginal reward for the
lockfile + build-verification cost.

---

## P0 — Branch-Protection: give Scorecard a token to read the rules
**Why:** Rules are live (PR+1 approval, required checks, no force-push/delete),
but `scorecard.yml` runs with the default `GITHUB_TOKEN`, which cannot read
branch-protection settings → `-1 internal error`. Documented fix: a PAT.

**Workflow wired (2026-05-31):** `scorecard.yml` now passes
`repo_token: ${{ secrets.SCORECARD_TOKEN }}` to the action. **Maintainer TODO
before the next scan:**
1. Create a **classic PAT** with scopes `repo` + `read:org` (or a fine-grained
   token with **Administration: read** + **Contents: read** on this repo).
2. `gh secret set SCORECARD_TOKEN --repo EventoFramework/evento-framework < token.txt`

⚠️ Set the secret **before merge / before the next Tuesday scan** — an empty
`SCORECARD_TOKEN` would make the action run with a blank token and fail.

**Expected:** Branch-Protection ~7–9 (our config: required PR+approval, required
status checks, no force-push/delete; admins-bypass and no required signatures
cap it below 10). Overall → low-to-mid 7s.

## P1 — Pinned-Dependencies 8 → 10 ✅ DONE (2026-05-31)
**Why:** Only remaining unpinned dependency was `pip install requests` in
`maven-build-and-push-repository.yaml`.
**Fix shipped:** Rewrote `publish.py` to use the stdlib (`urllib.request` +
`base64` Basic auth) instead of `requests`; removed the `Set up Python` and
`Install Python dependencies` (`pip install requests`) steps from the workflow;
promote step now invokes `python3 publish.py` (no `setup-python`, runner's
pre-installed interpreter). Nothing left to pin in that workflow.
**Expected:** Pinned-Dependencies → 10 on the next scan.

## P2 — Code-Review + CI-Tests (behavioral)
**Why:** 0 approved changesets / no PR found — everything lands via direct push
(admin bypass). Dependabot PRs were closed, not merged.
**Options:**
- **a.** Route changes through PRs and **merge** them (don't close) — even
  Dependabot ones. CI-Tests (Low) recovers immediately once merged PRs exist.
- **b.** For Code-Review (High) Scorecard wants *approved* changesets. As a solo
  maintainer, either add a second reviewer/account, or accept this stays low.
**Expected:** CI-Tests → ~10 with merged PRs; Code-Review only rises with
approvals. Net: depends on review capacity.

**Progress (2026-05-31):** PR #99 (P0+P1) merged to `main` with green CI
(`build-and-test`, `jdbc-integration-tests`, `Analyze Java`, CodeQL). The merge
was an **admin bypass** (no approval — solo maintainer), so it should credit
**CI-Tests** (merged PR + passing checks) but **not Code-Review** (needs an
*approved* changeset). Code-Review stays low until a second reviewer/account
approves PRs.

## P3 — CII-Best-Practices badge (manual, low weight)
Register at https://www.bestpractices.dev and complete the questionnaire. Most
answers are already true (SECURITY.md, CI, SAST/CodeQL, signed releases,
dependency updates). Earns the "passing" badge → check 0 → ~5+.

## Deferred / accepted (explicit decisions)
- **Vulnerabilities (0, ~109):** all GUI transitive; `npm audit fix` fixes 0,
  all require breaking majors (Angular 18→21, mermaid, Capacitor). Separate
  project. Dependabot keeps surfacing them.
- **License (9):** custom dual-license kept by choice. To reach 10: add canonical
  AGPL-3.0 text as `COPYING` alongside the existing files.
- **Binary-Artifacts (8):** `gradle-wrapper.jar` required (validated in CI);
  could reach 10 by fetching `graphvizlib.wasm` at build time instead of
  committing it.
- **Fuzzing (0):** a Jazzer harness on `Message` deserialization/framing would
  both harden and score, but is real work.
- **Contributors (0):** needs ≥2 orgs — not achievable for a solo project.

## Realistic target
P0 + P1 (+ CII): **~7.5–8**. Code-Review and Vulnerabilities are the remaining
high-weight zeros; both need a sustained effort (reviewers / GUI upgrade) rather
than a config change.

## Re-check after changes
Scorecard re-scans weekly (cron `31 4 * * 2`, Tuesdays) or on push to the
scorecard workflow. Re-read the API/viewer link above after the next run.
