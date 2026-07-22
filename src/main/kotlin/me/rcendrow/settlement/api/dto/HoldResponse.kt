package me.rcendrow.settlement.api.dto

import me.rcendrow.settlement.domain.Hold
import me.rcendrow.settlement.domain.HoldStatus
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

data class HoldResponse(
    val id: UUID,
    val accountId: UUID,
    val amount: BigDecimal,
    val status: HoldStatus,
    val expiresAt: LocalDateTime,
    val createdAt: LocalDateTime,
) {
    companion object {
        fun from(hold: Hold) = HoldResponse(
            id = hold.id,
            accountId = hold.accountId,
            amount = hold.amount,
            status = hold.status,
            expiresAt = hold.expiresAt,
            createdAt = hold.createdAt,
        )
    }
}
