use std::collections::BTreeSet;
use std::net::{IpAddr, Ipv4Addr, SocketAddr};
use std::time::Duration;

use serde::Serialize;
use tokio::net::UdpSocket;
use tokio::task::JoinHandle;
use tokio::time;

use crate::{LibraryServerError, Result};

pub const DISCOVERY_PORT: u16 = 8_687;
const DISCOVERY_INTERVAL: Duration = Duration::from_millis(1_500);
const PROTOCOL: &str = "danmaku-library";
const VERSION: u8 = 1;

#[derive(Debug)]
pub struct DiscoveryAnnouncer {
    task: JoinHandle<()>,
}

impl DiscoveryAnnouncer {
    pub async fn start(server_port: u16) -> Result<Self> {
        let payload = discovery_payload(server_port)?;
        let socket = UdpSocket::bind((Ipv4Addr::UNSPECIFIED, 0))
            .await
            .map_err(|error| {
                LibraryServerError::with_context(error, "failed to bind UDP discovery socket")
            })?;
        socket.set_broadcast(true).map_err(|error| {
            LibraryServerError::with_context(error, "failed to enable UDP broadcast")
        })?;
        let task = tokio::spawn(async move {
            let mut interval = time::interval(DISCOVERY_INTERVAL);
            loop {
                interval.tick().await;
                for destination in discovery_destinations() {
                    let _ = socket.send_to(&payload, destination).await;
                }
            }
        });
        Ok(Self { task })
    }
}

impl Drop for DiscoveryAnnouncer {
    fn drop(&mut self) {
        self.task.abort();
    }
}

pub fn discovery_payload(port: u16) -> Result<Vec<u8>> {
    if port == 0 {
        return Err(LibraryServerError::new(
            "discovery port payload must be non-zero",
        ));
    }
    serde_json::to_vec(&LanLibraryServerAnnouncement {
        protocol: PROTOCOL,
        version: VERSION,
        port,
    })
    .map_err(Into::into)
}

fn discovery_destinations() -> Vec<SocketAddr> {
    let mut addresses = BTreeSet::new();
    addresses.insert(Ipv4Addr::BROADCAST);
    addresses.extend(interface_broadcast_addresses());
    addresses
        .into_iter()
        .map(|address| SocketAddr::new(IpAddr::V4(address), DISCOVERY_PORT))
        .collect()
}

#[cfg(windows)]
fn interface_broadcast_addresses() -> Vec<Ipv4Addr> {
    windows_interfaces::broadcast_addresses()
}

#[cfg(not(windows))]
fn interface_broadcast_addresses() -> Vec<Ipv4Addr> {
    Vec::new()
}

#[cfg(windows)]
mod windows_interfaces {
    use std::net::Ipv4Addr;
    use std::ptr;

    use windows_sys::Win32::Foundation::{ERROR_BUFFER_OVERFLOW, NO_ERROR};
    use windows_sys::Win32::NetworkManagement::IpHelper::{
        GAA_FLAG_SKIP_ANYCAST, GAA_FLAG_SKIP_DNS_SERVER, GAA_FLAG_SKIP_MULTICAST,
        GetAdaptersAddresses, IF_TYPE_SOFTWARE_LOOPBACK, IP_ADAPTER_ADDRESSES_LH,
    };
    use windows_sys::Win32::NetworkManagement::Ndis::IfOperStatusUp;
    use windows_sys::Win32::Networking::WinSock::{AF_INET, SOCKADDR_IN};

    pub fn broadcast_addresses() -> Vec<Ipv4Addr> {
        let mut buffer_size = 15_000_u32;
        let mut buffer = vec![0_u8; buffer_size as usize];
        let flags = GAA_FLAG_SKIP_ANYCAST | GAA_FLAG_SKIP_MULTICAST | GAA_FLAG_SKIP_DNS_SERVER;
        let mut result = unsafe {
            GetAdaptersAddresses(
                AF_INET as u32,
                flags,
                ptr::null(),
                buffer.as_mut_ptr().cast::<IP_ADAPTER_ADDRESSES_LH>(),
                &mut buffer_size,
            )
        };
        if result == ERROR_BUFFER_OVERFLOW {
            buffer.resize(buffer_size as usize, 0);
            result = unsafe {
                GetAdaptersAddresses(
                    AF_INET as u32,
                    flags,
                    ptr::null(),
                    buffer.as_mut_ptr().cast::<IP_ADAPTER_ADDRESSES_LH>(),
                    &mut buffer_size,
                )
            };
        }
        if result != NO_ERROR {
            return Vec::new();
        }

        let mut broadcasts = Vec::new();
        let mut adapter = buffer.as_ptr().cast::<IP_ADAPTER_ADDRESSES_LH>();
        while !adapter.is_null() {
            let adapter_ref = unsafe { &*adapter };
            if adapter_ref.IfType != IF_TYPE_SOFTWARE_LOOPBACK
                && adapter_ref.OperStatus == IfOperStatusUp
            {
                let mut unicast = adapter_ref.FirstUnicastAddress;
                while !unicast.is_null() {
                    let unicast_ref = unsafe { &*unicast };
                    if unicast_ref.Address.lpSockaddr.is_null()
                        || unicast_ref.OnLinkPrefixLength > 32
                    {
                        unicast = unicast_ref.Next;
                        continue;
                    }
                    let sockaddr =
                        unsafe { &*(unicast_ref.Address.lpSockaddr.cast::<SOCKADDR_IN>()) };
                    if sockaddr.sin_family == AF_INET {
                        let ip = u32::from_be(unsafe { sockaddr.sin_addr.S_un.S_addr });
                        let prefix = unicast_ref.OnLinkPrefixLength;
                        let mask = if prefix == 0 {
                            0
                        } else {
                            u32::MAX << (32 - u32::from(prefix))
                        };
                        broadcasts.push(Ipv4Addr::from(ip | !mask));
                    }
                    unicast = unicast_ref.Next;
                }
            }
            adapter = adapter_ref.Next;
        }
        broadcasts
    }
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
struct LanLibraryServerAnnouncement<'a> {
    #[serde(skip_serializing_if = "is_default_protocol")]
    protocol: &'a str,
    #[serde(skip_serializing_if = "is_default_version")]
    version: u8,
    port: u16,
}

fn is_default_protocol(protocol: &&str) -> bool {
    *protocol == PROTOCOL
}

fn is_default_version(version: &u8) -> bool {
    *version == VERSION
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn default_discovery_payload_matches_kotlin_fixture() {
        assert_eq!(
            b"{\"port\":8686}".to_vec(),
            discovery_payload(8_686).expect("payload should encode")
        );
    }
}
