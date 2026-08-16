import SwiftUI
import WeaveCore

struct MonetTokens {
    let ink: Color
    let accent: Color
    let lavender: Color
    let coral: Color
    let canvas: Color
    let glass: Color
    let muted: Color
    let good: Color

    static func resolve(_ palette: WeavePalette, dark: Bool) -> MonetTokens {
        switch (palette, dark) {
        case (.impressionSunrise, false):
            MonetTokens(
                ink: Color(hex: 0x3E5875), accent: Color(hex: 0x9CB8B0),
                lavender: Color(hex: 0xB7A9C5), coral: Color(hex: 0xDF9A7D),
                canvas: Color(hex: 0xF2ECE4), glass: Color(hex: 0xFFF9F1),
                muted: Color(hex: 0x747986), good: Color(hex: 0x527C74)
            )
        case (.waterLilies, false):
            MonetTokens(
                ink: Color(hex: 0x405D6B), accent: Color(hex: 0x94BEB5),
                lavender: Color(hex: 0xA9A1C2), coral: Color(hex: 0xD7A09A),
                canvas: Color(hex: 0xEDF1EE), glass: Color(hex: 0xFAFCF8),
                muted: Color(hex: 0x6D7A83), good: Color(hex: 0x4F7D75)
            )
        case (.poppyField, false):
            MonetTokens(
                ink: Color(hex: 0x5A5260), accent: Color(hex: 0xA9BAA0),
                lavender: Color(hex: 0xB8A5BD), coral: Color(hex: 0xD88970),
                canvas: Color(hex: 0xF3ECE3), glass: Color(hex: 0xFFF9F0),
                muted: Color(hex: 0x7D7475), good: Color(hex: 0x647B67)
            )
        case (.twilightGarden, false):
            MonetTokens(
                ink: Color(hex: 0x3C456E), accent: Color(hex: 0x9CAFC0),
                lavender: Color(hex: 0xB59DBC), coral: Color(hex: 0xD8947C),
                canvas: Color(hex: 0xF0EBF0), glass: Color(hex: 0xFCF8F1),
                muted: Color(hex: 0x76758A), good: Color(hex: 0x5B7780)
            )
        case (.impressionSunrise, true):
            MonetTokens(
                ink: Color(hex: 0xEAE9E2), accent: Color(hex: 0x83AEA7),
                lavender: Color(hex: 0xA897C3), coral: Color(hex: 0xD98F7D),
                canvas: Color(hex: 0x131B2A), glass: Color(hex: 0x202C40),
                muted: Color(hex: 0xB5BDCB), good: Color(hex: 0x8CB9AE)
            )
        case (.waterLilies, true):
            MonetTokens(
                ink: Color(hex: 0xEAF1ED), accent: Color(hex: 0x7EAAA4),
                lavender: Color(hex: 0xA89BC4), coral: Color(hex: 0xD69B96),
                canvas: Color(hex: 0x142326), glass: Color(hex: 0x20343A),
                muted: Color(hex: 0xB7C6C7), good: Color(hex: 0x83B8AE)
            )
        case (.poppyField, true):
            MonetTokens(
                ink: Color(hex: 0xF4EDE2), accent: Color(hex: 0x9EB590),
                lavender: Color(hex: 0xB9A2BA), coral: Color(hex: 0xD68D78),
                canvas: Color(hex: 0x241F1C), glass: Color(hex: 0x352C28),
                muted: Color(hex: 0xC5B9AE), good: Color(hex: 0xA6BE9A)
            )
        case (.twilightGarden, true):
            MonetTokens(
                ink: Color(hex: 0xEFEAF2), accent: Color(hex: 0x91A8C2),
                lavender: Color(hex: 0xB49CC4), coral: Color(hex: 0xD88F7D),
                canvas: Color(hex: 0x171B2B), glass: Color(hex: 0x272D45),
                muted: Color(hex: 0xBEC1D1), good: Color(hex: 0x9FBAC4)
            )
        }
    }
}

private struct MonetTokensKey: EnvironmentKey {
    static let defaultValue = MonetTokens.resolve(.impressionSunrise, dark: false)
}

extension EnvironmentValues {
    var monet: MonetTokens {
        get { self[MonetTokensKey.self] }
        set { self[MonetTokensKey.self] = newValue }
    }
}

struct MonetScene<Content: View>: View {
    @EnvironmentObject private var model: AppModel
    @Environment(\.colorScheme) private var colorScheme
    @ViewBuilder let content: () -> Content

    var body: some View {
        let tokens = MonetTokens.resolve(model.palette, dark: colorScheme == .dark)
        ZStack {
            MonetAtmosphere(tokens: tokens)
            content()
        }
        .environment(\.monet, tokens)
        .tint(tokens.ink)
    }
}

struct MonetAtmosphere: View {
    let tokens: MonetTokens

    var body: some View {
        ZStack {
            LinearGradient(
                colors: [tokens.canvas, tokens.lavender.opacity(0.28), tokens.canvas],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            Circle()
                .fill(tokens.accent.opacity(0.34))
                .frame(width: 430, height: 430)
                .blur(radius: 86)
                .offset(x: -180, y: -260)
            Circle()
                .fill(tokens.coral.opacity(0.24))
                .frame(width: 330, height: 330)
                .blur(radius: 80)
                .offset(x: 170, y: -330)
            Ellipse()
                .fill(tokens.lavender.opacity(0.34))
                .frame(width: 470, height: 340)
                .blur(radius: 92)
                .offset(x: 150, y: 180)
            Ellipse()
                .fill(tokens.accent.opacity(0.18))
                .frame(width: 520, height: 230)
                .blur(radius: 78)
                .offset(x: -80, y: 520)
        }
        .ignoresSafeArea()
        .accessibilityHidden(true)
    }
}

struct GlassPanel<Content: View>: View {
    @Environment(\.monet) private var tokens
    @Environment(\.colorScheme) private var colorScheme
    let cornerRadius: CGFloat
    @ViewBuilder let content: () -> Content

    init(cornerRadius: CGFloat = 28, @ViewBuilder content: @escaping () -> Content) {
        self.cornerRadius = cornerRadius
        self.content = content
    }

    var body: some View {
        content()
            .background {
                ZStack {
                    Rectangle().fill(.ultraThinMaterial)
                    LinearGradient(
                        colors: [
                            tokens.glass.opacity(colorScheme == .dark ? 0.78 : 0.76),
                            tokens.accent.opacity(colorScheme == .dark ? 0.14 : 0.18),
                            tokens.lavender.opacity(colorScheme == .dark ? 0.12 : 0.17),
                            tokens.coral.opacity(colorScheme == .dark ? 0.06 : 0.09),
                        ],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .stroke(
                        LinearGradient(
                            colors: [.white.opacity(colorScheme == .dark ? 0.24 : 0.88), .white.opacity(0.18)],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        ),
                        lineWidth: 1
                    )
            }
            .shadow(color: Color.black.opacity(colorScheme == .dark ? 0.24 : 0.14), radius: 18, y: 10)
    }
}

struct PageHeader: View {
    @Environment(\.monet) private var tokens
    let title: String
    let subtitle: String

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title)
                .font(.system(size: 36, weight: .bold, design: .rounded))
                .foregroundStyle(.primary)
            Text(subtitle)
                .font(.subheadline.weight(.medium))
                .foregroundStyle(tokens.muted)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

struct GlassIcon: View {
    @Environment(\.monet) private var tokens
    let systemName: String
    var color: Color? = nil

    var body: some View {
        Image(systemName: systemName)
            .font(.system(size: 18, weight: .semibold))
            .foregroundStyle(color ?? tokens.ink)
            .frame(width: 46, height: 46)
            .background(tokens.accent.opacity(0.30), in: RoundedRectangle(cornerRadius: 15, style: .continuous))
    }
}

struct StatusPill: View {
    @Environment(\.monet) private var tokens
    let text: String
    let active: Bool

    var body: some View {
        HStack(spacing: 7) {
            Circle().fill(active ? tokens.good : tokens.muted).frame(width: 7, height: 7)
            Text(text).font(.caption.weight(.semibold))
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .background((active ? tokens.accent : tokens.lavender).opacity(0.24), in: Capsule())
    }
}

struct PrimaryGlassButtonStyle: ButtonStyle {
    @Environment(\.monet) private var tokens

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.headline.weight(.bold))
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity, minHeight: 56)
            .background(
                LinearGradient(
                    colors: [tokens.ink, tokens.ink.opacity(0.88)],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                ),
                in: RoundedRectangle(cornerRadius: 20, style: .continuous)
            )
            .overlay {
                RoundedRectangle(cornerRadius: 20, style: .continuous)
                    .stroke(.white.opacity(0.18), lineWidth: 1)
            }
            .scaleEffect(configuration.isPressed ? 0.975 : 1)
            .opacity(configuration.isPressed ? 0.88 : 1)
            .animation(.snappy(duration: 0.18), value: configuration.isPressed)
    }
}

extension Color {
    init(hex: UInt32) {
        self.init(
            red: Double((hex >> 16) & 0xff) / 255,
            green: Double((hex >> 8) & 0xff) / 255,
            blue: Double(hex & 0xff) / 255
        )
    }
}
