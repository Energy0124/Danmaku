//! Small code-native icon set used by the egui shell.
//!
//! Icons are painted from geometry instead of font glyphs so they render
//! consistently with the bundled CJK font on every Windows installation.

use eframe::egui::{self, Align2, Color32, FontId, Painter, Rect, Stroke, StrokeKind, pos2, vec2};

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum Icon {
    Home,
    Library,
    Search,
    Refresh,
    Settings,
    Power,
    Back,
    Previous,
    Next,
    Replay10,
    Forward30,
    Play,
    Pause,
    Volume,
    Muted,
    Fullscreen,
    Folder,
    Swap,
    Minimize,
    Maximize,
    Close,
    Subtitles,
    AudioTrack,
    Danmaku,
    Globe,
    Check,
}

pub fn paint_icon(painter: &Painter, rect: Rect, icon: Icon, color: Color32, width: f32) {
    let stroke = Stroke::new(width, color);
    let center = rect.center();
    let left = rect.left();
    let top = rect.top();
    let x = |fraction: f32| left + rect.width() * fraction;
    let y = |fraction: f32| top + rect.height() * fraction;

    match icon {
        Icon::Home => {
            painter.line(
                vec![
                    pos2(x(0.18), y(0.48)),
                    pos2(x(0.50), y(0.20)),
                    pos2(x(0.82), y(0.48)),
                ],
                stroke,
            );
            painter.rect_stroke(
                Rect::from_min_max(pos2(x(0.27), y(0.45)), pos2(x(0.73), y(0.80))),
                1.5,
                stroke,
                StrokeKind::Inside,
            );
            painter.line_segment([pos2(x(0.47), y(0.80)), pos2(x(0.47), y(0.62))], stroke);
        }
        Icon::Library => {
            for (index, offset) in [0.20, 0.42, 0.64].into_iter().enumerate() {
                let book = Rect::from_min_max(
                    pos2(x(offset), y(0.22 + index as f32 * 0.015)),
                    pos2(x(offset + 0.16), y(0.80)),
                );
                painter.rect_stroke(book, 1.5, stroke, StrokeKind::Inside);
                painter.line_segment(
                    [pos2(book.left(), y(0.66)), pos2(book.right(), y(0.66))],
                    Stroke::new(width * 0.75, color),
                );
            }
        }
        Icon::Search => {
            let radius = rect.width().min(rect.height()) * 0.25;
            let lens = center - vec2(radius * 0.25, radius * 0.25);
            painter.circle_stroke(lens, radius, stroke);
            painter.line_segment(
                [
                    lens + vec2(radius * 0.68, radius * 0.68),
                    pos2(x(0.80), y(0.80)),
                ],
                stroke,
            );
        }
        Icon::Refresh => {
            // 300-degree arc with an arrowhead at its open end.
            let radius = rect.width().min(rect.height()) * 0.30;
            let mut arc = Vec::with_capacity(25);
            for step in 0..=24 {
                let angle = -std::f32::consts::FRAC_PI_2
                    + std::f32::consts::TAU * (step as f32 / 24.0) * (300.0 / 360.0);
                arc.push(center + vec2(angle.cos(), angle.sin()) * radius);
            }
            painter.line(arc, stroke);
            let tip_angle = -std::f32::consts::FRAC_PI_2;
            let tip = center + vec2(tip_angle.cos(), tip_angle.sin()) * radius;
            painter.add(egui::Shape::convex_polygon(
                vec![
                    tip + vec2(-radius * 0.42, -radius * 0.38),
                    tip + vec2(radius * 0.40, -radius * 0.18),
                    tip + vec2(-radius * 0.10, radius * 0.45),
                ],
                color,
                Stroke::NONE,
            ));
        }
        Icon::Settings => {
            painter.circle_stroke(center, rect.width().min(rect.height()) * 0.16, stroke);
            let radius = rect.width().min(rect.height()) * 0.34;
            for index in 0..8 {
                let angle = index as f32 * std::f32::consts::TAU / 8.0;
                let direction = vec2(angle.cos(), angle.sin());
                painter.line_segment(
                    [
                        center + direction * radius * 0.72,
                        center + direction * radius,
                    ],
                    stroke,
                );
            }
        }
        Icon::Power => {
            painter.circle_stroke(
                center + vec2(0.0, rect.height() * 0.05),
                rect.width() * 0.28,
                stroke,
            );
            painter.line_segment(
                [pos2(center.x, y(0.12)), pos2(center.x, y(0.52))],
                Stroke::new(width + 0.8, color),
            );
        }
        Icon::Check => {
            painter.line_segment([pos2(x(0.20), y(0.55)), pos2(x(0.42), y(0.76))], stroke);
            painter.line_segment([pos2(x(0.42), y(0.76)), pos2(x(0.80), y(0.28))], stroke);
        }
        Icon::Back => {
            painter.line_segment([pos2(x(0.78), y(0.50)), pos2(x(0.22), y(0.50))], stroke);
            painter.line_segment([pos2(x(0.22), y(0.50)), pos2(x(0.46), y(0.27))], stroke);
            painter.line_segment([pos2(x(0.22), y(0.50)), pos2(x(0.46), y(0.73))], stroke);
        }
        Icon::Previous => {
            painter.line_segment([pos2(x(0.22), y(0.24)), pos2(x(0.22), y(0.76))], stroke);
            painter.add(egui::Shape::convex_polygon(
                vec![
                    pos2(x(0.70), y(0.22)),
                    pos2(x(0.70), y(0.78)),
                    pos2(x(0.30), y(0.50)),
                ],
                color,
                Stroke::NONE,
            ));
        }
        Icon::Next => {
            painter.line_segment([pos2(x(0.78), y(0.24)), pos2(x(0.78), y(0.76))], stroke);
            painter.add(egui::Shape::convex_polygon(
                vec![
                    pos2(x(0.30), y(0.22)),
                    pos2(x(0.30), y(0.78)),
                    pos2(x(0.70), y(0.50)),
                ],
                color,
                Stroke::NONE,
            ));
        }
        Icon::Replay10 | Icon::Forward30 => {
            painter.circle_stroke(center, rect.width().min(rect.height()) * 0.30, stroke);
            let forward = icon == Icon::Forward30;
            let arrow_x = if forward { x(0.78) } else { x(0.22) };
            painter.add(egui::Shape::convex_polygon(
                if forward {
                    vec![
                        pos2(arrow_x, y(0.22)),
                        pos2(x(0.60), y(0.18)),
                        pos2(x(0.68), y(0.38)),
                    ]
                } else {
                    vec![
                        pos2(arrow_x, y(0.22)),
                        pos2(x(0.40), y(0.18)),
                        pos2(x(0.32), y(0.38)),
                    ]
                },
                color,
                Stroke::NONE,
            ));
            painter.text(
                center,
                Align2::CENTER_CENTER,
                if forward { "30" } else { "10" },
                FontId::proportional(rect.height() * 0.26),
                color,
            );
        }
        Icon::Play => {
            painter.add(egui::Shape::convex_polygon(
                vec![
                    pos2(x(0.36), y(0.24)),
                    pos2(x(0.36), y(0.76)),
                    pos2(x(0.76), y(0.50)),
                ],
                color,
                Stroke::NONE,
            ));
        }
        Icon::Pause => {
            painter.rect_filled(
                Rect::from_min_max(pos2(x(0.30), y(0.24)), pos2(x(0.43), y(0.76))),
                1.5,
                color,
            );
            painter.rect_filled(
                Rect::from_min_max(pos2(x(0.57), y(0.24)), pos2(x(0.70), y(0.76))),
                1.5,
                color,
            );
        }
        Icon::Volume | Icon::Muted => {
            painter.add(egui::Shape::convex_polygon(
                vec![
                    pos2(x(0.18), y(0.42)),
                    pos2(x(0.36), y(0.42)),
                    pos2(x(0.58), y(0.24)),
                    pos2(x(0.58), y(0.76)),
                    pos2(x(0.36), y(0.58)),
                    pos2(x(0.18), y(0.58)),
                ],
                color,
                Stroke::NONE,
            ));
            if icon == Icon::Muted {
                painter.line_segment([pos2(x(0.68), y(0.36)), pos2(x(0.86), y(0.64))], stroke);
                painter.line_segment([pos2(x(0.86), y(0.36)), pos2(x(0.68), y(0.64))], stroke);
            } else {
                painter.line_segment([pos2(x(0.68), y(0.38)), pos2(x(0.78), y(0.50))], stroke);
                painter.line_segment([pos2(x(0.78), y(0.50)), pos2(x(0.68), y(0.62))], stroke);
            }
        }
        Icon::Fullscreen => {
            let segments = [
                (
                    pos2(x(0.18), y(0.38)),
                    pos2(x(0.18), y(0.18)),
                    pos2(x(0.38), y(0.18)),
                ),
                (
                    pos2(x(0.62), y(0.18)),
                    pos2(x(0.82), y(0.18)),
                    pos2(x(0.82), y(0.38)),
                ),
                (
                    pos2(x(0.18), y(0.62)),
                    pos2(x(0.18), y(0.82)),
                    pos2(x(0.38), y(0.82)),
                ),
                (
                    pos2(x(0.62), y(0.82)),
                    pos2(x(0.82), y(0.82)),
                    pos2(x(0.82), y(0.62)),
                ),
            ];
            for (a, b, c) in segments {
                painter.line(vec![a, b, c], stroke);
            }
        }
        Icon::Folder => {
            let folder = Rect::from_min_max(pos2(x(0.16), y(0.34)), pos2(x(0.84), y(0.78)));
            painter.rect_stroke(folder, 2.0, stroke, StrokeKind::Inside);
            painter.line(
                vec![
                    pos2(x(0.18), y(0.34)),
                    pos2(x(0.35), y(0.34)),
                    pos2(x(0.43), y(0.24)),
                    pos2(x(0.62), y(0.24)),
                    pos2(x(0.68), y(0.34)),
                ],
                stroke,
            );
        }
        Icon::Swap => {
            painter.line_segment([pos2(x(0.18), y(0.36)), pos2(x(0.78), y(0.36))], stroke);
            painter.line_segment([pos2(x(0.78), y(0.36)), pos2(x(0.66), y(0.24))], stroke);
            painter.line_segment([pos2(x(0.22), y(0.64)), pos2(x(0.82), y(0.64))], stroke);
            painter.line_segment([pos2(x(0.22), y(0.64)), pos2(x(0.34), y(0.76))], stroke);
        }
        Icon::Minimize => {
            painter.line_segment([pos2(x(0.24), y(0.68)), pos2(x(0.76), y(0.68))], stroke);
        }
        Icon::Maximize => {
            painter.rect_stroke(
                Rect::from_min_max(pos2(x(0.24), y(0.24)), pos2(x(0.76), y(0.76))),
                0.0,
                stroke,
                StrokeKind::Inside,
            );
        }
        Icon::Close => {
            painter.line_segment([pos2(x(0.26), y(0.26)), pos2(x(0.74), y(0.74))], stroke);
            painter.line_segment([pos2(x(0.74), y(0.26)), pos2(x(0.26), y(0.74))], stroke);
        }
        Icon::Subtitles => {
            painter.rect_stroke(
                Rect::from_min_max(pos2(x(0.14), y(0.26)), pos2(x(0.86), y(0.74))),
                3.0,
                stroke,
                StrokeKind::Inside,
            );
            painter.line_segment([pos2(x(0.24), y(0.52)), pos2(x(0.48), y(0.52))], stroke);
            painter.line_segment([pos2(x(0.56), y(0.52)), pos2(x(0.76), y(0.52))], stroke);
            painter.line_segment([pos2(x(0.24), y(0.63)), pos2(x(0.40), y(0.63))], stroke);
            painter.line_segment([pos2(x(0.48), y(0.63)), pos2(x(0.76), y(0.63))], stroke);
        }
        Icon::AudioTrack => {
            painter.line_segment(
                [pos2(x(0.60), y(0.20)), pos2(x(0.60), y(0.66))],
                Stroke::new(width, color),
            );
            painter.line_segment([pos2(x(0.60), y(0.20)), pos2(x(0.80), y(0.30))], stroke);
            painter.circle_filled(
                pos2(x(0.52), y(0.68)),
                rect.width().min(rect.height()) * 0.14,
                color,
            );
        }
        Icon::Danmaku => {
            // Speech bubble with flowing comment dashes.
            painter.rect_stroke(
                Rect::from_min_max(pos2(x(0.14), y(0.22)), pos2(x(0.86), y(0.66))),
                4.0,
                stroke,
                StrokeKind::Inside,
            );
            painter.add(egui::Shape::convex_polygon(
                vec![
                    pos2(x(0.30), y(0.66)),
                    pos2(x(0.46), y(0.66)),
                    pos2(x(0.30), y(0.82)),
                ],
                color,
                Stroke::NONE,
            ));
            painter.line_segment([pos2(x(0.26), y(0.37)), pos2(x(0.58), y(0.37))], stroke);
            painter.line_segment([pos2(x(0.42), y(0.51)), pos2(x(0.74), y(0.51))], stroke);
        }
        Icon::Globe => {
            let radius = rect.width().min(rect.height()) * 0.34;
            painter.circle_stroke(center, radius, stroke);
            painter.line_segment(
                [center - vec2(radius, 0.0), center + vec2(radius, 0.0)],
                stroke,
            );
            // Meridian ellipse approximated with two arcs of points.
            let mut left_arc = Vec::with_capacity(17);
            let mut right_arc = Vec::with_capacity(17);
            for step in 0..=16 {
                let angle = std::f32::consts::PI * step as f32 / 16.0;
                let sine = angle.sin();
                let cosine = angle.cos();
                left_arc.push(center + vec2(-radius * 0.45 * sine, -radius * cosine));
                right_arc.push(center + vec2(radius * 0.45 * sine, -radius * cosine));
            }
            painter.line(left_arc, stroke);
            painter.line(right_arc, stroke);
        }
    }
}
