package net.mullvad.mullvadvpn.lib.daemon.grpc

import android.net.LocalSocketAddress
import android.util.Log
import arrow.core.Either
import com.google.protobuf.BoolValue
import com.google.protobuf.Empty
import com.google.protobuf.StringValue
import com.google.protobuf.UInt32Value
import io.grpc.ConnectivityState
import io.grpc.ManagedChannel
import io.grpc.Status
import io.grpc.StatusException
import io.grpc.android.UdsChannelBuilder
import java.net.InetAddress
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import mullvad_daemon.management_interface.ManagementInterface
import mullvad_daemon.management_interface.ManagementInterface.AppVersionInfo
import mullvad_daemon.management_interface.ManagementInterface.CustomDnsOptions
import mullvad_daemon.management_interface.ManagementInterface.CustomRelaySettings
import mullvad_daemon.management_interface.ManagementInterface.DaemonEvent
import mullvad_daemon.management_interface.ManagementInterface.DeviceEvent
import mullvad_daemon.management_interface.ManagementInterface.DeviceState
import mullvad_daemon.management_interface.ManagementInterface.RelayList
import mullvad_daemon.management_interface.ManagementInterface.Settings
import mullvad_daemon.management_interface.ManagementInterface.TunnelState
import mullvad_daemon.management_interface.ManagementServiceGrpcKt
import mullvad_daemon.management_interface.copy
import net.mullvad.mullvadvpn.model.AccountCreationResult
import net.mullvad.mullvadvpn.model.AccountData
import net.mullvad.mullvadvpn.model.AccountToken
import net.mullvad.mullvadvpn.model.AppVersionInfo as ModelAppVersionInfo
import net.mullvad.mullvadvpn.model.ClearAllOverridesError
import net.mullvad.mullvadvpn.model.Constraint
import net.mullvad.mullvadvpn.model.CreateCustomListError
import net.mullvad.mullvadvpn.model.CustomList as ModelCustomList
import net.mullvad.mullvadvpn.model.CustomListId
import net.mullvad.mullvadvpn.model.CustomListName
import net.mullvad.mullvadvpn.model.DeleteCustomListError
import net.mullvad.mullvadvpn.model.Device as ModelDevice
import net.mullvad.mullvadvpn.model.DeviceState as ModelDeviceState
import net.mullvad.mullvadvpn.model.DnsOptions as ModelDnsOptions
import net.mullvad.mullvadvpn.model.DnsState as ModelDnsState
import net.mullvad.mullvadvpn.model.LocationConstraint as ModelLocationConstraint
import net.mullvad.mullvadvpn.model.LoginResult
import net.mullvad.mullvadvpn.model.ObfuscationSettings as ModelObfuscationSettings
import net.mullvad.mullvadvpn.model.Ownership as ModelOwnership
import net.mullvad.mullvadvpn.model.Providers as ModelProviders
import net.mullvad.mullvadvpn.model.QuantumResistantState as ModelQuantumResistantState
import net.mullvad.mullvadvpn.model.RelayList as ModelRelayList
import net.mullvad.mullvadvpn.model.RelaySettings
import net.mullvad.mullvadvpn.model.SetAllowLanError
import net.mullvad.mullvadvpn.model.SetAutoConnectError
import net.mullvad.mullvadvpn.model.SetDnsOptionsError
import net.mullvad.mullvadvpn.model.SetObfuscationOptionsError
import net.mullvad.mullvadvpn.model.SetRelayLocationError
import net.mullvad.mullvadvpn.model.SetWireguardConstraintsError
import net.mullvad.mullvadvpn.model.SetWireguardMtuError
import net.mullvad.mullvadvpn.model.SetWireguardQuantumResistantError
import net.mullvad.mullvadvpn.model.Settings as ModelSettings
import net.mullvad.mullvadvpn.model.SettingsPatchError
import net.mullvad.mullvadvpn.model.TunnelState as ModelTunnelState
import net.mullvad.mullvadvpn.model.UpdateCustomListError
import net.mullvad.mullvadvpn.model.WireguardConstraints as ModelWireguardConstraints
import net.mullvad.mullvadvpn.model.WireguardEndpointData as ModelWireguardEndpointData
import org.joda.time.Instant

class ManagementService(
    rpcSocketPath: String,
    private val scope: CoroutineScope,
) {

    data class ManagementServiceState(
        val tunnelState: TunnelState? = null,
        val settings: Settings? = null,
        val relayList: RelayList? = null,
        val versionInfo: AppVersionInfo? = null,
        val device: DeviceState? = null,
        val deviceEvent: DeviceEvent? = null,
    )

    private val channel =
        UdsChannelBuilder.forPath(rpcSocketPath, LocalSocketAddress.Namespace.FILESYSTEM).build()

    val connectionState: StateFlow<GrpcConnectivityState> =
        channel
            .connectivityFlow()
            .map(ConnectivityState::toDomain)
            .onEach { Log.d(TAG, "Connection state: $it") }
            .stateIn(scope, SharingStarted.Eagerly, channel.getState(false).toDomain())

    private fun ManagedChannel.connectivityFlow(): Flow<ConnectivityState> {
        return callbackFlow {
            var currentState = getState(false)
            send(currentState)

            while (isActive) {
                currentState =
                    suspendCoroutine<ConnectivityState> {
                        notifyWhenStateChanged(currentState) { it.resume(getState(false)) }
                    }
                send(currentState)
            }
        }
    }

    private val managementService =
        ManagementServiceGrpcKt.ManagementServiceCoroutineStub(channel).withWaitForReady()

    private val _mutableStateFlow: MutableStateFlow<ManagementServiceState> =
        MutableStateFlow(ManagementServiceState())
    val state: StateFlow<ManagementServiceState> = _mutableStateFlow

    val deviceState: Flow<ModelDeviceState> =
        _mutableStateFlow
            .mapNotNull { it.device }
            .map {
                when (it.state) {
                    DeviceState.State.LOGGED_IN ->
                        ModelDeviceState.LoggedIn(
                            device =
                                ModelDevice(
                                    it.device.device.id,
                                    it.device.device.name,
                                    it.device.device.pubkey.toByteArray(),
                                    it.device.device.created.toString(),
                                ),
                            accountToken = it.device.accountToken
                        )
                    DeviceState.State.LOGGED_OUT -> ModelDeviceState.LoggedOut
                    DeviceState.State.REVOKED -> ModelDeviceState.Revoked
                    DeviceState.State.UNRECOGNIZED -> ModelDeviceState.Unknown
                }
            }
            .stateIn(scope, SharingStarted.Eagerly, ModelDeviceState.Unknown)

    val tunnelState: Flow<ModelTunnelState> =
        _mutableStateFlow.mapNotNull { it.tunnelState }.map { it.toDomain() }

    val settings: Flow<ModelSettings> =
        _mutableStateFlow.mapNotNull { it.settings }.map { it.toDomain() }

    val versionInfo: Flow<ModelAppVersionInfo> =
        _mutableStateFlow.mapNotNull { it.versionInfo }.map { it.toDomain() }

    val relayList: Flow<ModelRelayList> =
        _mutableStateFlow.mapNotNull { it.relayList?.toDomain()?.first }

    val wireguardEndpointData: Flow<ModelWireguardEndpointData> =
        _mutableStateFlow.mapNotNull { it.relayList?.toDomain()?.second }

    suspend fun start() {
        scope.launch {
            try {
                managementService.eventsListen(Empty.getDefaultInstance()).collect { event ->
                    Log.d("ManagementService", "Event: $event")
                    @Suppress("WHEN_ENUM_CAN_BE_NULL_IN_JAVA")
                    when (event.eventCase) {
                        DaemonEvent.EventCase.TUNNEL_STATE ->
                            _mutableStateFlow.update { it.copy(tunnelState = event.tunnelState) }
                        DaemonEvent.EventCase.SETTINGS ->
                            _mutableStateFlow.update { it.copy(settings = event.settings) }
                        DaemonEvent.EventCase.RELAY_LIST ->
                            _mutableStateFlow.update { it.copy(relayList = event.relayList) }
                        DaemonEvent.EventCase.VERSION_INFO ->
                            _mutableStateFlow.update { it.copy(versionInfo = event.versionInfo) }
                        DaemonEvent.EventCase.DEVICE ->
                            _mutableStateFlow.update { it.copy(device = event.device.newState) }
                        DaemonEvent.EventCase.REMOVE_DEVICE -> {}
                        DaemonEvent.EventCase.EVENT_NOT_SET -> {}
                        DaemonEvent.EventCase.NEW_ACCESS_METHOD -> {}
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in eventsListen: ${e.message}")
            }
        }
        scope.launch { _mutableStateFlow.update { getInitialServiceState() } }
    }

    suspend fun getDevice(): DeviceState = managementService.getDevice(Empty.getDefaultInstance())

    suspend fun getTunnelState(): TunnelState =
        managementService.getTunnelState(Empty.getDefaultInstance())

    suspend fun connect(): Boolean =
        managementService.connectTunnel(Empty.getDefaultInstance()).value

    suspend fun disconnect(): Boolean =
        managementService.disconnectTunnel(Empty.getDefaultInstance()).value

    suspend fun reconnect(): Boolean =
        managementService.reconnectTunnel(Empty.getDefaultInstance()).value

    suspend fun getSettings(): Settings = managementService.getSettings(Empty.getDefaultInstance())

    suspend fun getRelayList(): RelayList =
        managementService.getRelayLocations(Empty.getDefaultInstance())

    suspend fun getVersionInfo(): AppVersionInfo =
        managementService.getVersionInfo(Empty.getDefaultInstance())

    suspend fun logoutAccount(): Unit {
        managementService.logoutAccount(Empty.getDefaultInstance())
    }

    suspend fun loginAccount(accountToken: String): LoginResult {
        return try {
            managementService.loginAccount(StringValue.of(accountToken))
            LoginResult.Ok
        } catch (e: StatusException) {
            when (e.status.code) {
                Status.Code.OK -> TODO()
                Status.Code.RESOURCE_EXHAUSTED -> LoginResult.MaxDevicesReached
                Status.Code.UNAVAILABLE -> LoginResult.RpcError
                Status.Code.UNAUTHENTICATED -> LoginResult.InvalidAccount
                Status.Code.CANCELLED -> TODO()
                Status.Code.UNKNOWN -> TODO()
                Status.Code.INVALID_ARGUMENT -> TODO()
                Status.Code.DEADLINE_EXCEEDED -> TODO()
                Status.Code.NOT_FOUND -> TODO()
                Status.Code.ALREADY_EXISTS -> TODO()
                Status.Code.PERMISSION_DENIED -> TODO()
                Status.Code.FAILED_PRECONDITION -> TODO()
                Status.Code.ABORTED -> TODO()
                Status.Code.OUT_OF_RANGE -> TODO()
                Status.Code.UNIMPLEMENTED -> TODO()
                Status.Code.INTERNAL -> TODO()
                Status.Code.DATA_LOSS -> TODO()
            }
        }
    }

    suspend fun clearAccountHistory(): Unit {
        managementService.clearAccountHistory(Empty.getDefaultInstance())
    }

    suspend fun getAccountHistory() =
        try {
            val history = managementService.getAccountHistory(Empty.getDefaultInstance())
            if (history.hasToken()) {
                AccountToken(history.token.value)
            } else {
                null
            }
        } catch (e: StatusException) {
            throw e
        }

    private suspend fun getInitialServiceState() =
        ManagementServiceState(
            getTunnelState(),
            getSettings(),
            getRelayList(),
            getVersionInfo(),
            getDevice(),
        )

    suspend fun getAccountData(accountToken: String): AccountData? =
        try {
            val accountData = managementService.getAccountData(StringValue.of(accountToken))
            accountData.expiry
            AccountData(
                UUID.fromString(accountData.id),
                Instant.ofEpochSecond(accountData.expiry.seconds).toDateTime()
            )
        } catch (e: StatusException) {
            throw e
        }

    suspend fun createAccount(): AccountCreationResult =
        try {
            val accountTokenStringValue =
                managementService.createNewAccount(Empty.getDefaultInstance())
            AccountCreationResult.Success(accountTokenStringValue.value)
        } catch (e: StatusException) {
            Log.e(TAG, "createAccount error: ${e.message}")
            AccountCreationResult.Failure
        }

    suspend fun setDnsOptions(dnsOptions: ModelDnsOptions): Either<SetDnsOptionsError, Unit> =
        Either.catch { managementService.setDnsOptions(dnsOptions.fromDomain()) }
            .mapLeft(SetDnsOptionsError::Unknown)
            .mapEmpty()

    suspend fun setDnsState(dnsState: ModelDnsState): Either<SetDnsOptionsError, Unit> =
        Either.catch {
                val currentDnsOptions = getSettings().tunnelOptions.dnsOptions
                val newDnsState = dnsState.fromDomain()
                managementService.setDnsOptions(currentDnsOptions.copy { this.state = newDnsState })
            }
            .mapLeft(SetDnsOptionsError::Unknown)
            .mapEmpty()

    suspend fun setCustomDns(index: Int, address: InetAddress): Either<SetDnsOptionsError, Unit> =
        Either.catch {
                val currentDnsOptions = getSettings().tunnelOptions.dnsOptions
                managementService.setDnsOptions(
                    currentDnsOptions.copy {
                        customOptions.copy {
                            this.addresses.apply {
                                if (index == -1) {
                                    add(address.toString())
                                } else {
                                    set(index, address.toString())
                                }
                            }
                        }
                    }
                )
            }
            .mapLeft(SetDnsOptionsError::Unknown)
            .mapEmpty()

    suspend fun deleteCustomDns(address: InetAddress): Either<SetDnsOptionsError, Unit> =
        Either.catch {
                val currentDnsOptions = getSettings().tunnelOptions.dnsOptions
                val currentCustomDnsOptions = currentDnsOptions.customOptions
                val newCustomDnsOptions =
                    CustomDnsOptions.newBuilder()
                        .addAllAddresses(
                            currentCustomDnsOptions.addressesList.filter {
                                it != address.toString()
                            }
                        )
                        .build()
                managementService.setDnsOptions(
                    currentDnsOptions.copy { this.customOptions = newCustomDnsOptions }
                )
            }
            .mapLeft(SetDnsOptionsError::Unknown)
            .mapEmpty()

    suspend fun setWireguardMtu(value: Int): Either<SetWireguardMtuError, Unit> =
        Either.catch { managementService.setWireguardMtu(UInt32Value.of(value)) }
            .mapLeft(SetWireguardMtuError::Unknown)
            .mapEmpty()

    suspend fun setWireguardQuantumResistant(
        value: ModelQuantumResistantState
    ): Either<SetWireguardQuantumResistantError, Unit> =
        Either.catch { managementService.setQuantumResistantTunnel(value.toDomain()) }
            .mapLeft(SetWireguardQuantumResistantError::Unknown)
            .mapEmpty()

    // Todo needs to be more advanced
    suspend fun setRelaySettings(value: RelaySettings) {
        managementService.setRelaySettings(value.fromDomain())
    }

    fun RelaySettings.fromDomain(): ManagementInterface.RelaySettings =
        ManagementInterface.RelaySettings.newBuilder()
            .apply {
                when (this@fromDomain) {
                    RelaySettings.CustomTunnelEndpoint ->
                        setCustom(CustomRelaySettings.newBuilder().build())
                    is RelaySettings.Normal ->
                        setNormal(
                            ManagementInterface.NormalRelaySettings.newBuilder()
                                .setLocation(this@fromDomain.relayConstraints.location.fromDomain())
                                .build()
                        )
                }
            }
            .build()

    suspend fun setObfuscationOptions(
        value: ModelObfuscationSettings
    ): Either<SetObfuscationOptionsError, Unit> =
        Either.catch { managementService.setObfuscationSettings(value.fromDomain()) }
            .mapLeft(SetObfuscationOptionsError::Unknown)
            .mapEmpty()

    suspend fun setAutoConnect(isEnabled: Boolean): Either<SetAutoConnectError, Unit> =
        Either.catch { managementService.setAutoConnect(BoolValue.of(isEnabled)) }
            .mapLeft(SetAutoConnectError::Unknown)
            .mapEmpty()

    suspend fun setAllowLan(allow: Boolean): Either<SetAllowLanError, Unit> =
        Either.catch { managementService.setAllowLan(BoolValue.of(allow)) }
            .mapLeft(SetAllowLanError::Unknown)
            .mapEmpty()

    suspend fun getCurrentVersion(): String =
        managementService.getCurrentVersion(Empty.getDefaultInstance()).value

    suspend fun setRelayLocation(
        location: ModelLocationConstraint
    ): Either<SetRelayLocationError, Unit> =
        Either.catch {
                val currentRelaySettings = getSettings().relaySettings
                val newRelaySettings =
                    currentRelaySettings.copy {
                        this.normal = this.normal.copy { this.location = location.fromDomain() }
                    }
                managementService.setRelaySettings(newRelaySettings)
            }
            .mapLeft(SetRelayLocationError::Unknown)
            .mapEmpty()

    suspend fun createCustomList(
        name: CustomListName
    ): Either<CreateCustomListError, CustomListId> =
        Either.catch { managementService.createCustomList(StringValue.of(name.value)) }
            .map { CustomListId(it.value) }
            .mapLeft {
                if (it is StatusException) {
                    when (it.status.code) {
                        Status.Code.ALREADY_EXISTS -> CreateCustomListError.CustomListAlreadyExists
                        else -> CreateCustomListError.Unknown(it)
                    }
                } else {
                    throw it
                }
            }

    suspend fun updateCustomList(customList: ModelCustomList): Either<UpdateCustomListError, Unit> =
        Either.catch { managementService.updateCustomList(customList.fromDomain()) }
            .mapLeft(UpdateCustomListError::Unknown)
            .mapEmpty()

    suspend fun deleteCustomList(id: CustomListId): Either<DeleteCustomListError, Unit> =
        Either.catch { managementService.deleteCustomList(StringValue.of(id.value)) }
            .mapLeft(DeleteCustomListError::Unknown)
            .mapEmpty()

    suspend fun clearAllRelayOverrides(): Either<ClearAllOverridesError, Unit> =
        Either.catch { managementService.clearAllRelayOverrides(Empty.getDefaultInstance()) }
            .mapLeft(ClearAllOverridesError::Unknown)
            .mapEmpty()

    suspend fun applySettingsPatch(json: String): Either<SettingsPatchError, Unit> =
        Either.catch { managementService.applyJsonSettings(StringValue.of(json)) }
            .mapLeft {
                if (it is StatusException) {
                    Log.d(
                        TAG,
                        "applySettingsPatch error: ${it.status.description} ${it.status.code}"
                    )
                    when (it.status.code) {
                        // Currently we only get invalid argument errors from daemon via gRPC
                        Status.Code.INVALID_ARGUMENT -> SettingsPatchError.ParsePatch
                        else -> SettingsPatchError.ApplyPatch
                    }
                } else {
                    throw it
                }
            }
            .mapEmpty()

    suspend fun setWireguardConstraints(
        value: ModelWireguardConstraints
    ): Either<SetWireguardConstraintsError, Unit> =
        Either.catch {
                val relaySettings = getSettings().relaySettings
                relaySettings.copy {
                    this.normal =
                        this.normal.copy { this.wireguardConstraints = value.fromDomain() }
                }
                managementService.setRelaySettings(relaySettings)
            }
            .mapLeft(SetWireguardConstraintsError::Unknown)
            .mapEmpty()

    suspend fun setOwnershipAndProviders(
        ownership: Constraint<ModelOwnership>,
        providers: Constraint<ModelProviders>
    ): Either<SetWireguardConstraintsError, Unit> =
        Either.catch {
                val relaySettings =
                    ManagementInterface.RelaySettings.newBuilder(getSettings().relaySettings)
                        .setNormal(
                            ManagementInterface.NormalRelaySettings.newBuilder()
                                .setOwnership(
                                    if (ownership is Constraint.Only) {
                                        ownership.value.fromDomain()
                                    } else {
                                        ManagementInterface.Ownership.ANY
                                    }
                                )
                                .addAllProviders(
                                    if (providers is Constraint.Only) {
                                        providers.value.fromDomain()
                                    } else {
                                        emptyList()
                                    }
                                )
                        )
                        .build()
                managementService.setRelaySettings(relaySettings)
            }
            .mapLeft(SetWireguardConstraintsError::Unknown)
            .mapEmpty()

    suspend fun setOwnership(
        ownership: Constraint<ModelOwnership>
    ): Either<SetWireguardConstraintsError, Unit> =
        Either.catch {
                val relaySettings = getSettings().relaySettings
                relaySettings.copy {
                    this.normal =
                        this.normal.copy {
                            this.ownership =
                                if (ownership is Constraint.Only) {
                                    ownership.value.fromDomain()
                                } else {
                                    ManagementInterface.Ownership.ANY
                                }
                        }
                }
                managementService.setRelaySettings(relaySettings)
            }
            .mapLeft(SetWireguardConstraintsError::Unknown)
            .mapEmpty()

    suspend fun setProviders(
        providers: Constraint<ModelProviders>
    ): Either<SetWireguardConstraintsError, Unit> =
        Either.catch {
                val relaySettings = getSettings().relaySettings
                relaySettings.copy {
                    this.normal =
                        this.normal.copy {
                            this.providers.clear()
                            this.providers.addAll(
                                if (providers is Constraint.Only) {
                                    providers.value.fromDomain()
                                } else {
                                    emptyList()
                                }
                            )
                        }
                }
                managementService.setRelaySettings(relaySettings)
            }
            .mapLeft(SetWireguardConstraintsError::Unknown)
            .mapEmpty()

    private fun <A> Either<A, Empty>.mapEmpty() = map {}

    companion object {
        private const val TAG = "ManagementService"
    }
}

sealed interface GrpcConnectivityState {
    data object Connecting : GrpcConnectivityState

    data object Ready : GrpcConnectivityState

    data object Idle : GrpcConnectivityState

    data object TransientFailure : GrpcConnectivityState

    data object Shutdown : GrpcConnectivityState
}

sealed interface ServiceConnectionState {
    data class Connected(val serviceState: ServiceState) : ServiceConnectionState

    data class Connecting(val lastKnownState: ServiceState?) : ServiceConnectionState

    data class Disconnected(val lastKnownState: ServiceState?, val error: ServiceConnectError?) :
        ServiceConnectionState
}

data class ServiceState(val settings: ModelSettings, val accountState: ModelSettings)

sealed interface ServiceConnectError {
    data object Timeout : ServiceConnectError

    data class Connection(val message: String) : ServiceConnectError
}
