//! UDP discovery of LAN library servers.
//!
//! Servers broadcast a JSON announcement every 1.5s on UDP port 8687 (see
//! docs/lan-protocol.md). Default fields are omitted on the wire, so the
//! minimal packet is `{"port":8686}`.

use std::{
    collections::HashMap,
    net::{IpAddr, UdpSocket},
    sync::{
        Arc, Mutex,
        atomic::{AtomicBool, Ordering},
    },
    thread::JoinHandle,
    time::{Duration, Instant},
};

pub const DEFAULT_DISCOVERY_PORT: u16 = 8_687;
const ANNOUNCE_TTL: Duration = Duration::from_secs(6);
const RECEIVE_TIMEOUT: Duration = Duration::from_millis(400);

/// Parses one announcement datagram per the frozen protocol: optional
/// `protocol` must be `danmaku-library`, optional `version` must be `1`,
/// and `port` must be a valid TCP port.
pub fn parse_announcement(payload: &[u8]) -> Result<u16, String> {
    let root: serde_json::Value = serde_json::from_slice(payload)
        .map_err(|error| format!("invalid announcement JSON: {error}"))?;
    if let Some(protocol) = root.get("protocol")
        && protocol.as_str() != Some("danmaku-library")
    {
        return Err("unsupported discovery protocol".to_owned());
    }
    if let Some(version) = root.get("version")
        && version.as_i64() != Some(1)
    {
        return Err("unsupported discovery version".to_owned());
    }
    let port = root
        .get("port")
        .and_then(serde_json::Value::as_i64)
        .ok_or_else(|| "announcement is missing a port".to_owned())?;
    u16::try_from(port)
        .ok()
        .filter(|port| *port > 0)
        .ok_or_else(|| "announcement port is out of range".to_owned())
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct DiscoveredServer {
    pub base_url: String,
}

/// Background listener collecting recently announced servers.
pub struct DiscoveryListener {
    servers: Arc<Mutex<HashMap<String, Instant>>>,
    stop: Arc<AtomicBool>,
    thread: Option<JoinHandle<()>>,
    local_port: u16,
}

impl DiscoveryListener {
    pub fn start(port: u16) -> Result<Self, String> {
        let socket = UdpSocket::bind(("0.0.0.0", port))
            .map_err(|error| format!("failed to bind discovery port {port}: {error}"))?;
        socket
            .set_read_timeout(Some(RECEIVE_TIMEOUT))
            .map_err(|error| format!("failed to configure discovery socket: {error}"))?;
        let local_port = socket
            .local_addr()
            .map_err(|error| format!("failed to read discovery socket address: {error}"))?
            .port();

        let servers: Arc<Mutex<HashMap<String, Instant>>> = Arc::new(Mutex::new(HashMap::new()));
        let stop = Arc::new(AtomicBool::new(false));
        let thread_servers = Arc::clone(&servers);
        let thread_stop = Arc::clone(&stop);
        let thread = std::thread::Builder::new()
            .name("danmaku-discovery".to_owned())
            .spawn(move || {
                let mut buffer = [0_u8; 2_048];
                while !thread_stop.load(Ordering::Relaxed) {
                    match socket.recv_from(&mut buffer) {
                        Ok((length, source)) => {
                            if let Ok(server_port) = parse_announcement(&buffer[..length]) {
                                let host = match source.ip() {
                                    IpAddr::V4(ip) => ip.to_string(),
                                    IpAddr::V6(ip) => format!("[{ip}]"),
                                };
                                let base_url = format!("http://{host}:{server_port}");
                                if let Ok(mut servers) = thread_servers.lock() {
                                    servers.insert(base_url, Instant::now());
                                }
                            }
                        }
                        Err(error)
                            if matches!(
                                error.kind(),
                                std::io::ErrorKind::WouldBlock | std::io::ErrorKind::TimedOut
                            ) => {}
                        Err(_) => break,
                    }
                }
            })
            .map_err(|error| format!("failed to start discovery thread: {error}"))?;

        Ok(Self {
            servers,
            stop,
            thread: Some(thread),
            local_port,
        })
    }

    /// The bound UDP port (useful when started with port 0 in tests).
    pub fn local_port(&self) -> u16 {
        self.local_port
    }

    /// Recently announced servers, most recently seen first.
    pub fn servers(&self) -> Vec<DiscoveredServer> {
        let Ok(mut servers) = self.servers.lock() else {
            return Vec::new();
        };
        let now = Instant::now();
        servers.retain(|_, last_seen| now.duration_since(*last_seen) <= ANNOUNCE_TTL);
        let mut entries: Vec<(String, Instant)> = servers
            .iter()
            .map(|(url, seen)| (url.clone(), *seen))
            .collect();
        entries.sort_by_key(|(_, seen)| std::cmp::Reverse(*seen));
        entries
            .into_iter()
            .map(|(base_url, _)| DiscoveredServer { base_url })
            .collect()
    }
}

impl Drop for DiscoveryListener {
    fn drop(&mut self) {
        self.stop.store(true, Ordering::Relaxed);
        if let Some(thread) = self.thread.take() {
            let _ = thread.join();
        }
    }
}

#[cfg(test)]
mod tests {
    use super::{DiscoveryListener, parse_announcement};
    use std::net::UdpSocket;
    use std::time::{Duration, Instant};

    #[test]
    fn parses_minimal_and_full_announcements() {
        assert_eq!(parse_announcement(br#"{"port":8686}"#), Ok(8686));
        assert_eq!(
            parse_announcement(br#"{"protocol":"danmaku-library","version":1,"port":9000}"#),
            Ok(9000),
        );
    }

    #[test]
    fn rejects_wrong_protocol_version_and_ports() {
        assert!(parse_announcement(br#"{"protocol":"other","port":1}"#).is_err());
        assert!(parse_announcement(br#"{"version":2,"port":1}"#).is_err());
        assert!(parse_announcement(br#"{"port":0}"#).is_err());
        assert!(parse_announcement(br#"{"port":65536}"#).is_err());
        assert!(parse_announcement(br#"{}"#).is_err());
        assert!(parse_announcement(b"not json").is_err());
    }

    #[test]
    fn listener_collects_loopback_announcements() {
        let listener = DiscoveryListener::start(0).expect("listener starts");
        let sender = UdpSocket::bind(("127.0.0.1", 0)).expect("sender binds");
        sender
            .send_to(br#"{"port":8686}"#, ("127.0.0.1", listener.local_port()))
            .expect("send announcement");

        let deadline = Instant::now() + Duration::from_secs(5);
        loop {
            let servers = listener.servers();
            if servers
                .iter()
                .any(|server| server.base_url == "http://127.0.0.1:8686")
            {
                break;
            }
            assert!(
                Instant::now() < deadline,
                "discovery listener did not observe the announcement in time",
            );
            std::thread::sleep(Duration::from_millis(50));
        }
    }
}
