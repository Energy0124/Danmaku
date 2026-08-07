use library_server::{cli::ServerOptions, runtime::LoadedServer};

#[tokio::main]
async fn main() {
    let options = match ServerOptions::parse_from_process() {
        Ok(options) => options,
        Err(error) => error.exit(),
    };

    match LoadedServer::load(options) {
        Ok(server) => {
            let summary = server.startup_summary();
            for line in summary.to_log_lines() {
                println!("{line}");
            }
            let bound_server = match server.bind().await {
                Ok(bound_server) => bound_server,
                Err(error) => {
                    eprintln!("library-server startup failed: {error}");
                    std::process::exit(1);
                }
            };
            println!(
                "HTTP server listening on http://127.0.0.1:{}",
                bound_server.local_port()
            );
            if let Err(error) = bound_server
                .serve_until_shutdown(async {
                    let _ = tokio::signal::ctrl_c().await;
                })
                .await
            {
                eprintln!("library-server failed: {error}");
                std::process::exit(1);
            }
        }
        Err(error) => {
            eprintln!("library-server startup failed: {error}");
            std::process::exit(1);
        }
    }
}
