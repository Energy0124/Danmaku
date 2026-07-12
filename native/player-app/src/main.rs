use std::{
    process,
    sync::{Arc, Mutex},
};

use danmaku_player::{
    app::{PlayerApp, media_display_title},
    cli::{Cli, usage},
    smoke::SmokeReport,
};
use eframe::egui;

/// Decodes the bundled mascot icon for the window/taskbar. Downscaled to 256
/// so the in-memory RGBA stays small; the OS scales it per surface.
fn load_app_icon() -> Option<egui::IconData> {
    let image = image::load_from_memory(include_bytes!("../assets/app-icon.png"))
        .ok()?
        .resize_exact(256, 256, image::imageops::FilterType::Lanczos3)
        .into_rgba8();
    let (width, height) = image.dimensions();
    Some(egui::IconData {
        rgba: image.into_raw(),
        width,
        height,
    })
}

fn main() {
    let cli = match Cli::parse_env() {
        Ok(cli) => cli,
        Err(error) => {
            eprintln!("{error}");
            eprintln!("{}", usage());
            process::exit(2);
        }
    };
    if cli.help {
        println!("{}", usage());
        return;
    }

    let window_title = match (&cli.title, &cli.media) {
        (Some(title), _) => format!("Danmaku Player - {title}"),
        (None, Some(media)) => format!("Danmaku Player - {}", media_display_title(media)),
        (None, None) => "Danmaku Player".to_owned(),
    };
    let report_slot = Arc::new(Mutex::new(None));
    let app_cli = cli.clone();
    let app_report_slot = Arc::clone(&report_slot);
    let mut viewport = egui::ViewportBuilder::default()
        .with_inner_size([1280.0, 720.0])
        .with_min_inner_size([960.0, 600.0])
        .with_decorations(false)
        .with_title(window_title.clone());
    if let Some(icon) = load_app_icon() {
        viewport = viewport.with_icon(Arc::new(icon));
    }
    let native_options = eframe::NativeOptions {
        renderer: eframe::Renderer::Glow,
        viewport,
        vsync: true,
        ..Default::default()
    };

    let run_result = eframe::run_native(
        &window_title,
        native_options,
        Box::new(move |creation_context| {
            danmaku_player::theme::apply(&creation_context.egui_ctx);
            PlayerApp::new(
                creation_context,
                app_cli.clone(),
                Arc::clone(&app_report_slot),
            )
            .map(|app| Box::new(app) as Box<dyn eframe::App>)
        }),
    );

    let smoke_media = cli.media.clone().unwrap_or_default();
    if let Err(error) = run_result {
        if let Some(duration) = cli.smoke {
            let report = SmokeReport::fail(&smoke_media, duration, format!("app failed: {error}"));
            println!("{report}");
            process::exit(report.exit_code());
        }
        eprintln!("app failed: {error}");
        process::exit(1);
    }

    if let Some(duration) = cli.smoke {
        let report = report_slot
            .lock()
            .ok()
            .and_then(|mut slot| slot.take())
            .unwrap_or_else(|| {
                SmokeReport::fail(&smoke_media, duration, "app closed before smoke completed")
            });
        println!("{report}");
        process::exit(report.exit_code());
    }
}
