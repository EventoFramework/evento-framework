# OpenSSF Scorecard — improvement plan

Live report: https://securityscorecards.dev/viewer/?uri=github.com/EventoFramework/evento-framework
API: https://api.securityscorecards.dev/projects/github.com/EventoFramework/evento-framework

## Current state (scan 2026-05-31): **6.9 / 10** (was 5.4 before this work)

| Check | Score | Weight | Status / next action |
|---|---|---|---|
| Dangerous-Workflow | 10 | Critical | ✅ hold |
| Token-Permissions | 10 | High | ✅ fixed this round |
| Dependency-Update-Tool | 10 | High | ✅ hold |
| Maintained | 10 | High | ✅ hold |
| SAST | 10 | Medium | ✅ hold |
| Security-Policy | 10 | Medium | ✅ hold |
| Packaging | 10 | Medium | ✅ hold |
| License | 9 | Low | ⏸️ maintainer chose to leave (custom dual-license) |
| Signed-Releases | 8 | High | ◑ improves to ~10 once a real `v*` tag runs `release.yml` (provenance) |
| Binary-Artifacts | 8 | High | ⏸️ `gradle-wrapper.jar` unavoidable; `graphvizlib.wasm` optional |
| Pinned-Dependencies | 8 | Medium | ▶ **P1** — one unpinned `pip install` left |
| Branch-Protection | -1 | High | ▶ **P0** — enabled, but Scorecard needs a PAT to read it |
| CI-Tests | -1 | Low | ▶ **P2** — resolves once changes merge via PR |
| Code-Review | 0 | High | ▶ **P2** — needs approved+merged PRs |
| CII-Best-Practices | 0 | Low | ▶ **P3** — register the badge (manual) |
| Vulnerabilities | 0 | High | ⏸️ deferred — GUI major upgrades (separate project) |
| Fuzzing | 0 | Medium | ⏸️ deferred — would need a CBOR/framing harness |
| Contributors | 0 | Low | ❌ structural (solo project) — ignore |

`-1` = check errored/inconclusive and is excluded from the average. Turning
Branch-Protection from `-1` into a real score is the single biggest lever left,
because it re-adds a **High**-weight check to the aggregate.

---

## P0 — Branch-Protection: give Scorecard a token to read the rules
**Why:** Rules are live (PR+1 approval, required checks, no force-push/delete),
but `scorecard.yml` runs with the default `GITHUB_TOKEN`, which cannot read
branch-protection settings → `-1 internal error`. Documented fix: a PAT.

**Steps (maintainer creates the token; I wire the workflow):**
1. Create a **classic PAT** with scopes `repo` + `read:org` (or a fine-grained
   token with **Administration: read** + **Contents: read** on this repo).
2. `gh secret set SCORECARD_TOKEN --repo EventoFramework/evento-framework < token.txt`
3. In `scorecard.yml`, pass it to the action:
   ```yaml
   - uses: ossf/scorecard-action@<pinned-sha>
     with:
       results_file: results.sarif
       results_format: sarif
       repo_token: ${{ secrets.SCORECARD_TOKEN }}
       publish_results: true
   ```
**Expected:** Branch-Protection ~7–9 (our config: required PR+approval, required
status checks, no force-push/delete; admins-bypass and no required signatures
cap it below 10). Overall → low-to-mid 7s.

## P1 — Pinned-Dependencies 8 → 10 (I can do now, zero risk)
**Why:** Only remaining unpinned dependency is `pip install requests` in
`maven-build-and-push-repository.yaml`.
**Fix:** Rewrite `publish.py` to use the stdlib (`urllib.request`) instead of
`requests`, and delete the `Set up Python deps` / `pip install` step. Removes the
dependency entirely — nothing left to pin.
**Expected:** Pinned-Dependencies → 10.

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
