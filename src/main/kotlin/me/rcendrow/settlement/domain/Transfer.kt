package me.rcendrow.settlement.domain

import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

data class Transfer(
    val id: UUID,
    val fromAccount: UUID,
    val toAccount: UUID,
    val amount: BigDecimal,
    val idempotencyKey: String,
    val createdAt: LocalDateTime,
)
