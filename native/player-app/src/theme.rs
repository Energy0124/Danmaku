//! Design-system layer (phase 3 M2.5).
//!
//! The P0-S5 spike's accepted look, promoted into reusable tokens: palette,
//! typography, metrics, and animation helpers. All player screens style
//! themselves through this module — inline `Color32`/`FontId` literals in
//! screen code are a review smell.

use std::sync::Arc;

use eframe::egui::{
    self, Color32, CornerRadius, FontData, FontDefinitions, FontFamily, Stroke, vec2,
};

/// Color tokens. Alpha-carrying helpers live below (`scrim`, `outline`).
pub mod palette {
    use eframe::egui::Color32;

    /// Deepest backdrop, behind video.
    pub const VIDEO_BACKDROP: Color32 = Color32::from_rgb(5, 6, 8);
    /// Deep application background (scroll wells, extreme_bg).
    pub const BG_DEEP: Color32 = Color32::from_rgb(7, 8, 11);
    /// Default panel background.
    pub const BG_PANEL: Color32 = Color32::from_rgb(12, 14, 18);
    /// Raised surfaces: menus, dialogs, cards.
    pub const SURFACE: Color32 = Color32::from_rgb(18, 20, 25);
    /// Subtle widget background.
    pub const SURFACE_FAINT: Color32 = Color32::from_rgb(28, 32, 39);
    /// Hovered widget background.
    pub const WIDGET_HOVER: Color32 = Color32::from_rgb(38, 43, 52);
    /// Active/pressed widget background.
    pub const WIDGET_ACTIVE: Color32 = Color32::from_rgb(48, 65, 86);
    /// Primary accent (selection, focused slider fill).
    pub const ACCENT: Color32 = Color32::from_rgb(54, 112, 168);
    /// Accent used for hover outlines (poster cards, focus rings).
    pub const ACCENT_OUTLINE: Color32 = Color32::from_rgb(119, 184, 238);
    /// Primary foreground text.
    pub const TEXT_PRIMARY: Color32 = Color32::from_rgb(233, 238, 244);
    /// Secondary text: timestamps, values.
    pub const TEXT_SECONDARY: Color32 = Color32::from_rgb(214, 221, 229);
    /// Muted text: captions, hints.
    pub const TEXT_MUTED: Color32 = Color32::from_rgb(137, 149, 164);
    /// Errors and destructive affordances.
    pub const DANGER: Color32 = Color32::from_rgb(255, 126, 126);
    /// Base color of translucent overlay panels (control bar).
    pub const OVERLAY_BASE: Color32 = Color32::from_rgb(10, 12, 16);
}

/// Type scale. Every on-screen string picks one of these.
pub mod typography {
    use eframe::egui::FontId;

    pub fn title() -> FontId {
        FontId::proportional(17.0)
    }

    pub fn heading() -> FontId {
        FontId::proportional(15.0)
    }

    pub fn body() -> FontId {
        FontId::proportional(14.0)
    }

    pub fn caption() -> FontId {
        FontId::proportional(13.0)
    }

    pub fn small() -> FontId {
        FontId::proportional(12.0)
    }
}

/// Layout metrics shared across screens.
pub mod metrics {
    /// Outer gutter between chrome and the window/video edges.
    pub const GUTTER: f32 = 18.0;
    /// Inner padding of overlay panels.
    pub const OVERLAY_PADDING: f32 = 14.0;
    /// Corner radius of overlay panels (control bar).
    pub const OVERLAY_RADIUS: f32 = 10.0;
    /// Corner radius of cards (posters, dialogs).
    pub const CARD_RADIUS: f32 = 8.0;
    /// Control bar height in the playback view.
    pub const CONTROL_BAR_HEIGHT: f32 = 96.0;
    /// Vertical rhythm between stacked control rows.
    pub const ROW_GAP: f32 = 8.0;
}

/// Seconds the fade-over-video chrome stays fully visible after activity.
pub const CHROME_HOLD_SECONDS: f32 = 1.7;
/// Seconds the fade-over-video chrome takes to fade out after the hold.
pub const CHROME_FADE_SECONDS: f32 = 1.1;

/// Applies the bundled fonts (CJK-capable) and the theme's widget style to
/// an egui context. Call once at startup.
pub fn apply(ctx: &egui::Context) {
    let mut fonts = FontDefinitions::default();
    let name = "noto-sans-cjk-tc".to_owned();
    fonts.font_data.insert(
        name.clone(),
        Arc::new(FontData::from_static(include_bytes!(
            "../assets/NotoSansCJKtc-Regular.otf"
        ))),
    );
    fonts
        .families
        .entry(FontFamily::Proportional)
        .or_default()
        .insert(0, name.clone());
    fonts
        .families
        .entry(FontFamily::Monospace)
        .or_default()
        .push(name);
    ctx.set_fonts(fonts);

    let mut visuals = egui::Visuals::dark();
    visuals.panel_fill = palette::BG_PANEL;
    visuals.window_fill = palette::SURFACE;
    visuals.faint_bg_color = palette::SURFACE_FAINT;
    visuals.extreme_bg_color = palette::BG_DEEP;
    visuals.selection.bg_fill = palette::ACCENT;
    visuals.widgets.hovered.bg_fill = palette::WIDGET_HOVER;
    visuals.widgets.active.bg_fill = palette::WIDGET_ACTIVE;
    visuals.menu_corner_radius = CornerRadius::same(metrics::CARD_RADIUS as u8);
    visuals.window_corner_radius = CornerRadius::same(metrics::CARD_RADIUS as u8);
    ctx.set_visuals(visuals);

    let mut style = (*ctx.style()).clone();
    style.spacing.item_spacing = vec2(8.0, 8.0);
    style.spacing.button_padding = vec2(12.0, 6.0);
    ctx.set_style(style);
}

/// Fade-over-video alpha: fully opaque during `hold_seconds` of inactivity,
/// then a linear fade over `fade_seconds` (linear was validated against the
/// spike gate; easing is available separately for motion).
pub fn fade_alpha(inactive_seconds: f32, hold_seconds: f32, fade_seconds: f32) -> f32 {
    if !inactive_seconds.is_finite() || inactive_seconds <= hold_seconds {
        return 1.0;
    }
    if fade_seconds <= 0.0 {
        return 0.0;
    }
    (1.0 - (inactive_seconds - hold_seconds) / fade_seconds).clamp(0.0, 1.0)
}

/// Cubic ease-out: fast start, gentle settle. For hover/transition motion.
pub fn ease_out_cubic(t: f32) -> f32 {
    let t = t.clamp(0.0, 1.0);
    1.0 - (1.0 - t).powi(3)
}

/// Cubic ease-in-out for symmetric transitions.
pub fn ease_in_out_cubic(t: f32) -> f32 {
    let t = t.clamp(0.0, 1.0);
    if t < 0.5 {
        4.0 * t * t * t
    } else {
        1.0 - (-2.0 * t + 2.0).powi(3) / 2.0
    }
}

/// Linear color interpolation in unmultiplied RGBA space.
pub fn mix(from: Color32, to: Color32, t: f32) -> Color32 {
    let t = t.clamp(0.0, 1.0);
    let channel = |a: u8, b: u8| -> u8 { (a as f32 + (b as f32 - a as f32) * t).round() as u8 };
    Color32::from_rgba_unmultiplied(
        channel(from.r(), to.r()),
        channel(from.g(), to.g()),
        channel(from.b(), to.b()),
        channel(from.a(), to.a()),
    )
}

/// Translucent overlay-panel fill at the given chrome alpha (0..=1).
pub fn overlay_fill(alpha: f32) -> Color32 {
    let base = palette::OVERLAY_BASE;
    Color32::from_rgba_premultiplied(
        base.r(),
        base.g(),
        base.b(),
        (alpha.clamp(0.0, 1.0) * 210.0).round() as u8,
    )
}

/// Hairline outline for overlay panels at the given chrome alpha (0..=1).
pub fn overlay_outline(alpha: f32) -> Stroke {
    Stroke::new(
        1.0_f32,
        Color32::from_rgba_premultiplied(255, 255, 255, (alpha.clamp(0.0, 1.0) * 36.0) as u8),
    )
}

/// Primary text color faded to the given chrome alpha (0..=1).
pub fn text_primary_faded(alpha: f32) -> Color32 {
    let base = palette::TEXT_PRIMARY;
    Color32::from_rgba_premultiplied(
        base.r(),
        base.g(),
        base.b(),
        (alpha.clamp(0.0, 1.0) * 220.0).round() as u8,
    )
}

/// Card outline stroke; `hover_t` in 0..=1 animates toward the accent.
pub fn card_outline(hover_t: f32) -> Stroke {
    let color = mix(
        Color32::from_rgba_unmultiplied(255, 255, 255, 24),
        palette::ACCENT_OUTLINE,
        ease_out_cubic(hover_t),
    );
    Stroke::new(1.0_f32, color)
}

#[cfg(test)]
mod tests {
    use super::{
        CHROME_FADE_SECONDS, CHROME_HOLD_SECONDS, ease_in_out_cubic, ease_out_cubic, fade_alpha,
        mix, palette,
    };
    use eframe::egui::Color32;

    fn assert_close(actual: f32, expected: f32) {
        assert!(
            (actual - expected).abs() < 0.000_1,
            "actual {actual}, expected {expected}"
        );
    }

    #[test]
    fn fade_holds_then_fades_linearly_and_clamps() {
        assert_close(
            fade_alpha(0.0, CHROME_HOLD_SECONDS, CHROME_FADE_SECONDS),
            1.0,
        );
        assert_close(
            fade_alpha(1.7, CHROME_HOLD_SECONDS, CHROME_FADE_SECONDS),
            1.0,
        );
        assert_close(
            fade_alpha(1.7 + 0.55, CHROME_HOLD_SECONDS, CHROME_FADE_SECONDS),
            0.5,
        );
        assert_close(
            fade_alpha(10.0, CHROME_HOLD_SECONDS, CHROME_FADE_SECONDS),
            0.0,
        );
        assert_close(
            fade_alpha(f32::NAN, CHROME_HOLD_SECONDS, CHROME_FADE_SECONDS),
            1.0,
        );
        assert_close(fade_alpha(5.0, 1.0, 0.0), 0.0);
    }

    #[test]
    fn easing_hits_endpoints_and_stays_in_range() {
        for ease in [ease_out_cubic, ease_in_out_cubic] {
            assert_close(ease(0.0), 0.0);
            assert_close(ease(1.0), 1.0);
            assert_close(ease(-1.0), 0.0);
            assert_close(ease(2.0), 1.0);
            let mut previous = 0.0;
            for step in 0..=20 {
                let value = ease(step as f32 / 20.0);
                assert!((0.0..=1.0).contains(&value));
                assert!(value >= previous - 0.000_1, "easing must be monotonic");
                previous = value;
            }
        }
    }

    #[test]
    fn mix_interpolates_between_endpoints() {
        let transparent = Color32::from_rgba_unmultiplied(0, 0, 0, 0);
        let to = Color32::from_rgba_unmultiplied(200, 100, 50, 255);
        assert_eq!(mix(transparent, to, 0.0), transparent);
        assert_eq!(mix(transparent, to, 1.0), to);
        // Opaque endpoints so `Color32`'s premultiplied accessors read back
        // the interpolated channels directly.
        let mid = mix(Color32::from_rgb(0, 0, 0), to, 0.5);
        assert_eq!(mid.r(), 100);
        assert_eq!(mid.g(), 50);
        assert_eq!(mid.a(), 255);
    }

    #[test]
    fn overlay_helpers_scale_with_alpha() {
        assert_eq!(super::overlay_fill(0.0).a(), 0);
        assert_eq!(super::overlay_fill(1.0).a(), 210);
        assert_eq!(super::overlay_outline(1.0).color.a(), 36);
        assert_eq!(super::text_primary_faded(1.0).a(), 220);
        assert_eq!(
            super::card_outline(1.0).color,
            palette::ACCENT_OUTLINE.to_opaque()
        );
    }
}
