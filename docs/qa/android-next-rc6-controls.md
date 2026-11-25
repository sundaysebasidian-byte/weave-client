# Android Next · Connection and navigation controls

Development candidate. Baseline: `b2569a469e5f197b442a618bf42f4c207f790eed`. This is an interaction inventory, not an Android device sign-off. The Claude layout and selected Weave color palette remain authoritative. Apple Settings informs only the alignment of confirmation titles, explanatory text and actions.

| # | Detail | Where to verify it |
|---:|---|---|
| 01 | All three measurements display the whole numeric reading and its unit; none uses a one-line ellipsis. | Home, active connection |
| 02 | Changing digits keep tabular spacing. | Compare readings from the same fixture |
| 03 | Roomy layouts use the existing 26 sp headline; medium-width cells use 20 sp when necessary. | Wide vs narrow Home |
| 04 | If only the unit needs to wrap, preserve all three columns and reserve a second line. | Narrow phone, regular type |
| 05 | When an unbroken numeric token or localized caption won’t fit, use stacked full-width groups. No text shrinking below 20 sp. | French/German; large type |
| 06 | Layout selection depends on measured representative readings and the translated labels, not the traffic sample. | Idle → busy → idle |
| 07 | Missing values keep the dash; the download sparkline appears only with real samples and its space is reserved. | Disconnected vs connected Home |
| 08 | The page body accepts one horizontal step through the same ordered destinations as the dock. | Interior of any of the four tabs |
| 09 | A swipe on the dock also moves a single step and never wraps. | Home and Settings endpoints |
| 10 | Vertical or diagonal-first motion is left to the current scrollable list. | Scroll a route/subscription list |
| 11 | A second finger, a competing child or a canceled stream does not commit navigation; release below threshold does nothing. | Touch interaction |
| 12 | Screen-body swipes exclude at least 24 dp at either edge and the system’s larger reported Back gesture inset. | Android gesture navigation |
| 13 | Dock items expose a selected tab within a selectable group. Decorative tab icons no longer duplicate spoken labels. | TalkBack |
| 14 | Tapping the mode chip opens the existing choices; swiping the chip or the open selector moves one mode in reading order. | Capsule and open dial |
| 15 | Long-press scrubbing still previews individual detents. Preview is not the committed mode; release selects. A long press without moving leaves choices open. | Hold and scrub the chip |
| 16 | Mode steps mirror in RTL. At 1.3× type or above the cramped arc becomes three labeled radio rows. | Forced RTL; large type |
| 17 | Chip and dock heights are minimums, not text-clipping ceilings. | Largest supported type |
| 18 | On connection, Global ties a ring, Rules draws a fork, and Direct is a straight stitch. Color is not the only difference. Connection verification is still reported in text, never inferred from a knot. | Connected state, three modes |
| 19 | The knot settles with a finite, non-bouncing transition; switching mode changes its geometry without starting a loop. No motion is required to operate the controls. | System animations off; idle Home |
| 20 | The 33 app-owned alert call sites share the chosen palette’s paper, ink and hairline, a leading title/optional icon, a divider and the existing actions. Native VPN consent, document picking and installation do not pass through this component. | Confirmation, DNS child Back, export/update and external Android activities |

## Review and release boundary

Host-executable checks cover swipe axis, release threshold, LTR/RTL direction and actual extreme throughput formatting. The device suite adds a width × type × language text-layout check, wheel tap/swipe checks and dock/content transitions. Run on the actual candidate APK before accepting appearance or accessibility. Explicit device checks still required: TalkBack and keyboard focus, actual Back/Cancel/long press, interrupted gestures and nested scrolling, IME in input dialogs, light/dark palettes, landscape, and Android’s “Remove animations”. No before/after screenshots or font rendering are claimed here.

Authorized deviations from the baseline are limited to the app modal hierarchy, adaptive measurements, mode-specific knot, tab/mode gestures and accessibility accommodation. No locale table, route icon, permission, VPN implementation, release metadata or platform-owned surface was changed by this workstream.
