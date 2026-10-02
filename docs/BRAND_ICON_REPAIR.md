# Source icon repair

The previous 1024 px raster master contained 11,505 opaque near-black pixels in the four outer 16% corner squares. At README size this appeared as thin black bands around the curved corners.

The built-in imagegen tool repaired the supplied raster, retaining the woven mark, coral dot and cream painted panel. Two outputs were reviewed; the first was selected because it preserved the composition more closely. The chosen repaired source is `docs/images/weave-icon-master.png`. Its same corner measurement is zero. Generated image provenance and the original reference hashes are retained in the private delivery evidence; no API key or new credential was used.

Run `bash tools/export-brand-icons.sh` on macOS with the existing Swift, sips and cwebp tools. The native exporter starts from a cleared RGBA canvas, creates a deterministic geometric silhouette and preserves genuine alpha. It trims generated perimeter flecks after the source repair; it is not a runtime mask hiding the old black pixels. iOS uses a full opaque cream canvas and exports RGB, while Android, documentation, macOS and Windows retain alpha. The Windows build wraps its generated 256 px PNG into ICO as before.

The exporter updates the README WebP, Android foreground WebP, macOS master, all ten iconset images and seven ICNS PNG chunks, Windows PNG and iOS app icon. Native Android render QA also exposed a separate existing crop problem: AdaptiveIconDrawable expands its layers, so the knot was being cut at the top and bottom. All four color launcher definitions now reference an 18/108 per-side inset of the repaired source, preserving the woven mark on circular launchers. This uses Android's documented foreground safe zone and does not hide the original black source defect. The monochrome Android vector is unchanged. The historical `GenerateAppIcon.swift` creates a different legacy mark; it is not the canonical painted source.

Adaptive-icon geometry reference: https://developer.android.com/develop/ui/compose/system/icon_design_adaptive . The 108dp layer and central 66dp safe zone are platform requirements; the chosen proportion is also verified by actual packaged drawable rendering.

Private QA checks each raster's decoded pixels and ICNS container, then renders at the README's actual 112 px on light and dark surfaces. An Android instrumentation test decodes the packaged WebP before adaptive masking and renders the actual adaptive icon on both backgrounds. Platform packaging checks do not constitute a Windows VPN or Apple distribution validation.
