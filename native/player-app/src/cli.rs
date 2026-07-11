//! Command-line parsing for the native player.

use std::{ffi::OsString, path::PathBuf, time::Duration};

use crate::danmaku::DanmakuDisplaySettings;

#[derive(Clone, Debug, PartialEq)]
pub struct Cli {
    /// Direct media path/URL; when absent the player starts in library mode
    /// (connect screen, or straight to the library when `--server-url` is
    /// given).
    pub media: Option<String>,
    pub title: Option<String>,
    pub start_position_s: Option<f64>,
    pub volume_percent: Option<u8>,
    pub smoke: Option<Duration>,
    pub danmaku_path: Option<PathBuf>,
    pub server_url: Option<String>,
    pub pairing_token: Option<String>,
    pub media_id: Option<String>,
    pub auto_next: bool,
    /// QA hook: in library mode, plays the first catalog item as soon as
    /// the catalog loads (mirrors the desktop app's autoplay QA hooks).
    pub qa_play_first: bool,
    pub danmaku_force_refresh: bool,
    pub danmaku_opacity: Option<f32>,
    pub danmaku_speed: Option<f32>,
    pub danmaku_density: Option<f32>,
    pub danmaku_lanes: Option<usize>,
    pub help: bool,
}

impl Cli {
    pub fn parse_env() -> Result<Self, String> {
        Self::parse_from(std::env::args_os())
    }

    pub fn parse_from<I, S>(args: I) -> Result<Self, String>
    where
        I: IntoIterator<Item = S>,
        S: Into<OsString>,
    {
        let mut media = None;
        let mut title = None;
        let mut start_position_s = None;
        let mut volume_percent = None;
        let mut smoke = None;
        let mut danmaku_path = None;
        let mut server_url = None;
        let mut pairing_token = None;
        let mut media_id = None;
        let mut auto_next = false;
        let mut qa_play_first = false;
        let mut danmaku_force_refresh = false;
        let mut danmaku_opacity = None;
        let mut danmaku_speed = None;
        let mut danmaku_density = None;
        let mut danmaku_lanes = None;
        let mut help = false;
        let mut args = args.into_iter().map(Into::into);
        let _program = args.next();

        while let Some(arg) = args.next() {
            let arg = arg
                .into_string()
                .map_err(|_| "arguments must be valid UTF-8".to_owned())?;
            match arg.as_str() {
                "--media" => {
                    media = Some(next_string(&mut args, "--media requires a path or URL")?);
                }
                "--title" => {
                    title = Some(next_string(&mut args, "--title requires a value")?);
                }
                "--start" => {
                    let value = next_string(&mut args, "--start requires seconds")?;
                    let seconds = value
                        .parse::<f64>()
                        .map_err(|_| format!("invalid --start position: {value}"))?;
                    if !seconds.is_finite() || seconds < 0.0 {
                        return Err("--start must be a non-negative number of seconds".to_owned());
                    }
                    start_position_s = Some(seconds);
                }
                "--volume" => {
                    let value = next_string(&mut args, "--volume requires a percentage")?;
                    volume_percent = Some(
                        value
                            .parse::<u8>()
                            .ok()
                            .filter(|percent| *percent <= 130)
                            .ok_or_else(|| format!("invalid --volume (0-130): {value}"))?,
                    );
                }
                "--smoke" => {
                    let value = next_string(&mut args, "--smoke requires a duration in seconds")?;
                    let seconds = value
                        .parse::<f64>()
                        .map_err(|_| format!("invalid --smoke duration: {value}"))?;
                    if !seconds.is_finite() || seconds <= 0.0 {
                        return Err("--smoke duration must be a positive number".to_owned());
                    }
                    smoke = Some(Duration::from_secs_f64(seconds));
                }
                "--danmaku" => {
                    danmaku_path = Some(PathBuf::from(next_string(
                        &mut args,
                        "--danmaku requires an XML, JSON, or ASS path",
                    )?));
                }
                "--server-url" => {
                    server_url = Some(next_string(
                        &mut args,
                        "--server-url requires an http:// URL",
                    )?);
                }
                "--media-id" => {
                    media_id = Some(next_string(&mut args, "--media-id requires a value")?);
                }
                "--pairing-token" => {
                    pairing_token =
                        Some(next_string(&mut args, "--pairing-token requires a value")?);
                }
                "--auto-next" => auto_next = true,
                "--qa-play-first" => qa_play_first = true,
                "--danmaku-force-refresh" => danmaku_force_refresh = true,
                "--danmaku-opacity" => {
                    danmaku_opacity = Some(parse_f32_range(
                        next_string(&mut args, "--danmaku-opacity requires 0-1")?,
                        "--danmaku-opacity",
                        0.0,
                        1.0,
                    )?);
                }
                "--danmaku-speed" => {
                    danmaku_speed = Some(parse_f32_range(
                        next_string(&mut args, "--danmaku-speed requires 0.25-4")?,
                        "--danmaku-speed",
                        0.25,
                        4.0,
                    )?);
                }
                "--danmaku-density" => {
                    danmaku_density = Some(parse_f32_range(
                        next_string(&mut args, "--danmaku-density requires 0-1")?,
                        "--danmaku-density",
                        0.0,
                        1.0,
                    )?);
                }
                "--danmaku-lanes" => {
                    let value = next_string(&mut args, "--danmaku-lanes requires 1-64")?;
                    danmaku_lanes = Some(
                        value
                            .parse::<usize>()
                            .ok()
                            .filter(|lanes| (1..=64).contains(lanes))
                            .ok_or_else(|| format!("invalid --danmaku-lanes (1-64): {value}"))?,
                    );
                }
                "--help" | "-h" => help = true,
                unknown => return Err(format!("unknown argument: {unknown}")),
            }
        }

        let cli = Self {
            media,
            title,
            start_position_s,
            volume_percent,
            smoke,
            danmaku_path,
            server_url,
            pairing_token,
            media_id,
            auto_next,
            qa_play_first,
            danmaku_force_refresh,
            danmaku_opacity,
            danmaku_speed,
            danmaku_density,
            danmaku_lanes,
            help,
        };
        if cli.help {
            return Ok(cli);
        }
        if cli.qa_play_first && (cli.media.is_some() || cli.server_url.is_none()) {
            return Err("--qa-play-first requires --server-url library mode".to_owned());
        }
        if cli.media.is_some() {
            if cli.danmaku_path.is_some() && (cli.server_url.is_some() || cli.media_id.is_some()) {
                return Err("--danmaku cannot be combined with --server-url/--media-id".to_owned());
            }
            if cli.server_url.is_some() != cli.media_id.is_some() {
                return Err("--server-url and --media-id must be provided together".to_owned());
            }
            if cli.danmaku_force_refresh && cli.server_url.is_none() {
                return Err(
                    "--danmaku-force-refresh requires --server-url and --media-id".to_owned(),
                );
            }
        } else {
            // Library mode: the server drives media selection.
            if cli.media_id.is_some() {
                return Err("--media-id requires --media".to_owned());
            }
            if cli.danmaku_path.is_some() {
                return Err("--danmaku requires --media".to_owned());
            }
            if cli.start_position_s.is_some() {
                return Err("--start requires --media".to_owned());
            }
            if cli.smoke.is_some() {
                return Err("--smoke requires --media".to_owned());
            }
        }
        Ok(cli)
    }

    pub fn danmaku_display_settings(&self) -> DanmakuDisplaySettings {
        let defaults = DanmakuDisplaySettings::default();
        DanmakuDisplaySettings {
            enabled: defaults.enabled,
            opacity: self.danmaku_opacity.unwrap_or(defaults.opacity),
            speed: self.danmaku_speed.unwrap_or(defaults.speed),
            density: self.danmaku_density.unwrap_or(defaults.density),
            max_lanes: self.danmaku_lanes.unwrap_or(defaults.max_lanes),
        }
        .sanitized()
    }
}

fn next_string(
    args: &mut impl Iterator<Item = OsString>,
    missing_message: &str,
) -> Result<String, String> {
    args.next()
        .ok_or_else(|| missing_message.to_owned())?
        .into_string()
        .map_err(|_| "arguments must be valid UTF-8".to_owned())
}

fn parse_f32_range(value: String, option: &str, minimum: f32, maximum: f32) -> Result<f32, String> {
    value
        .parse::<f32>()
        .ok()
        .filter(|value| value.is_finite() && (minimum..=maximum).contains(value))
        .ok_or_else(|| format!("invalid {option} ({minimum}-{maximum}): {value}"))
}

pub fn usage() -> &'static str {
    "Usage: danmaku-player [--media <path-or-url>] [--title <text>] [--start <seconds>] \
[--volume <0-130>] [--danmaku <xml-json-ass>] \
[--server-url <http-url> [--pairing-token <token>]] [--media-id <id>] \
[--auto-next] [--danmaku-force-refresh] \
[--danmaku-opacity <0-1>] [--danmaku-speed <0.25-4>] \
[--danmaku-density <0-1>] [--danmaku-lanes <1-64>] [--smoke <seconds>]

Without --media the player starts in library mode: --server-url connects
directly, otherwise the connect screen lists LAN-discovered servers."
}

#[cfg(test)]
mod tests {
    use super::Cli;
    use std::{path::PathBuf, time::Duration};

    #[test]
    fn no_arguments_selects_library_connect_mode() {
        let cli = Cli::parse_from(["danmaku-player"]).expect("library mode parses");
        assert_eq!(cli.media, None);
        assert_eq!(cli.server_url, None);
    }

    #[test]
    fn server_url_without_media_selects_library_mode() {
        let cli = Cli::parse_from([
            "danmaku-player",
            "--server-url",
            "http://127.0.0.1:8686",
            "--pairing-token",
            "123456",
            "--auto-next",
        ])
        .expect("library mode parses");

        assert_eq!(cli.media, None);
        assert_eq!(cli.server_url.as_deref(), Some("http://127.0.0.1:8686"));
        assert_eq!(cli.pairing_token.as_deref(), Some("123456"));
        assert!(cli.auto_next);
    }

    #[test]
    fn direct_playback_options_require_media() {
        for arguments in [
            vec!["danmaku-player", "--media-id", "id"],
            vec!["danmaku-player", "--danmaku", "comments.xml"],
            vec!["danmaku-player", "--start", "10"],
            vec!["danmaku-player", "--smoke", "5"],
        ] {
            let error = Cli::parse_from(arguments).expect_err("must require --media");
            assert!(error.contains("--media"), "unexpected error: {error}");
        }
    }

    #[test]
    fn parses_all_options() {
        let cli = Cli::parse_from([
            "danmaku-player",
            "--media",
            "sample.mkv",
            "--title",
            "Episode 01",
            "--start",
            "42.5",
            "--volume",
            "85",
            "--danmaku",
            "comments.xml",
            "--danmaku-opacity",
            "0.75",
            "--danmaku-speed",
            "1.5",
            "--danmaku-density",
            "0.5",
            "--danmaku-lanes",
            "8",
            "--smoke",
            "2.5",
        ])
        .expect("parse args");

        assert_eq!(cli.media.as_deref(), Some("sample.mkv"));
        assert_eq!(cli.title.as_deref(), Some("Episode 01"));
        assert_eq!(cli.start_position_s, Some(42.5));
        assert_eq!(cli.volume_percent, Some(85));
        assert_eq!(cli.danmaku_path, Some(PathBuf::from("comments.xml")));
        assert_eq!(cli.danmaku_opacity, Some(0.75));
        assert_eq!(cli.danmaku_speed, Some(1.5));
        assert_eq!(cli.danmaku_density, Some(0.5));
        assert_eq!(cli.danmaku_lanes, Some(8));
        assert_eq!(cli.smoke, Some(Duration::from_millis(2500)));
    }

    #[test]
    fn parses_server_danmaku_options() {
        let cli = Cli::parse_from([
            "danmaku-player",
            "--media",
            "sample.mkv",
            "--server-url",
            "http://127.0.0.1:8686",
            "--media-id",
            "episode-id",
            "--danmaku-force-refresh",
        ])
        .expect("server options");

        assert_eq!(cli.server_url.as_deref(), Some("http://127.0.0.1:8686"));
        assert_eq!(cli.media_id.as_deref(), Some("episode-id"));
        assert!(cli.danmaku_force_refresh);
    }

    #[test]
    fn rejects_incomplete_or_conflicting_sources() {
        let incomplete = Cli::parse_from([
            "danmaku-player",
            "--media",
            "a.mkv",
            "--server-url",
            "http://127.0.0.1:8686",
        ])
        .expect_err("media ID must be required");
        assert!(incomplete.contains("--media-id"));

        let conflict = Cli::parse_from([
            "danmaku-player",
            "--media",
            "a.mkv",
            "--danmaku",
            "comments.xml",
            "--server-url",
            "http://127.0.0.1:8686",
            "--media-id",
            "id",
        ])
        .expect_err("sources must be exclusive");
        assert!(conflict.contains("cannot be combined"));
    }

    #[test]
    fn rejects_out_of_range_values() {
        let volume = Cli::parse_from(["danmaku-player", "--media", "a.mkv", "--volume", "200"])
            .expect_err("volume must be rejected");
        assert!(volume.contains("--volume"));

        let density = Cli::parse_from([
            "danmaku-player",
            "--media",
            "a.mkv",
            "--danmaku-density",
            "1.5",
        ])
        .expect_err("density must be rejected");
        assert!(density.contains("--danmaku-density"));
    }

    #[test]
    fn help_does_not_require_media() {
        let cli = Cli::parse_from(["danmaku-player", "--help"]).expect("help parses");
        assert!(cli.help);
    }
}
