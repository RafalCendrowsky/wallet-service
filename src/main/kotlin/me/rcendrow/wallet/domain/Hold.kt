package me.rcendrow.wallet.domain

import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

data class Hold(
    val id: UUID,
    val fromWallet: UUID,
    val toWallet: UUID,
    val customerId: UUID,
    val amount: BigDecimal,
    val status: HoldStatus,
    val expiresAt: LocalDateTime,
    val createdAt: LocalDateTime,
)
