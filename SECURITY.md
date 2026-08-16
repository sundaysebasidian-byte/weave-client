# Security policy

## Supported versions

Weave is currently an alpha preview. Security fixes are made only on the latest
published preview. Older APKs and unofficial builds are not supported.

## Distribution boundary

The public build is intended to remain a local client: no Weave account,
hosted control plane, proxy relay, telemetry, crash-reporting service, bundled
node credentials, or in-app remote updater is part of this repository. A user
may still deliberately connect to a subscription, proxy, DNS, IP-quality, or
LAN endpoint; those third parties are outside Weave's trust boundary. See the
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
promised for this volunteer alpha, but confirmed high-impact issues should be
handled privately until a patched build and advisory are ready.

The detailed threat model and release gates are in
[`docs/SECURITY.md`](docs/SECURITY.md).
