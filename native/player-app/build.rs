//! Embeds the Windows application icon into the executable so Explorer, the
//! taskbar, and Alt-Tab show the mascot even before the app sets its runtime
//! window icon. No-op on non-Windows targets, and non-fatal if the platform
//! resource compiler is unavailable.

fn main() {
    println!("cargo:rerun-if-changed=assets/app.ico");
    println!("cargo:rerun-if-changed=build.rs");

    if std::env::var("CARGO_CFG_TARGET_OS").as_deref() != Ok("windows") {
        return;
    }

    let mut resource = winresource::WindowsResource::new();
    resource.set_icon("assets/app.ico");
    if let Err(error) = resource.compile() {
        // A missing resource compiler must not fail the build; the runtime
        // window icon still applies. Surface the reason as a warning.
        println!("cargo:warning=app icon resource not embedded: {error}");
    }
}
