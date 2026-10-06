# Weave privacy notice

**[简体中文（主要阅读版本）](PRIVACY.zh-CN.md)** · English

Last updated: 2026-10-04

This notice describes the local-first Android client in this repository. A
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

Live QR scanning requests the camera only after the user chooses Scan. Camera frames are
decoded on the device, are not photographed or saved, and are not uploaded. Analysis stops
when the scanner closes or the app leaves the foreground. After a code is accepted, a
subscription URL can be fetched by the normal user-initiated import workflow.

To implement routing, the Android VPN process can access packet metadata, DNS
requests, the local UID/package attribution of a connection, and the proxy rule
that matched it. Weave does not upload that information to a Weave
server.

## Network parties selected by the user

When used, Weave connects to parties outside this project:

- subscription URLs imported by the user;
- HTTPS node-provider URLs explicitly referenced by an imported subscription, fetched only
  during import/update (at most 16 providers and 5 MiB combined); rule-provider URLs are not
  fetched, and a failed child fetch leaves the previous encrypted subscription untouched;
- proxy servers and destination services selected by the user's configuration;
- the configured DoH or DoT resolver, and plaintext bootstrap DNS used to resolve
  encrypted DNS server names. The default bootstrap servers are `223.5.5.5` and
  `119.29.29.29`; the alternative is `1.1.1.1` and `9.9.9.9`. Bootstrap queries
  can reveal the resolver hostname to the local network; encrypted DNS is not
  a claim that every DNS request traverses a proxy or that no leakage is possible;
- `www.gstatic.com/generate_204` for Mihomo node health checks. An active automatic
  group may probe at the configured 45- or 60-second interval while the VPN runs;
  user-triggered health checks can also send requests;
- public HTTPS IP-quality endpoints (`api4.ipify.org`, `api6.ipify.org`, `ipwho.is`,
  `www.cloudflare.com/cdn-cgi/trace`, `cp.cloudflare.com/generate_204`, and
  `www.gstatic.com/generate_204`) only when the user taps “IP 质量检测”. These endpoints see the
  request's current proxy exit and may return IP, region, ASN and security-label metadata. Weave
  keeps the report in memory and does not send it to a Weave service;
- common-site reachability endpoints (`x.com`, `www.tiktok.com`, `www.youtube.com`,
  `www.google.com/generate_204`, `chatgpt.com` and `claude.ai`) only when the user runs the full
  “网络与隐私检测” while the VPN is connected. Weave sends a small HTTPS `GET` with a byte-range,
  records only status and round-trip time, and does not read or retain page content. A site can
  still see the selected proxy exit, and a 401/403/429 means the service responded but may require
  login, region access or rate-limit clearance;
- Google's public STUN endpoint (`stun.l.google.com:19302`) for one WebRTC ICE probe only when the
  user opens the browser privacy lab and runs the test. The endpoint can see the request's network
  exit. ICE candidates and browser-surface fields remain in memory and are not uploaded by Weave;
- independent diagnostic pages (`dnsleaktest.com` and `browserleaks.com`) only after the user
  taps a named check. They open inside a restricted in-app WebView, operate under their own privacy
  practices, and can see the current exit IP and data needed for their DNS/browser tests. Weave
  neither scrapes nor stores their results. Results describe that WebView, not Chrome or other apps;

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
- `FOREGROUND_SERVICE` and its special-use subtype support the active VPN service.
- `FOREGROUND_SERVICE_SPECIAL_USE` keeps an active VPN visible and stable.
- `POST_NOTIFICATIONS` displays VPN state where the Android version requires it.
- `CAMERA` is requested only for user-selected live QR scanning; frames are decoded
  locally and not saved or uploaded.
- `RECEIVE_BOOT_COMPLETED` preserves the user's optional scheduled subscription
  refresh job across reboots. Automatic refresh is disabled by default; enabling
  it permits background HTTPS requests to the selected subscription providers.
- `BIND_VPN_SERVICE` protects the non-exported VPN service.

China-direct routing is enabled by default in rule mode. Matching domestic and
local-network traffic uses a direct outbound rather than a proxy node. This is
different from excluding an application from the VPN interface; the optional
Direct-app VPN bypass setting is off by default. Global proxy mode ignores saved
application rules and China-direct routing; it uses the default outbound after
any applicable policy rules. Direct mode selects direct egress. A user can also
explicitly select a direct default outbound. The privacy report distinguishes
saved settings from locally confirmed connection/system state, not the result
of an external packet capture or proof that no bypass exists.

Weave does not request `QUERY_ALL_PACKAGES`; it lists only applications with a
launcher entry for the application-routing picker. The same on-device list can identify a small,
fixed set of known compatible proxy clients. Migration starts only after the user confirms a source
and selects that client's exported file in Android's system file picker; Weave cannot read another
application's private storage and does not modify the source application.

## Deletion and reports

Deleting a subscription removes its encrypted payload, URL, node metadata, and
affected references from the app's local storage. Uninstalling Weave removes its
application data according to Android platform behavior.

Do not post live subscription links, QR codes, node credentials, or traffic logs
in a public issue. Use GitHub private vulnerability reporting for security
problems as described in [`SECURITY.md`](SECURITY.md).
