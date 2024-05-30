//! This module contains various helper functions for the relay selector implementation.

use std::net::SocketAddr;

use mullvad_types::{
    constraints::Constraint,
    endpoint::MullvadWireguardEndpoint,
    relay_constraints::{ShadowsocksSettings, Udp2TcpObfuscationSettings},
    relay_list::Relay,
};
use rand::{seq::SliceRandom, thread_rng, Rng};
use talpid_types::net::obfuscation::ObfuscatorConfig;

use crate::SelectedObfuscator;

#[derive(thiserror::Error, Debug)]
pub enum Error {
    #[error("Port selection algorithm is broken")]
    PortSelectionAlgorithm,
    #[error("Invalid port argument: port {0} is not in any valid Wireguard port range")]
    PortNotInRange(u16),
}

/// Picks a relay using [pick_random_relay_weighted], using the `weight` member of each relay
/// as the weight function.
pub fn pick_random_relay(relays: &[Relay]) -> Option<&Relay> {
    pick_random_relay_weighted(relays, |relay| relay.weight)
}

/// Pick a random relay from the given slice. Will return `None` if the given slice is empty.
/// If all of the relays have a weight of 0, one will be picked at random without bias,
/// otherwise roulette wheel selection will be used to pick only relays with non-zero
/// weights.
pub fn pick_random_relay_weighted<RelayType>(
    relays: &[RelayType],
    weight: impl Fn(&RelayType) -> u64,
) -> Option<&RelayType> {
    let total_weight: u64 = relays.iter().map(&weight).sum();
    let mut rng = thread_rng();
    if total_weight == 0 {
        relays.choose(&mut rng)
    } else {
        // Assign each relay a subset of the range 0..total_weight with size equal to its weight.
        // Pick a random number in the range 1..=total_weight. This choses the relay with a
        // non-zero weight.
        //
        //                           rng(1..=total_weight)
        //                           |
        //                           v
        //   ________________________i_______________________________________________
        // 0|_____________|____________________|___________|_____|________|__________| total_weight
        //  ^             ^                    ^                          ^          ^
        //  |             |                    |                          |          |
        //  ------------------------------------                          ------------
        //         |                  |                                         |
        //   weight(relay 0)     weight(relay 1)    ..       ..     ..    weight(relay n)
        let mut i: u64 = rng.gen_range(1..=total_weight);
        Some(
            relays
                .iter()
                .find(|relay| {
                    i = i.saturating_sub(weight(relay));
                    i == 0
                })
                .expect("At least one relay must've had a weight above 0"),
        )
    }
}

pub fn get_udp2tcp_obfuscator(
    obfuscation_settings_constraint: &Constraint<Udp2TcpObfuscationSettings>,
    udp2tcp_ports: &[u16],
    relay: Relay,
    endpoint: &MullvadWireguardEndpoint,
) -> Option<SelectedObfuscator> {
    let udp2tcp_endpoint_port =
        get_udp2tcp_obfuscator_port(obfuscation_settings_constraint, udp2tcp_ports)?;
    let config = ObfuscatorConfig::Udp2Tcp {
        endpoint: SocketAddr::new(endpoint.peer.endpoint.ip(), udp2tcp_endpoint_port),
    };

    Some(SelectedObfuscator { config, relay })
}

pub fn get_udp2tcp_obfuscator_port(
    obfuscation_settings_constraint: &Constraint<Udp2TcpObfuscationSettings>,
    udp2tcp_ports: &[u16],
) -> Option<u16> {
    match obfuscation_settings_constraint {
        Constraint::Only(obfuscation_settings) if obfuscation_settings.port.is_only() => {
            udp2tcp_ports
                .iter()
                .find(|&candidate| obfuscation_settings.port == Constraint::Only(*candidate))
                .copied()
        }
        // There are no specific obfuscation settings to take into consideration in this case.
        Constraint::Any | Constraint::Only(_) => udp2tcp_ports.choose(&mut thread_rng()).copied(),
    }
}

pub fn get_shadowsocks_obfuscator(
    settings: &Constraint<ShadowsocksSettings>,
    port_ranges: &[(u16, u16)],
    relay: Relay,
    endpoint: &MullvadWireguardEndpoint,
) -> Option<SelectedObfuscator> {
    let port = select_random_port(
        settings.as_ref().and_then(|settings| settings.port),
        port_ranges,
    )
    .ok()?;

    let config = ObfuscatorConfig::Shadowsocks {
        endpoint: SocketAddr::new(endpoint.peer.endpoint.ip(), port),
    };

    Some(SelectedObfuscator { config, relay })
}

/// Selects a random port number from a list of provided port ranges.
///
/// This function iterates over a list of port ranges, each represented as a tuple (u16, u16)
/// where the first element is the start of the range and the second is the end (inclusive),
/// and selects a random port from the set of all ranges.
///
/// # Parameters
/// - `port`: Constraint to apply to the port selection
/// - `port_ranges`: A slice of tuples, each representing a range of valid port numbers.
///
/// # Returns
/// - A randomly selected port number within the given ranges.
///
/// # Panic
/// - If port ranges contains no ports, this function panics.
pub fn select_random_port(port: Constraint<u16>, port_ranges: &[(u16, u16)]) -> Result<u16, Error> {
    match port {
        Constraint::Any => select_random_port_inner(port_ranges),
        Constraint::Only(port) => {
            if port_ranges
                .iter()
                .any(|range| (range.0 <= port && port <= range.1))
            {
                Ok(port)
            } else {
                Err(Error::PortNotInRange(port))
            }
        }
    }
}

fn select_random_port_inner(port_ranges: &[(u16, u16)]) -> Result<u16, Error> {
    let get_port_amount = |range: &(u16, u16)| -> u64 { (1 + range.1 - range.0) as u64 };
    let port_amount: u64 = port_ranges.iter().map(get_port_amount).sum();

    if port_amount < 1 {
        return Err(Error::PortSelectionAlgorithm);
    }

    let mut port_index = rand::thread_rng().gen_range(0..port_amount);

    for range in port_ranges.iter() {
        let ports_in_range = get_port_amount(range);
        if port_index < ports_in_range {
            return Ok(port_index as u16 + range.0);
        }
        port_index -= ports_in_range;
    }
    Err(Error::PortSelectionAlgorithm)
}
