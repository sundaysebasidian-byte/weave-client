# Android migration to Weave Next

The legacy Android package is `io.weave.client`. Weave Next is `io.weave.client.next`.
They install alongside each other. A Weave Next APK must never be advertised as an
in-place update to the legacy app. Keep the legacy release assets available and put
any migration notice on the release page, not in an update APK or legacy asset slot.

Route updates only to an exact package match. A matching package also must use the
correct pinned signing certificate and a higher version code. No automatic Android
application updater is enabled in this source revision.

Keep the old app installed. In old Weave, export a password-protected backup via the
app's backup screen. Open Weave Next and restore through the system file picker,
with the same password. Check subscriptions, nodes, routes, groups, rules and
settings after closing and reopening Weave Next. The apps do not share data; the
backup and its password stay under your control and are not sent to a service.
A restore can replace Weave Next's existing profile. Back up that profile first
if it already has data. Only remove the old app after validating the migration.

Android VPN permission does not transfer. Once ready to switch, stop the old VPN
before enabling the new one. Re-add Weave Next's Quick Settings tile and widget;
existing instances still belong to the legacy app.
