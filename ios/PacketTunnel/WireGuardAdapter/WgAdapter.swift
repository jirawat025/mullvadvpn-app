//
//  WgAdapter.swift
//  PacketTunnel
//
//  Created by pronebird on 29/08/2023.
//  Copyright © 2023 Mullvad VPN AB. All rights reserved.
//

import Foundation
import MullvadLogging
import MullvadTypes
import NetworkExtension
import PacketTunnelCore
import WireGuardKit

struct WgAdapter: TunnelAdapterProtocol {
    let logger = Logger(label: "WgAdapter")
    let adapter: WireGuardAdapter

    init(packetTunnelProvider: NEPacketTunnelProvider) {
        let wgGoLogger = Logger(label: "WireGuard")

        adapter = WireGuardAdapter(
            with: packetTunnelProvider,
            shouldHandleReasserting: false,
            logHandler: { logLevel, string in
                wgGoLogger.log(level: logLevel.loggerLevel, "\(string)")
            }
        )
    }

    func start(configuration: TunnelAdapterConfiguration) async throws {
        try await startMultihop(exitConfiguration: configuration)
    }

    func startMultihop(exitConfiguration: TunnelAdapterConfiguration, entryConfiguration: TunnelAdapterConfiguration? = nil) async throws {
        let exitConfiguration = exitConfiguration.asWgConfig
        let entryConfiguration = entryConfiguration?.asWgConfig

        logger.info("\(exitConfiguration.peers)")
        logger.info("\(entryConfiguration?.peers)")

        do {
            try await adapter.stop()
            adapter.startMultihop(exitConfiguration: exitConfiguration, entryConfiguration: entryConfiguration) {
                logger.error("\($0?.localizedDescription ?? "Error starting the adapter")")
            }
        } catch WireGuardAdapterError.invalidState {
            adapter.startMultihop(exitConfiguration: exitConfiguration, entryConfiguration: entryConfiguration) {
                logger.error("\($0?.localizedDescription ?? "Error starting the adapter")")
            }
        }

        let exitTunAddresses = exitConfiguration.interface.addresses.map { $0.address }
        let entryTunAddresses = entryConfiguration?.interface.addresses.map { $0.address } ?? []
        let tunAddresses = exitTunAddresses + entryTunAddresses

        // TUN addresses can be empty when adapter is configured for blocked state.
        if !tunAddresses.isEmpty {
            logIfDeviceHasSameIP(than: tunAddresses)
        }
    }

    func stop() async throws {
        try await adapter.stop()
    }

    private func logIfDeviceHasSameIP(than addresses: [IPAddress]) {
        let sameIPv4 = IPv4Address("10.127.255.254")
        let sameIPv6 = IPv6Address("fc00:bbbb:bbbb:bb01:ffff:ffff:ffff:ffff")

        let hasIPv4SameAddress = addresses.compactMap { $0 as? IPv4Address }
            .contains { $0 == sameIPv4 }
        let hasIPv6SameAddress = addresses.compactMap { $0 as? IPv6Address }
            .contains { $0 == sameIPv6 }

        let isUsingSameIP = (hasIPv4SameAddress || hasIPv6SameAddress) ? "" : "NOT "
        logger.debug("Same IP is \(isUsingSameIP)being used")
    }
}

extension WgAdapter: TunnelDeviceInfoProtocol {
    var interfaceName: String? {
        return adapter.interfaceName
    }

    func getStats() throws -> WgStats {
        var result: String?

        let dispatchGroup = DispatchGroup()
        dispatchGroup.enter()
        adapter.getRuntimeConfiguration { string in
            result = string
            dispatchGroup.leave()
        }

        guard case .success = dispatchGroup.wait(wallTimeout: .now() + 1) else { throw StatsError.timeout }
        guard let result else { throw StatsError.nilValue }
        guard let newStats = WgStats(from: result) else { throw StatsError.parse }

        return newStats
    }

    enum StatsError: LocalizedError {
        case timeout, nilValue, parse

        var errorDescription: String? {
            switch self {
            case .timeout:
                return "adapter.getRuntimeConfiguration() timeout."
            case .nilValue:
                return "Received nil string for stats."
            case .parse:
                return "Couldn't parse stats."
            }
        }
    }
}

private extension TunnelAdapterConfiguration {
    var asWgConfig: TunnelConfiguration {
        var interfaceConfig = InterfaceConfiguration(privateKey: privateKey)
        interfaceConfig.addresses = interfaceAddresses
        interfaceConfig.dns = dns.map { DNSServer(address: $0) }
        interfaceConfig.listenPort = 0

        var peers: [PeerConfiguration] = []
        if let peer {
            var peerConfig = PeerConfiguration(publicKey: peer.publicKey)
            peerConfig.endpoint = peer.endpoint.wgEndpoint
            peerConfig.allowedIPs = allowedIPs
            peerConfig.preSharedKey = peer.preSharedKey
            peers.append(peerConfig)
        }

        return TunnelConfiguration(
            name: nil,
            interface: interfaceConfig,
            peers: peers
        )
    }
}

private extension AnyIPEndpoint {
    var wgEndpoint: Endpoint {
        switch self {
        case let .ipv4(endpoint):
            return Endpoint(host: .ipv4(endpoint.ip), port: .init(integerLiteral: endpoint.port))
        case let .ipv6(endpoint):
            return Endpoint(host: .ipv6(endpoint.ip), port: .init(integerLiteral: endpoint.port))
        }
    }
}

private extension WgStats {
    init?(from string: String) {
        var bytesReceived: UInt64?
        var bytesSent: UInt64?

        string.enumerateLines { line, stop in
            if bytesReceived == nil, let value = parseValue("rx_bytes=", in: line) {
                bytesReceived = value
            } else if bytesSent == nil, let value = parseValue("tx_bytes=", in: line) {
                bytesSent = value
            }

            if bytesReceived != nil, bytesSent != nil {
                stop = true
            }
        }

        guard let bytesReceived, let bytesSent else {
            return nil
        }

        self.init(bytesReceived: bytesReceived, bytesSent: bytesSent)
    }
}

@inline(__always) private func parseValue(_ prefixKey: String, in line: String) -> UInt64? {
    guard line.hasPrefix(prefixKey) else { return nil }

    let value = line.dropFirst(prefixKey.count)

    return UInt64(value)
}
