package me.rcendrow.wallet.api.dto

import me.rcendrow.wallet.domain.wallet.Wallet
import me.rcendrow.wallet.domain.wallet.WalletStatus
import java.time.LocalDateTime
import java.util.*

data class WalletResponse(
    val id: UUID,
    val status: WalletStatus,
    val createdAt: LocalDateTime,
) {
    companion object {
        fun from(wallet: Wallet) = WalletResponse(
            id = wallet.id,
            status = wallet.status,
            createdAt = wallet.createdAt,
        )
    }
}
