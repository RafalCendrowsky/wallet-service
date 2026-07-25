package me.rcendrow.wallet.domain.wallet

import java.time.LocalDateTime
import java.util.*

data class ServiceWallet(
    override val id: UUID,
    override val status: WalletStatus,
    override val createdAt: LocalDateTime,
    val role: ServiceWalletRole
) : Wallet {
    override val type: WalletType = WalletType.SERVICE
}
