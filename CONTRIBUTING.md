# Contributing to Evento Framework

Thank you for your interest in contributing! This document covers environment setup, branching
strategy, commit conventions, and the pull request process.

---

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [Development Environment](#development-environment)
- [Running Tests](#running-tests)
- [Branching Strategy](#branching-strategy)
- [Commit Conventions](#commit-conventions)
- [Pull Request Process](#pull-request-process)
- [Coding Standards](#coding-standards)
- [Security and Responsible Disclosure](#security-and-responsible-disclosure)
- [Licensing](#licensing)

---

## Code of Conduct

This project follows the [Contributor Covenant Code of Conduct](CODE_OF_CONDUCT.md). By
participating, you are expected to uphold this code. Please report unacceptable behaviour to
[gabor.galazzo@gmail.com](mailto:gabor.galazzo@gmail.com).

---

## Getting Started

Before contributing, familiarise yourself with the project:

- **Website:** [www.eventoframework.com](https://www.eventoframework.com/)
- **Documentation:** [docs.eventoframework.com](https://docs.eventoframework.com/)
- **Architecture notes:** [`.claude/ARCHITECTURE.md`](.claude/ARCHITECTURE.md) — authoritative module map and design decisions

1. **Fork** the repository and clone your fork.
2. **Create a branch** from `main` for your change (see [Branching Strategy](#branching-strategy)).
3. Make your changes, write tests, and ensure the suite is green.
4. Open a **Pull Request** against `main`.

---

## Development Environment

| Requirement | Version |
|---|---|
| JDK | **25** (LTS) |
| Gradle | 9.0 (use the wrapper — `./gradlew`) |
| Spring Boot | 3.5.5 (managed by root `build.gradle`) |
| Docker Desktop | Latest (only required for JDBC integration tests) |

**macOS — set JAVA_HOME:**
```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
```

**Build (skip tests):**
```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) ./gradlew clean build -x test
```

**Local publishing credentials** — copy `gradle.properties.template` to `gradle.properties`
(gitignored) and fill in your local credentials:
```bash
cp gradle.properties.template gradle.properties
# Edit gradle.properties with your Maven Central + signing credentials
```

---

## Running Tests

**Core suite** (no Docker required):
```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) ./gradlew \
  :evento-transport-api:test \
  :evento-transport-netty:test \
  :evento-server:test --tests 'com.evento.server.bus.*' \
  :evento-common:test \
  :evento-bundle:test \
  :evento-lab:test \
  :evento-lab-microservices:evento-lab-ms-it:test
```

**JDBC integration tests** (requires Docker Desktop with Postgres + MySQL via Testcontainers):
```bash
EVENTO_RUN_JDBC_IT=true JAVA_HOME=$(/usr/libexec/java_home -v 25) \
  ./gradlew :evento-consumer-state-store:evento-consumer-state-store-jdbc:test
```

All tests must pass before a PR is eligible for review. New features must include integration
tests. New bug fixes must include a regression test.

---

## Branching Strategy

| Branch | Purpose |
|---|---|
| `main` | Stable; protected. Direct pushes blocked. |
| `feat/<short-description>` | New features |
| `fix/<short-description>` | Bug fixes |
| `chore/<short-description>` | Tooling, deps, refactors |
| `docs/<short-description>` | Documentation only |

Branch from `main`, rebase on `main` before opening a PR.

---

## Commit Conventions

This project follows [Conventional Commits](https://www.conventionalcommits.org/). Every commit
message must match:

```
<type>(<scope>): <short summary in present tense>

<body — explain WHY, not what. Include context, trade-offs, constraints.>
```

**Types:** `feat`, `fix`, `chore`, `docs`, `test`, `refactor`, `perf`

**Scopes** (use the module name): `transport-api`, `transport-netty`, `server`, `bundle`,
`common`, `consumer-state-store`, `lab`, `build`, `ci`

**Examples:**
```
feat(server): add TokenValidator SPI for per-handshake auth

fix(bundle): DEGRADED channel drops in-flight responses

chore(build): bump Spring Boot to 3.5.5
```

Commit bodies explain the *why* and non-obvious constraints. `git show <hash>` should be
self-contained enough for anyone to understand the motivation without external context.

---

## Pull Request Process

1. Ensure all tests pass locally.
2. Fill in the PR template completely (summary + test plan).
3. Link any related issues in the PR description.
4. At least **one approval** from a maintainer is required before merge.
5. PRs are merged with **squash-and-merge** to keep `main` history linear.
6. Update `CHANGELOG.md` with your changes under `[Unreleased]`.

**PR title** must follow the same Conventional Commits format as individual commit messages.

---

## Coding Standards

- **Java 25** — use records, sealed interfaces, pattern matching, and virtual threads where
  appropriate. No preview features in production code.
- **No `System.exit`** — failures surface through futures, exceptions, or callbacks.
- **Constructors do no work** — thread starts and I/O go in explicit `start()` methods.
- **OCP via dispatcher maps** — `Map<Class<? extends Message>, Handler>` not switch chains.
- **Tests at the boundary** — integration tests use real TCP (`NettyServerTransport` +
  `BundleClient`). Mocks only for the in-memory transport test double.
- **No comments that restate the code** — comment only non-obvious invariants, constraints, or
  workarounds.
- **No extra abstractions** — solve the problem at hand; don't design for hypothetical futures.
- **Virtual threads** for business executors. Never block a Netty EventLoop.

Code style is enforced via `.editorconfig` (4-space indent for Java, LF line endings, 120-char
line length soft limit).

---

## Security and Responsible Disclosure

Do not open public issues for vulnerabilities. Follow [SECURITY.md](SECURITY.md) and include a
minimal reproduction, affected version, impact assessment, and any known mitigations.

Security-sensitive changes include authentication, TLS, token validation, wire protocol,
serialization, event-store persistence, and dependency updates with known CVEs. Call these out in
the PR description so maintainers can prioritize review.

---

## Licensing

By submitting a pull request, you agree that your contributions will be licensed under the same
[dual-licence](LICENSE.txt) as the project (AGPL-3.0 for open source; commercial licence for
proprietary use).
