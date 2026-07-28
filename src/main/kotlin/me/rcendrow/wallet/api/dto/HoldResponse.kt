package me.rcendrow.wallet.api.dto

import me.rcendrow.wallet.domain.Hold
import me.rcendrow.wallet.domain.HoldStatus
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

data class HoldResponse(
    val id: UUID,
    val fromWallet: UUID,
    val toWallet: UUID,
    val amount: BigDecimal,
    val status: HoldStatus,
    val expiresAt: LocalDateTime,
    val createdAt: LocalDateTime,
) {
    companion object {
        fun from(hold: Hold) = HoldResponse(
            id = hold.id,
            fromWallet = hold.fromWallet,
            toWallet = hold.toWallet,
            amount = hold.amount,
            status = hold.status,
            expiresAt = hold.expiresAt,
            createdAt = hold.createdAt,
        )
    }
}
