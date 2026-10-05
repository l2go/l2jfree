# Release artifact verification

> **Retired.** This document describes the 2.x line. v2.5.0 is its final release ([ADR-0008](adr/0008-retire-the-2x-line.md)). See the [documentation index](index.md).

Every public release includes a SHA-256 manifest, a CycloneDX dependency SBOM,
a vulnerability scan report, and GitHub artifact attestations for the released
files. It also includes a documentation ZIP with the README, the license, and the
`docs/` directory: the modernization vision, Platform 3.0 vision, stack map,
database migration strategy, dependency inventory, logging migration plan,
release verification guide, operations runbook, and deployment notes. The
v2.5.0 documentation ZIP predates this packaging. It holds the README, the
license, and seven documents, and it omits the dependency inventory and the
deployment notes, which the runbook links to. Read those on the `v2.5.0` tag.
The archives and reports are promoted from the CI build that passed the Linux
checks; the release job does not rebuild them.
Linux and Windows packaging jobs verify the expected launcher, configuration,
schema, and datapack entries, and reject distributions that still contain the
removed ORM or connection-pool libraries.

## Verify checksums

On Linux, macOS, or another system with `sha256sum`:

```sh
sha256sum --check SHA256SUMS.txt
```

On Windows PowerShell:

```powershell
Get-Content .\SHA256SUMS.txt | ForEach-Object {
    $expected, $file = $_ -split '\s+', 2
    $actual = (Get-FileHash -Algorithm SHA256 -Path ".\$file").Hash.ToLowerInvariant()
    if ($actual -ne $expected) {
        throw "SHA-256 mismatch: $file"
    }
    Write-Host "Verified $file"
}
```

## Verify build provenance

Install a current GitHub CLI release with artifact attestation support, then
verify each downloaded archive and report against this repository. v2.5.0
publishes these files:

```sh
gh attestation verify l2jfree-login-2.5.0-dist.zip --repo l2go/l2jfree
gh attestation verify l2jfree-core-2.5.0-dist.zip --repo l2go/l2jfree
gh attestation verify l2jfree-2.5.0-docs.zip --repo l2go/l2jfree
gh attestation verify l2jfree-2.5.0-sbom.json --repo l2go/l2jfree
gh attestation verify l2jfree-2.5.0-vulnerability-report.json --repo l2go/l2jfree
gh attestation verify SHA256SUMS.txt --repo l2go/l2jfree
```

The GameServer archive contains the datapack. There is no separate datapack
zip from v2.5.0 onward. v2.0.0 still publishes `l2jfree-datapack-2.0.0-dist.zip`
in addition to the LoginServer and GameServer archives. Check the source
repository, workflow identity, and source commit shown by verification before
deployment.

A stable tag, one whose name has no hyphen, publishes only after the
`stable-release` environment is approved. A tag with a hyphen publishes as a
pre-release without that approval. The approval is the publish gate. It is
not a second build.
