use std::collections::HashMap;
use std::ffi::OsString;
use std::path::PathBuf;

use clap::{Arg, ArgAction, Command, Error, error::ErrorKind};

pub const DATA_DIR_ENV: &str = "DANMAKU_SERVER_DATA_DIR";
pub const WEB_UI_DIST_ENV: &str = "DANMAKU_WEB_UI_DIST";
pub const DEFAULT_PORT: u16 = 8_686;

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ServerOptions {
    pub data_directory: PathBuf,
    pub library_roots: Vec<PathBuf>,
    pub port: u16,
    pub pairing_token: Option<String>,
    pub web_assets_root: Option<PathBuf>,
    pub import_desktop_catalog: Option<PathBuf>,
}

impl ServerOptions {
    pub fn parse_from_process() -> std::result::Result<Self, Error> {
        let environment = std::env::vars().collect::<HashMap<_, _>>();
        Self::parse_from_env(std::env::args_os(), &environment)
    }

    pub fn parse_from_env<I, T>(
        args: I,
        environment: &HashMap<String, String>,
    ) -> std::result::Result<Self, Error>
    where
        I: IntoIterator<Item = T>,
        T: Into<OsString> + Clone,
    {
        let matches = command().try_get_matches_from(args)?;

        let data_directory = matches
            .get_one::<String>("data-dir")
            .map(|value| non_blank_path(value, "--data-dir"))
            .transpose()?
            .or_else(|| non_blank_env(environment, DATA_DIR_ENV).map(PathBuf::from))
            .unwrap_or_else(|| PathBuf::from("data").join("library-server"));

        let library_roots = matches
            .get_many::<String>("root")
            .into_iter()
            .flatten()
            .map(|value| non_blank_path(value, "--root"))
            .collect::<std::result::Result<Vec<_>, _>>()?;

        let port = matches
            .get_one::<u16>("port")
            .copied()
            .unwrap_or(DEFAULT_PORT);

        let pairing_token = matches
            .get_one::<String>("pairing-token")
            .map(|value| non_blank_string(value, "--pairing-token"))
            .transpose()?;

        let web_assets_root = matches
            .get_one::<String>("web-assets-dir")
            .map(|value| non_blank_path(value, "--web-assets-dir"))
            .transpose()?
            .or_else(|| non_blank_env(environment, WEB_UI_DIST_ENV).map(PathBuf::from));

        let import_desktop_catalog = matches
            .get_one::<String>("import-desktop-catalog")
            .map(|value| non_blank_path(value, "--import-desktop-catalog"))
            .transpose()?;

        Ok(Self {
            data_directory,
            library_roots,
            port,
            pairing_token,
            web_assets_root,
            import_desktop_catalog,
        })
    }
}

fn command() -> Command {
    Command::new("library-server")
        .version(env!("CARGO_PKG_VERSION"))
        .about("Danmaku headless library server")
        .arg(
            Arg::new("data-dir")
                .long("data-dir")
                .value_name("PATH")
                .num_args(1)
                .help("Data directory; overrides DANMAKU_SERVER_DATA_DIR"),
        )
        .arg(
            Arg::new("root")
                .long("root")
                .value_name("PATH")
                .num_args(1)
                .action(ArgAction::Append)
                .help("Library root to publish; may be repeated"),
        )
        .arg(
            Arg::new("port")
                .long("port")
                .value_name("PORT")
                .num_args(1)
                .value_parser(parse_port)
                .help("HTTP port to bind when serving is implemented"),
        )
        .arg(
            Arg::new("pairing-token")
                .long("pairing-token")
                .value_name("TOKEN")
                .num_args(1)
                .help("Pairing token to persist into server-settings.json"),
        )
        .arg(
            Arg::new("web-assets-dir")
                .long("web-assets-dir")
                .alias("web-ui-dist")
                .value_name("PATH")
                .num_args(1)
                .help("Web UI asset directory; overrides DANMAKU_WEB_UI_DIST"),
        )
        .arg(
            Arg::new("import-desktop-catalog")
                .long("import-desktop-catalog")
                .value_name("DB_COPY")
                .num_args(1)
                .help("Import a read-only copy of the desktop SQLite catalog, then exit"),
        )
}

fn parse_port(value: &str) -> std::result::Result<u16, String> {
    value
        .parse::<u32>()
        .ok()
        .filter(|port| *port <= u16::MAX as u32)
        .map(|port| port as u16)
        .ok_or_else(|| format!("Port must be between 0 and 65535: {value}"))
}

fn non_blank_env(environment: &HashMap<String, String>, name: &str) -> Option<String> {
    environment
        .get(name)
        .filter(|value| !value.trim().is_empty())
        .cloned()
}

fn non_blank_path(value: &str, option: &str) -> std::result::Result<PathBuf, Error> {
    non_blank_string(value, option).map(PathBuf::from)
}

fn non_blank_string(value: &str, option: &str) -> std::result::Result<String, Error> {
    if value.trim().is_empty() {
        Err(Error::raw(
            ErrorKind::ValueValidation,
            format!("{option} requires a value"),
        ))
    } else {
        Ok(value.to_owned())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_cli_and_environment_options() {
        let options = ServerOptions::parse_from_env(
            [
                "library-server",
                "--root=W:/Anime",
                "--port",
                "0",
                "--pairing-token=123456",
            ],
            &HashMap::from([
                (DATA_DIR_ENV.to_owned(), "S:/Danmaku/server-data".to_owned()),
                (WEB_UI_DIST_ENV.to_owned(), "apps/web-ui/dist".to_owned()),
            ]),
        )
        .expect("options should parse");

        assert_eq!(
            PathBuf::from("S:/Danmaku/server-data"),
            options.data_directory
        );
        assert_eq!(vec![PathBuf::from("W:/Anime")], options.library_roots);
        assert_eq!(0, options.port);
        assert_eq!(Some("123456".to_owned()), options.pairing_token);
        assert_eq!(
            Some(PathBuf::from("apps/web-ui/dist")),
            options.web_assets_root
        );
        assert_eq!(None, options.import_desktop_catalog);
    }

    #[test]
    fn cli_overrides_environment_fallbacks() {
        let options = ServerOptions::parse_from_env(
            [
                "library-server",
                "--data-dir=cli-data",
                "--web-assets-dir=cli-web",
            ],
            &HashMap::from([
                (DATA_DIR_ENV.to_owned(), "env-data".to_owned()),
                (WEB_UI_DIST_ENV.to_owned(), "env-web".to_owned()),
            ]),
        )
        .expect("options should parse");

        assert_eq!(PathBuf::from("cli-data"), options.data_directory);
        assert_eq!(Some(PathBuf::from("cli-web")), options.web_assets_root);
    }

    #[test]
    fn uses_jvm_defaults_when_cli_and_environment_are_absent() {
        let options = ServerOptions::parse_from_env(["library-server"], &HashMap::new())
            .expect("options should parse");

        assert_eq!(
            PathBuf::from("data").join("library-server"),
            options.data_directory
        );
        assert!(options.library_roots.is_empty());
        assert_eq!(DEFAULT_PORT, options.port);
        assert_eq!(None, options.pairing_token);
        assert_eq!(None, options.web_assets_root);
    }

    #[test]
    fn supports_legacy_web_ui_dist_alias() {
        let options = ServerOptions::parse_from_env(
            ["library-server", "--web-ui-dist=dist"],
            &HashMap::new(),
        )
        .expect("options should parse");

        assert_eq!(Some(PathBuf::from("dist")), options.web_assets_root);
    }

    #[test]
    fn parses_desktop_catalog_import_mode() {
        let options = ServerOptions::parse_from_env(
            [
                "library-server",
                "--data-dir=server-data",
                "--import-desktop-catalog=library-copy.db",
            ],
            &HashMap::new(),
        )
        .expect("options should parse");

        assert_eq!(PathBuf::from("server-data"), options.data_directory);
        assert_eq!(
            Some(PathBuf::from("library-copy.db")),
            options.import_desktop_catalog
        );
    }

    #[test]
    fn rejects_invalid_port() {
        let error =
            ServerOptions::parse_from_env(["library-server", "--port=70000"], &HashMap::new())
                .expect_err("invalid port should fail");

        assert!(
            error
                .to_string()
                .contains("Port must be between 0 and 65535")
        );
    }

    #[test]
    fn rejects_unknown_arguments() {
        let error = ServerOptions::parse_from_env(["library-server", "--unknown"], &HashMap::new())
            .expect_err("unknown argument should fail");

        assert!(error.to_string().contains("unexpected argument"));
    }

    #[test]
    fn rejects_blank_option_values() {
        let error =
            ServerOptions::parse_from_env(["library-server", "--pairing-token="], &HashMap::new())
                .expect_err("blank pairing token should fail");

        assert!(
            error
                .to_string()
                .contains("--pairing-token requires a value")
        );
    }
}
