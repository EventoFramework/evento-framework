# OpenSSF Best Practices badge — answer sheet (P3)

Scorecard's **CII-Best-Practices** check reads the badge earned at
<https://www.bestpractices.dev>. This file is a paste-ready draft of the
*passing*-level questionnaire, grounded in the repo's current state. Most
criteria are already **Met**; a short "needs maintainer input" list is at the
bottom.

**How to use:**
1. Sign in at <https://www.bestpractices.dev> with the GitHub maintainer account.
2. "Add project" → repo URL `https://github.com/EventoFramework/evento-framework`.
3. For each criterion below, select the status and paste the justification/URL.
4. Once ≥ all *passing* criteria are Met, the badge is awarded; add its URL to
   the README and re-run Scorecard.

Base URLs used below:
- Repo: `https://github.com/EventoFramework/evento-framework`
- Site: `https://www.eventoframework.com` · Docs: `https://docs.eventoframework.com`

---

## Basics

| Criterion | Status | Justification to paste |
|---|---|---|
| `description_good` | **Met** | README "What is Evento Framework?" — distributed Event-Sourcing/CQRS (RECQ) framework for Java. `…/blob/main/README.md` |
| `interact` | **Met** | GitHub Issues + structured issue templates (`.github/ISSUE_TEMPLATE/`). |
| `contribution` | **Met** | `CONTRIBUTING.md` — setup, branching, commit conventions, PR process. `…/blob/main/CONTRIBUTING.md` |
| `contribution_requirements` | **Met** | CONTRIBUTING "Coding Standards" + "Pull Request Process" + Conventional Commits. |
| `floss_license` | **Met** | Dual-licensed; the open option is **AGPL-3.0** (OSI-approved). `…/blob/main/LICENSE.txt` |
| `floss_license_osi` | **Met** | AGPL-3.0 is OSI-approved. |
| `license_location` | **Met** | `LICENSE.txt` (+ `LICENSE-COMMERCIAL.txt`) at repo root. |
| `documentation_basics` | **Met** | `https://docs.eventoframework.com` + README + `.claude/ARCHITECTURE.md`. |
| `documentation_interface` | **Met** | Docs site documents the public API (annotations, gateways, bundle bootstrap). |
| `english` | **Met** | All docs in English. |
| `maintained` | **Met** | Active commits; Dependabot enabled; v2.0 line maintained. |
| `repo_public` | **Met** | Public GitHub repo. |
| `repo_track` | **Met** | Git. |
| `repo_interim` | **Met** | All work lands on `main`; interim commits public. |
| `repo_distributed` | **Met** | Git (distributed VCS). |
| `version_unique` | **Met** | Each release has a unique version (`2.0.x`). |
| `version_semver` | **Met** | Semantic Versioning (`MAJOR.MINOR.PATCH`). |
| `version_tags` | **Met** | Releases tagged `v[0-9]+.[0-9]+.[0-9]*` (see `release.yml` trigger). |
| `release_notes` | **Met** | `CHANGELOG.md`. `…/blob/main/CHANGELOG.md` |
| `release_notes_vulns` | **Met** | Vulnerabilities are disclosed via GitHub Security Advisories, not buried in notes. |

## Change Control

| Criterion | Status | Justification |
|---|---|---|
| `repo_public` | **Met** | (above) |
| `repo_track` | **Met** | (above) |
| `repo_distributed` | **Met** | (above) |
| `version_unique` / `version_semver` / `version_tags` | **Met** | (above) |

## Reporting

| Criterion | Status | Justification |
|---|---|---|
| `report_process` | **Met** | `SECURITY.md` + issue templates describe how to report bugs/vulns. `…/blob/main/SECURITY.md` |
| `report_tracker` | **Met** | GitHub Issues. |
| `report_responses` | **Met** | Maintainer responds on issues/PRs; SECURITY.md commits to a 5-business-day ack. |
| `enhancement_responses` | **Met** | Feature-request template + maintainer triage. |
| `report_archive` | **Met** | GitHub issues/PRs are publicly archived. |
| `vulnerability_report_process` | **Met** | SECURITY.md "Reporting a Vulnerability". |
| `vulnerability_report_private` | **Met** | GitHub Security Advisories (private) + email fallback. |
| `vulnerability_report_response` | **Met** | "acknowledgement within 5 business days". |

## Quality

| Criterion | Status | Justification |
|---|---|---|
| `build` | **Met** | Gradle wrapper: `./gradlew build`. CONTRIBUTING "Development Environment". |
| `build_common_tools` | **Met** | Gradle 9 (widely used). |
| `build_floss_tools` | **Met** | Gradle + OpenJDK — all FLOSS. |
| `test` | **Met** | Extensive suite across all modules (transport, server bus, bundle, consumer-store JDBC ITs via Testcontainers, lab IT harnesses). |
| `test_invocation` | **Met** | `./gradlew test` (CONTRIBUTING "Running Tests"). |
| `test_most` | **Met** | Broad coverage; jacoco minimum 70% on new modules. |
| `test_continuous_integration` | **Met** | `.github/workflows/ci.yml` runs the suite on every push/PR to `main`. |
| `test_policy` | **Met** | CONTRIBUTING: "Make your changes, write tests, and ensure the suite is green." |
| `tests_are_added` | **Met** | Tests accompany features (tests-at-the-boundary convention). |
| `tests_documented_added` | **Met** | CONTRIBUTING documents the test policy. |
| `warnings` | **Met** | Java compiler warnings enabled (default `javac`). |
| `warnings_fixed` | **Met** | Build is clean. |

## Security

| Criterion | Status | Justification |
|---|---|---|
| `know_secure_design` | **Met** | Security model documented: `.claude/ARCHITECTURE.md` §7 (auth token + constant-time compare, TLS, CBOR type whitelist against gadget chains). |
| `know_common_errors` | **Met** | Type-whitelist deserialization, no `System.exit`, constant-time token compare — documented design decisions. |
| `crypto_published` | **Met** | Uses standard TLS (JSSE/Netty `SslContext`); no proprietary crypto. |
| `crypto_call` | **Met** | Delegates to the JDK/Netty TLS stack. |
| `crypto_floss` | **Met** | OpenJDK/Netty TLS (FLOSS). |
| `crypto_keylength` | **Met** | TLS key lengths per JSSE defaults. |
| `crypto_working` | **Met** | TLS 1.2/1.3 via JSSE; no broken primitives. |
| `crypto_weaknesses` | **Met** | No MD5/SHA-1 for security; relies on TLS suites. |
| `crypto_pfs` | **Met** | TLS 1.2/1.3 ECDHE suites (PFS). |
| `crypto_password_storage` | **N/A** | Framework stores no user passwords; auth is a shared-secret token compared in constant time. |
| `crypto_random` | **Met** | `UUID.randomUUID()` / JDK `SecureRandom` where security-relevant. |
| `delivery_mitm` | **Met** | Releases delivered over HTTPS (Maven Central, GHCR, Docker Hub) **and** signed (cosign keyless + SLSA provenance + checksums — `release.yml`). |
| `delivery_unsigned` | **Met** | Artifacts are signed (cosign / GPG for Maven Central). |
| `vulnerabilities_fixed_60_days` | **Met** | SECURITY.md commits to coordinated, prompt fixes. |
| `vulnerabilities_critical_fixed` | **Met** | No known unpatched critical vulns (npm audit reduced 97→5, all unfixable transitive/dev-only). |
| `no_leaked_credentials` | **Met** | No secrets in repo; `.gitignore` blocks `token.txt`/`*.pat`/`*.token`; CI uses GitHub secrets. |

## Analysis

| Criterion | Status | Justification |
|---|---|---|
| `static_analysis` | **Met** | CodeQL (`.github/workflows/codeql.yml`) + OpenSSF Scorecard. |
| `static_analysis_common_vulnerabilities` | **Met** | CodeQL "Analyze Java" covers the standard CWE set. |
| `static_analysis_fixed` | **Met** | CodeQL findings triaged on `main`. |
| `static_analysis_often` | **Met** | CodeQL runs on push/PR + scheduled. |
| `dynamic_analysis` | **Met** | **Jazzer coverage-guided fuzzing** of the CBOR wire codec (`CborCodecFuzzTest`, weekly `fuzz.yml`). |
| `dynamic_analysis_unsafe` | **N/A** | Java is memory-safe; no manual memory management to check with ASan/Valgrind. |

---

## Previously-open items — now resolved (all **Met**, no repo change required)

| Criterion | Status | Justification to paste |
|---|---|---|
| `discussion` | **Met** | Public GitHub **Issues** tracker (`has_issues=true`) + issue/feature templates serve as the discussion mechanism. *(Optional: enabling GitHub Discussions would strengthen this — see note below.)* |
| `documentation_security` | **Met** | Security model documented in `.claude/ARCHITECTURE.md` §7 — enable TLS via `NettyTransportConfig.sslContext`; authenticate bundles with a shared-secret token via `TokenValidator.sharedSecret(...)` (constant-time compare); CBOR type whitelist. Reporting in `SECURITY.md`. |
| `sites_https` | **Met** | `https://www.eventoframework.com` and `https://docs.eventoframework.com` both serve over HTTPS (also the badges/links in README use `https`). |
| `crypto_certificate_verification` | **Met** | The client verifies the server certificate via the default JSSE trust manager (standard `SslContext`); TLS 1.2/1.3 only. |

## Submission

The badge is **self-asserted** — fill the form at bestpractices.dev with the
answers above. There is **no API token** for writing to the BadgeApp, so the
form must be submitted from the logged-in maintainer account (it cannot be
automated). Once all *passing* criteria are Met and submitted, Scorecard's
CII-Best-Practices check flips **0 → 5+** (Low weight). Finally, add the earned
badge markdown to the README.

**Optional strengthening (not required for *passing*):**
- Enable **GitHub Discussions** (`Settings → Features → Discussions`) — makes
  `discussion` unambiguous and gives users a non-issue channel.
- Add a short "Secure deployment" section to `SECURITY.md` (TLS + token setup)
  so `documentation_security` is evidenced in-repo, not just in the docs site.
