//! Command-line parsing for the native player.

use std::{ffi::OsString, time::Duration};

#[derive(Clone, Debug, PartialEq)]
pub struct Cli {
    pub media: String,
    pub title: Option<String>,
    pub start_position_s: Option<f64>,
    pub volume_percent: Option<u8>,
    pub smoke: Option<Duration>,
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
                    let percent = value
                        .parse::<u8>()
                        .ok()
                        .filter(|percent| *percent <= 130)
                        .ok_or_else(|| format!("invalid --volume (0-130): {value}"))?;
                    volume_percent = Some(percent);
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
                "--help" | "-h" => help = true,
                unknown => return Err(format!("unknown argument: {unknown}")),
            }
        }

        if help {
            return Ok(Self {
                media: String::new(),
                title,
                start_position_s,
                volume_percent,
                smoke,
                help,
            });
        }
        let media = media.ok_or_else(|| "--media <path-or-url> is required".to_owned())?;
        Ok(Self {
            media,
            title,
            start_position_s,
            volume_percent,
            smoke,
            help,
        })
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

pub fn usage() -> &'static str {
    "Usage: danmaku-player --media <path-or-url> [--title <text>] [--start <seconds>] \
[--volume <0-130>] [--smoke <seconds>]"
}

#[cfg(test)]
mod tests {
    use super::Cli;
    use std::time::Duration;

    #[test]
    fn requires_media() {
        let error = Cli::parse_from(["danmaku-player"]).expect_err("media should be required");

        assert!(error.contains("--media"));
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
            "--smoke",
            "2.5",
        ])
        .expect("parse args");

        assert_eq!(cli.media, "sample.mkv");
        assert_eq!(cli.title.as_deref(), Some("Episode 01"));
        assert_eq!(cli.start_position_s, Some(42.5));
        assert_eq!(cli.volume_percent, Some(85));
        assert_eq!(cli.smoke, Some(Duration::from_millis(2500)));
    }

    #[test]
    fn rejects_out_of_range_volume() {
        let error = Cli::parse_from(["danmaku-player", "--media", "a.mkv", "--volume", "200"])
            .expect_err("volume must be rejected");

        assert!(error.contains("--volume"));
    }

    #[test]
    fn help_does_not_require_media() {
        let cli = Cli::parse_from(["danmaku-player", "--help"]).expect("help parses");

        assert!(cli.help);
    }
}
