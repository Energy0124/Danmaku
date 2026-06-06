fn main() {
    if let Err(error) = run() {
        eprintln!("libmpv probe failed: {error}");
        std::process::exit(1);
    }
}

fn run() -> Result<(), Box<dyn std::error::Error>> {
    use player_windows_mpv::{MpvLibrary, find_library_for_current_process};

    let path = find_library_for_current_process()?;
    let library = MpvLibrary::load(&path)?;

    println!("loaded {}", library.loaded_path().display());
    println!("client API version: {}", library.client_api_version());

    let _mpv = library.create()?;
    println!("mpv context initialized successfully");

    Ok(())
}
