# Android: transfer selected subscriptions without LAN

In **Subscriptions → Transfer subscriptions → Export**, check the subscriptions to send, then choose **Save encrypted subscription file** rather than generating a LAN code. Enter the same passphrase twice (at least eight characters) and choose a destination in Files. Only the selected sources, identifiers, names and materialized node data go into the `.weavesubs` file. Transfer it yourself and communicate the passphrase separately. A cloud-backed Files location may sync the encrypted file; Weave has no transfer service.

On the receiving Android client, open **Transfer subscriptions → Import → Open encrypted subscription file**. Enter the passphrase, choose the file, inspect the subscription and node counts, and confirm. Canceling before confirmation leaves the profile unchanged. The receiver never needs to fetch the source or child providers to preview the transfer. After import, remote subscriptions are subject to the receiving device’s normal refresh settings.

This is an **add-only** import, not a merge. If any ID or unambiguous name/source pair already exists, no item is added. An ambiguous match, a changed index, or a full restore during preview also prevents publication. Local routes, installed-app selections, groups, DNS, preferences and existing subscriptions are not written. Live quota and usage readings are not transferable settings. No listeners, QR codes, clipboard copies, new permissions, account or service are required.

## Compatibility

- The existing six-digit code is for one-time LAN links, not file decryption. Existing Android/iOS/macOS/Windows LAN v1 bytes and full-backup envelopes remain unchanged.
- Android clients with the selected-file feature can exchange these files. Other clients and older Android releases should use the established LAN protocol; no cross-platform file compatibility is claimed.
- The new file reuses the authenticated WVBK v1 envelope. Each entry is named `sub-<lowercase-uuid>.json` with one JSON string containing the Base64 of a single canonical WVLAN001 record. No full-backup manifest, settings, routes or other entry type is accepted. The encrypted full-profile restore flow requires a different manifest and rejects selected files.
- Limit: 1–64 subscriptions, 20 MiB aggregate record bytes, 32 MiB input file; original per-field LAN limits apply. Contents, password and source URI are not logged or saved to an instance-state bundle. The file dialog is screenshot-protected.

## QA scope

Production JVM tests: `SubscriptionFileCodecTest`, legacy LAN known vector, backup v1/v2 and archive limits. Android-only fixture additions in `SubscriptionTransferIntegrationTest` and `NativeTransferFeedbackTest` cover add-only publication, discarding a preview, resolving no providers, preserving local records/settings, rejecting post-restore and concurrent edits, and choosing only checked items. System Files interoperability, rotation while Files is open, accessibility at large text and physical devices still need native QA; JVM tests do not prove them.
