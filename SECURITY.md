# Security policy

## Supported versions

Weave is currently an alpha preview. Security fixes are made only on the latest
published preview. Older APKs and unofficial builds are not supported.

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
