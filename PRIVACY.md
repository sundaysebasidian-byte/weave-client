# Weave privacy notice

Last updated: 2026-08-16

This notice describes the open-source preview builds in this repository. A
distributor that changes Weave, adds analytics, or operates a hosted service
must publish an accurate notice for that build.

## Distribution boundary

The public repository follows the `local-open-source` profile. Weave does not
operate accounts, a hosted control plane, proxy relays, a node marketplace, a
subscription service, advertising, analytics, crash reporting, or an in-app
remote updater. GitHub releases are static source and build artifacts published
by a maintainer; they are not a Weave backend or a remote configuration channel.

This boundary does not mean that all traffic stays on the device. A user-selected
subscription, proxy, DNS resolver, IP-quality service, destination, or LAN peer
can receive the data needed to complete that request. The current endpoint
inventory is documented in [`docs/NETWORK_ENDPOINT_INVENTORY.md`](docs/NETWORK_ENDPOINT_INVENTORY.md).
This notice is a technical description, not a legal conclusion or a promise that
any distribution method is exempt from local rules.

## What stays on the device

Weave does not operate an account, analytics, advertising, crash-reporting, or
traffic-logging service. Subscription URLs and payloads are encrypted with an
Android Keystore key. Runtime configuration is created in app-private storage
and removed when the VPN stops. Application routing rules, DNS preferences, and
the VPN disclosure acknowledgement are stored locally and excluded from Android
backup.

To implement routing, the Android VPN process can access packet metadata, DNS
requests, the local UID/package attribution of a connection, and the proxy rule
that matched it. The preview build does not upload that information to a Weave
server.

## Network parties selected by the user

When used, Weave connects to parties outside this project:

- subscription URLs imported by the user;
- proxy servers and destination services selected by the user's configuration;
- the configured DoH or DoT resolver;
- `www.gstatic.com/generate_204` during an on-demand Mihomo availability test;
- public HTTPS IP-quality endpoints (`api4.ipify.org`, `api6.ipify.org`, `ipwho.is`,
  `www.cloudflare.com/cdn-cgi/trace`, `cp.cloudflare.com/generate_204`, and
  `www.gstatic.com/generate_204`) only when the user taps “IP 质量检测”. These endpoints see the
  request's current proxy exit and may return IP, region, ASN and security-label metadata. Weave
  keeps the report in memory and does not send it to a Weave service;

Those parties have their own privacy practices. A subscription provider or
proxy operator may observe the source IP, connection timing, destination
metadata, and traffic that is not independently end-to-end encrypted. Weave
does not make an unsupported “zero knowledge” claim about third-party nodes.

## Local-network transfer

LAN transfer starts only after a user action, expires after one successful read
or five minutes, and carries AES-256-GCM ciphertext. The encryption key is in
the fragment of the `weave://` link and is not sent in the HTTP request. Anyone
who obtains the complete QR code or link before expiry can import its contents.

## Permissions

- `INTERNET` sends traffic requested by the user.
- `ACCESS_NETWORK_STATE` handles network loss and Wi-Fi/mobile transitions.
- `FOREGROUND_SERVICE_SPECIAL_USE` keeps an active VPN visible and stable.
- `POST_NOTIFICATIONS` displays VPN state where the Android version requires it.
- `BIND_VPN_SERVICE` protects the non-exported VPN service.

Weave does not request `QUERY_ALL_PACKAGES`; it lists only applications with a
launcher entry for the application-routing picker.

## Deletion and reports

Deleting a subscription removes its encrypted payload, URL, node metadata, and
affected references from the app's local storage. Uninstalling Weave removes its
application data according to Android platform behavior.

Do not post live subscription links, QR codes, node credentials, or traffic logs
in a public issue. Use GitHub private vulnerability reporting for security
problems as described in [`SECURITY.md`](SECURITY.md).
