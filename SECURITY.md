# Security Policy

Project home: [www.eventoframework.com](https://www.eventoframework.com/)

## Supported Versions

Security fixes are provided for the currently maintained major release line.

| Version | Supported |
|---|---|
| `2.0.x` | Yes |
| `1.x` | No |

## Reporting a Vulnerability

Please do **not** report security vulnerabilities through public GitHub issues.

**Preferred channel:** Use [GitHub Security Advisories](https://github.com/EventoFramework/evento-framework/security/advisories/new)
to report privately — this keeps the report confidential and lets maintainers coordinate a fix before public disclosure.

**Email fallback:** [gabor.galazzo@gmail.com](mailto:gabor.galazzo@gmail.com) with:

- Affected module and version or commit SHA
- A clear description of the vulnerability and impact
- Reproduction steps or proof-of-concept details when safe to share
- Any known mitigations or workarounds

You should receive an acknowledgement within 5 business days. Maintainers will coordinate next steps,
including validation, fix development, release timing, and disclosure.

## Disclosure Process

1. The report is acknowledged and triaged privately.
2. Maintainers validate impact and affected versions.
3. A fix is prepared and reviewed in a private branch when necessary.
4. A patched release is published.
5. A GitHub Security Advisory is published after users have a reasonable upgrade path.

## Security Expectations for Contributors

- Do not commit credentials, tokens, signing keys, or private certificates.
- Use `gradle.properties.template` for local publishing configuration and keep real credentials out
  of source control.
- Treat wire-protocol, authentication, TLS, and event-store changes as security-sensitive.
- Add regression tests for vulnerabilities and authentication/authorization fixes.
