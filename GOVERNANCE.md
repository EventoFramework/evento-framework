# Governance

Project home: [www.eventoframework.com](https://www.eventoframework.com/)

Evento Framework is maintained as a maintainer-led open source project.

## Roles

| Role | Responsibilities |
|---|---|
| Maintainer | Review and merge PRs, publish releases, manage security reports, set technical direction |
| Contributor | Propose issues/PRs, follow project standards, maintain tests and documentation for changes |
| Security contact | Triage private vulnerability reports and coordinate disclosure |

## Decision Making

- Maintainers make final decisions on roadmap, architecture, releases, and compatibility.
- Significant architectural changes should be discussed in an issue before implementation.
- Security-sensitive changes require maintainer review before merge.
- Backward-incompatible changes require release-note coverage in `CHANGELOG.md`.

## Pull Request Requirements

- PRs target `main` unless maintainers request otherwise.
- PR titles follow Conventional Commits.
- At least one maintainer approval is required before merge.
- Tests must pass for affected modules.
- Public APIs, configuration, wire protocol, and migration changes require documentation updates.

## Release Responsibilities

Maintainers are responsible for:

- Updating `CHANGELOG.md`
- Verifying CI and release artifacts
- Tagging releases using Semantic Versioning
- Publishing Maven/Docker artifacts when applicable
- Communicating breaking changes and security advisories
