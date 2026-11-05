using System.Numerics;
using Microsoft.UI.Composition;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Hosting;

namespace Weave.Windows;

/// <summary>
/// Page entrance motion. Everything here is a Composition implicit animation: it runs on the compositor thread,
/// never touches layout and costs nothing per frame on the UI thread.
/// </summary>
internal static class PageMotion
{
    private static readonly bool Enabled = AnimationsEnabled();

    /// <summary>False when Windows' "Show animations in Windows" is off: decorative motion then jumps straight to its end state.</summary>
    internal static bool AnimationsAllowed => Enabled;

    // Windows' "Show animations" switch (SPI_GETCLIENTAREAANIMATION). If it cannot be read, motion stays on.
    private static bool AnimationsEnabled()
    {
        try { var enabled = 1; return !SystemParametersInfo(0x1042, 0, ref enabled, 0) || enabled != 0; }
        catch (Exception) { return true; }
    }

    [System.Runtime.InteropServices.DllImport("user32.dll", EntryPoint = "SystemParametersInfoW")]
    private static extern bool SystemParametersInfo(uint action, uint parameter, ref int value, uint flags);

    /// <summary>Fade and rise when <paramref name="element"/> becomes visible. <paramref name="order"/> staggers siblings.</summary>
    public static void Entrance(UIElement element, int order)
    {
        if (!Enabled) return;
        try
        {
            var compositor = ElementCompositionPreview.GetElementVisual(element).Compositor;
            ElementCompositionPreview.SetIsTranslationEnabled(element, true);
            // Fluent "decelerate": quick start, soft landing.
            var easing = compositor.CreateCubicBezierEasingFunction(new Vector2(0.1f, 0.9f), new Vector2(0.2f, 1f));
            var delay = TimeSpan.FromMilliseconds(Math.Clamp(order, 0, 4) * 40);

            var fade = compositor.CreateScalarKeyFrameAnimation();
            fade.Target = "Opacity";
            fade.InsertKeyFrame(0f, 0f);
            fade.InsertKeyFrame(1f, 1f, easing);
            fade.Duration = TimeSpan.FromMilliseconds(240);
            fade.DelayTime = delay;
            fade.DelayBehavior = AnimationDelayBehavior.SetInitialValueBeforeDelay;

            // Keep the card's own elevation (Translation.Z drives ThemeShadow depth) while sliding on Y.
            var rest = new Vector3(0f, 0f, element.Translation.Z);
            var rise = compositor.CreateVector3KeyFrameAnimation();
            rise.Target = "Translation";
            rise.InsertKeyFrame(0f, new Vector3(0f, 12f, rest.Z));
            rise.InsertKeyFrame(1f, rest, easing);
            rise.Duration = TimeSpan.FromMilliseconds(320);
            rise.DelayTime = delay;
            rise.DelayBehavior = AnimationDelayBehavior.SetInitialValueBeforeDelay;

            var group = compositor.CreateAnimationGroup();
            group.Add(fade);
            group.Add(rise);
            ElementCompositionPreview.SetImplicitShowAnimation(element, group);
        }
        catch (Exception) { /* Motion is decoration; never let it block the UI. */ }
    }
}
