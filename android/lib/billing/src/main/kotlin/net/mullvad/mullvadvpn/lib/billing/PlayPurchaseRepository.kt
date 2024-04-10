package net.mullvad.mullvadvpn.lib.billing

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import net.mullvad.mullvadvpn.lib.daemon.grpc.ManagementService
import net.mullvad.mullvadvpn.model.PlayPurchase

class PlayPurchaseRepository(
    private val managementService: ManagementService
) {
    suspend fun initializePlayPurchase() = managementService.initializePlayPurchase()

    suspend fun verifyPlayPurchase(purchase: PlayPurchase) =
        managementService.verifyPlayPurchase(purchase)
}
