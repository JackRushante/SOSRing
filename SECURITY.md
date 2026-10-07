# Security Policy

## Supported versions

Only the latest release of SOS Ring receives security fixes. Please update to the most recent version from [GitHub Releases](https://github.com/JackRushante/SOSRing/releases) or F-Droid before reporting.

## Reporting a vulnerability

Please do not open public issues for security problems. Report them privately through one of these channels:

- GitHub private vulnerability reporting: [Report a vulnerability](https://github.com/JackRushante/SOSRing/security/advisories/new)
- Email: Marcì Lorenzo <marci.lorenzo@gmail.com>

Include the affected version, the device and Android version, the steps to reproduce and the impact you observed.

## Scope

In scope:

- The SOS Ring source code in this repository
- Release APKs published on GitHub Releases and F-Droid
- The peer-to-peer location sharing protocol (WebPush encryption, ECDSA message signing, QR pairing)

Out of scope: vulnerabilities in Android itself, in UnifiedPush distributors or in push servers you run yourself. Please report those to their maintainers.

## Disclosure

Reports are handled by the maintainer, Marcì Lorenzo. Fixes ship in a new release, and the issue is then published as a GitHub Security Advisory crediting the reporter, unless they prefer to stay anonymous.
