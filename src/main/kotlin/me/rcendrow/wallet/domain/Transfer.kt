package me.rcendrow.wallet.domain

import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

data class Transfer(
    val id: UUID,
    val fromWallet: UUID,
    val toWallet: UUID,
    val amount: BigDecimal,
    val idempotencyKey: String,
    val createdAt: LocalDateTime,
)
