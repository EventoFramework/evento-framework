
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
| Binary-Artifacts | 8 | High | ◑ **improved (2026-05-31)** — deleted the unused `graphvizlib.wasm` (1 MB) + its 3 dead deps; only `gradle-wrapper.jar` remains (unavoidable, validated in CI) |
| Branch-Protection | 5 | High | ◑ **P0 done (now counted)** — "not maximal"; →higher needs enforce-admins / signed-commits, which conflicts with solo admin-bypass |
| Code-Review | 0 | High | ⏸️ **P2 capped** — "0/28 approved changesets"; admin-bypass merges have no approval. Needs a 2nd reviewer/account |
| CII-Best-Practices | 0 | Low | ▶ **P3** — register the badge (manual) |
| Vulnerabilities | 0 | High | ◑ **in progress** — GUI majors done (2026-05-31): `npm audit` 97 → 5, all 5 unfixable (4 build-only dev-server transitives of latest Angular toolchain + abandoned `mxgraph`). See "GUI dependency upgrade" below |
| Fuzzing | 0 | Medium | ◑ **done (2026-05-31)** — Jazzer `@FuzzTest` on the CBOR codec + weekly `fuzz.yml`. Scorecard detects the `com.code_intelligence.jazzer` import → expect 10. See "Fuzzing harness" below |
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

## GUI dependency upgrade (2026-05-31) — `npm audit` 97 → 5
Branch `chore/dependency-upgrades`. Upgraded `evento-gui` across the breaking
majors that the Vulnerabilities check flagged. Production build
(`ng build --configuration production`) is green after every step.

- **Angular 18 → 21** via three sequential `ng update` runs (18→19→20→21). Ran
  the standalone + control-flow + bootstrap migrations the schematics offered.
- **ngx-markdown 18 → 21** + **marked 12 → 16/18** (marked 16 made
  `MarkedOptions`/`MarkedExtension` generic, which ngx-markdown 20+ requires).
- **ng-apexcharts 1.11 → 2.4** + **apexcharts 3 → 5** (peer needs Angular ≥20).
- **@ionic/angular 8.3 → 8.8.8** (older Stencil-generated `.d.ts` clashed with
  TS 5.9). Added `"skipLibCheck": true` to `tsconfig.json` (Angular CLI default
  for new projects) to skip type-checking third-party declaration files.
- **@ngx-translate/core + http-loader 15/8 → 17** — migrated `app.module.ts` to
  the v17 loader API (`provideTranslateHttpLoader({prefix, suffix})`).
- **Capacitor 6 → 8**, **ionicons 7 → 8**, **jwt-decode 3 → 4**, **mermaid 9 →
  11**, **jsdom 20 → 29** (jwt-decode/Capacitor are not imported in `src/`).
- **ESLint 8 → 9** + **typescript-eslint 6 → 8** + **angular-eslint 21** +
  **eslint-plugin-jsdoc 48 → 63**. Migrated `.eslintrc.json` → flat
  `eslint.config.js`. (`ng lint` still reports the same pre-existing rule
  violations — `prefer-inject`/`prefer-standalone` — those are code-quality
  findings, not part of this dependency work.)
- Held back intentionally: **typescript** at `~5.9` (Angular 21 peer ceiling,
  not 6.x) and **zone.js** at `~0.15` (Angular 21 peer).
- `npm audit fix` (non-force) cleared the rest of the transitive dev-chain.

**5 remaining (all `fixAvailable: false`):** `mxgraph` (abandoned runtime lib,
XSS, no patched version in any release — heavily used by the diagram components,
replacing it is a separate project) and `@angular-devkit/build-angular` →
`webpack-dev-server` → `sockjs` → `uuid` (dev-server-only, not shipped; already
on the latest Angular toolchain).

## Fuzzing harness (2026-05-31) — Jazzer on the CBOR codec
The CBOR `Codec.decode(byte[])` is the most exposed parser in the framework:
every byte off the TCP socket hits it *before* auth or any type check. Its
contract — "for any input, return an allow-listed `Message` or throw
`CodecException`, never an undeclared throwable" — is an ideal fuzzing property.

- **`evento-transport-api`** test `CborCodecFuzzTest` — two Jazzer `@FuzzTest`
  targets (`decode(byte[])` and the offset/length window variant). Dep:
  `com.code-intelligence:jazzer-junit` (`jazzerVersion` in root `build.gradle`).
- **Dual mode:** the per-PR `CI` job runs `:evento-transport-api:test` with no
  env var → Jazzer **regression mode** (replays the seed corpus as ordinary
  JUnit, ~instant, deterministic). `fuzz.yml` (weekly cron + manual dispatch)
  sets `JAZZER_FUZZ=1` → real libFuzzer mutation, `maxDuration=120s`/target,
  uploads any `crash-*` reproducer.
- **Validated on JDK 25:** Jazzer 0.24.0 instruments and fuzzes fine — a local
  run did **2.77 M execs in 16 s (~160 k/s), zero findings**, i.e. the codec
  is robust against arbitrary input. Regression run is green in the full suite.
- **Scorecard:** the Fuzzing check's Jazzer detector greps `.java` for the
  `com.code_intelligence.jazzer` import — present now → expect the check to
  flip 0 → 10 on the next scan (Medium weight, ≈ +0.5 overall).
- **Future:** extend with a target on the chunk-frame parser
  (`ChunkReassembler`, the `0x00 FULL` / `0x01 + UUID + isLast` header) in
  `evento-transport-netty`, and/or apply for OSS-Fuzz for continuous runs.

## Deferred / accepted (explicit decisions)
- **Vulnerabilities (0):** GUI npm majors upgraded — see above. Down to 5
  unfixable moderates (4 build-only + abandoned `mxgraph`). Dependabot will keep
  surfacing `mxgraph` until it's replaced.
- **License (9):** custom dual-license kept by choice. To reach 10: add canonical
  AGPL-3.0 text as `COPYING` alongside the existing files.
- **Binary-Artifacts (8 → improving):** `src/assets/wasm/graphvizlib.wasm` was
  dead weight — nothing in the GUI imported `graphviz-wasm` / `graphviz-builder`
  / `vizjs`, and the wasm was only copied by the `src/assets` glob, never loaded.
  Deleted the binary and the 3 dead deps (2026-05-31). Only `gradle-wrapper.jar`
  remains, which is required and validated in CI — so this check is now at its
  practical ceiling.
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
