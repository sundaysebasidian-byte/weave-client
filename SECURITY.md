# Security policy

## Supported versions

Weave currently publishes prereleases. The public Weave Next Android candidate is
[0.4.0-rc5 / Build 111](https://github.com/sundaysebasidian-byte/weave-client/releases/tag/weave-next-v0.4.0-rc5),
not a stable release. Its exact source is under the matching tag. Later source
builds do not inherit the published binary’s verification. Security fixes target
the latest published prerelease; older APKs and unofficial builds are not supported.

## Distribution boundary

The public build is intended to remain a local client: no Weave account,
hosted control plane, proxy relay, telemetry, crash-reporting service, bundled
node credentials, or silent app installation. Weave Next can check the public
GitHub release channel and verify a user-requested APK; Android handles installation
after the user opens the file. GitHub, as well as subscription, proxy, DNS,
IP-quality, and LAN endpoints, is outside Weave's service boundary. See the
[local-open-source release profile](docs/LOCAL_ONLY_RELEASE_PROFILE.md) and the
[network endpoint inventory](docs/NETWORK_ENDPOINT_INVENTORY.md) before auditing
a release. This statement describes engineering scope and is not a legal or
"zero-log" certification.

## Reporting a vulnerability

Please do not open a public issue for a vulnerability, live subscription URL,
node credential, QR code, or exploit details.

Use **Security → Report a vulnerability** in the GitHub repository to create a
private security advisory. Include the affected version, Android/macOS version,
reproduction steps, impact, and a minimal redacted sample if one is required.
Never include an unredacted personal subscription.

Maintainers should acknowledge a report within seven days. A fix date cannot be
promised for this volunteer project, but confirmed high-impact issues should be
handled privately until a patched build and advisory are ready.

The detailed threat model and release gates are in
[`docs/SECURITY.md`](docs/SECURITY.md).
