use std::{ffi::OsString, time::Duration};

use crate::DEFAULT_MEDIA;

#[derive(Clone, Debug, PartialEq)]
pub struct Cli {
    pub media: String,
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
        let mut media = DEFAULT_MEDIA.to_owned();
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
                    let value = args
                        .next()
                        .ok_or_else(|| "--media requires a path or URL".to_owned())?
                        .into_string()
                        .map_err(|_| "--media must be valid UTF-8".to_owned())?;
                    media = value;
                }
                "--smoke" => {
                    let value = args
                        .next()
                        .ok_or_else(|| "--smoke requires a duration in seconds".to_owned())?
                        .into_string()
                        .map_err(|_| "--smoke must be valid UTF-8".to_owned())?;
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

        Ok(Self { media, smoke, help })
    }
}

pub fn usage() -> &'static str {
    "Usage: spike-egui-player [--media <path-or-url>] [--smoke <seconds>]"
}

#[cfg(test)]
mod tests {
    use super::Cli;
    use crate::DEFAULT_MEDIA;
    use std::time::Duration;

    #[test]
    fn applies_defaults() {
        let cli = Cli::parse_from(["spike-egui-player"]).expect("parse defaults");

        assert_eq!(cli.media, DEFAULT_MEDIA);
        assert_eq!(cli.smoke, None);
    }

    #[test]
    fn parses_media_and_smoke() {
        let cli = Cli::parse_from([
            "spike-egui-player",
            "--media",
            "sample.mkv",
            "--smoke",
            "2.5",
        ])
        .expect("parse args");

        assert_eq!(cli.media, "sample.mkv");
        assert_eq!(cli.smoke, Some(Duration::from_millis(2500)));
    }
}
