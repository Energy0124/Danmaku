use library_server::{cli::ServerOptions, run_foundations};

fn main() {
    let options = match ServerOptions::parse_from_process() {
        Ok(options) => options,
        Err(error) => error.exit(),
    };

    match run_foundations(options) {
        Ok(summary) => {
            for line in summary.to_log_lines() {
                println!("{line}");
            }
            println!(
                "HTTP serving is not yet implemented in this Phase 1 foundations build; \
                 startup artifacts were loaded successfully and the process is exiting cleanly."
            );
        }
        Err(error) => {
            eprintln!("library-server startup failed: {error}");
            std::process::exit(1);
        }
    }
}
