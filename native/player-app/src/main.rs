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

    let window_title = format!(
        "Danmaku Player - {}",
        cli.title
            .clone()
            .unwrap_or_else(|| media_display_title(&cli.media))
    );
    let report_slot = Arc::new(Mutex::new(None));
    let app_cli = cli.clone();
    let app_report_slot = Arc::clone(&report_slot);
    let native_options = eframe::NativeOptions {
        renderer: eframe::Renderer::Glow,
        viewport: egui::ViewportBuilder::default()
            .with_inner_size([1280.0, 720.0])
            .with_min_inner_size([640.0, 360.0])
            .with_title(window_title.clone()),
        vsync: true,
        ..Default::default()
    };

    let run_result = eframe::run_native(
        &window_title,
        native_options,
        Box::new(move |creation_context| {
            PlayerApp::configure_fonts(&creation_context.egui_ctx);
            PlayerApp::configure_style(&creation_context.egui_ctx);
            PlayerApp::new(
                creation_context,
                app_cli.clone(),
                Arc::clone(&app_report_slot),
            )
            .map(|app| Box::new(app) as Box<dyn eframe::App>)
        }),
    );

    if let Err(error) = run_result {
        if let Some(duration) = cli.smoke {
            let report = SmokeReport::fail(&cli.media, duration, format!("app failed: {error}"));
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
                SmokeReport::fail(&cli.media, duration, "app closed before smoke completed")
            });
        println!("{report}");
        process::exit(report.exit_code());
    }
}
