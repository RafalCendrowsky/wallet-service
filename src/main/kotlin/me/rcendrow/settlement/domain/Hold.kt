package me.rcendrow.settlement.domain

import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

data class Hold(
    val id: UUID,
    val accountId: UUID,
    val amount: BigDecimal,
    val status: HoldStatus,
    val expiresAt: LocalDateTime,
    val createdAt: LocalDateTime,
)
