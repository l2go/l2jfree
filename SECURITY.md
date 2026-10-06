# Security policy

## Supported versions

| Version | Supported |
|---|---|
| Platform 3.0 (in development) | Yes, once released as `v3.0.0` |
| 2.x line | No. `v2.5.0` is the final release and receives no fixes ([decision](docs/adr/0008-retire-the-2x-line.md)) |

## Report a vulnerability

Do not open a public issue. Use a [private security advisory](https://github.com/l2go/l2jfree/security/advisories/new). Include the affected component, the impact on a running server, and the steps to reproduce.

Credentials and session keys must never appear in a report. Redact account names, addresses, and file paths.

## What to expect

- Acknowledgement of the report.
- An assessment of the impact and a fix or a stated decision.
- Credit in the release notes if you want it.

This is a noncommercial archive project with one maintainer, so there is no guaranteed response time.

## Supply chain

Releases publish checksums, a CycloneDX SBOM, a vulnerability scan report, and signed provenance. See the [verification guide](docs/RELEASE-VERIFICATION.md) for the 2.x releases. The same checks are planned for the 3.0 image ([roadmap](docs/roadmap.md)).
