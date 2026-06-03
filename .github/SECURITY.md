# Security Policy

Danmaku is early-stage software. The current LAN library server is designed for
trusted local networks only. It is not an internet-facing remote-access server.

## Reporting A Vulnerability

Please do not open a public issue for a vulnerability report. Instead, contact
the repository owner privately through GitHub.

Include:

- A short description of the issue.
- Steps to reproduce.
- Affected platform or module.
- Whether credentials, pairing tokens, signed URLs, or local files may be
  exposed.

## Security Boundaries

- Pairing codes are required for current LAN catalog, media, and progress
  endpoints, but this does not replace a hardened remote-access design.
- Do not expose the LAN server directly to the public internet.
- Do not log pairing tokens, credentials, cookies, signed URLs, or provider
  secrets.
- Do not implement DRM circumvention.
- Only authorized media sources are in scope.
- Public remote access requires a separate threat model, authentication design,
  and transport-security plan.
