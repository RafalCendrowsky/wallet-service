package me.rcendrow.wallet.domain.wallet

import java.time.LocalDateTime
import java.util.*

data class CustomerWallet(
    override val id: UUID,
    override val status: WalletStatus,
    override val createdAt: LocalDateTime,
    val customerId: UUID
) : Wallet {
    override val type: WalletType = WalletType.CUSTOMER
}
