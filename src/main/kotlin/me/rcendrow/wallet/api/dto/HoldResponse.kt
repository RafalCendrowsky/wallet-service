package me.rcendrow.wallet.api.dto

import me.rcendrow.wallet.domain.Hold
import me.rcendrow.wallet.domain.HoldStatus
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

data class HoldResponse(
    val id: UUID,
    val from: WalletOwnerResponse?,
    val to: WalletOwnerResponse?,
    val amount: BigDecimal,
    val status: HoldStatus,
    val expiresAt: LocalDateTime,
    val createdAt: LocalDateTime,
) {
    companion object {
        fun from(hold: Hold) = HoldResponse(
            id = hold.id,
            from = hold.fromOwner?.let { WalletOwnerResponse.from(it) },
            to = hold.toOwner?.let { WalletOwnerResponse.from(it) },
            amount = hold.amount,
            status = hold.status,
            expiresAt = hold.expiresAt,
            createdAt = hold.createdAt,
        )
    }
}
