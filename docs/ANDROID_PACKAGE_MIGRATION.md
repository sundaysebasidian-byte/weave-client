# Android migration to Weave Next

The legacy Android package is `io.weave.client`. Weave Next is `io.weave.client.next`.
They install alongside each other. A Weave Next APK must never be advertised as an
in-place update to the legacy app. Keep the legacy release assets available and put
any migration notice on the release page, not in an update APK or legacy asset slot.

## Updating an existing Weave Next installation

On a supported official Android ARM64 build with the update feature, open
**Settings → About → App updates**. Automatic checks run when you open the app at
most once every 24 hours and can be switched off. Manual checks remain available.
Only matching public GitHub releases are offered. Before export, Weave checks that
the APK targets `io.weave.client.next` and ARM64, has a higher build number, and
is signed by the expected production certificate matching the installed app.
The release size and digest are checked too; the digest does not replace the
signature check.

Tap **Download and verify**, then **Save APK**. Choose a local folder such as
Downloads, open the saved file from Android’s Files app, and confirm the system
update. Android may require you to allow the particular Files app to install an APK;
Weave does not request install-source permission. Canceling the Android prompt
does not replace the installed app. Successful in-place updates to Weave Next
do not require importing a legacy backup.

## Moving data from the legacy app

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
