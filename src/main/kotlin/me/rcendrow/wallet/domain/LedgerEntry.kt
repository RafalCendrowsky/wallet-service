package me.rcendrow.wallet.domain

import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

data class LedgerEntry(
    val id: UUID,
    val transferId: UUID,
    val walletId: UUID,
    val amount: BigDecimal,
    val createdAt: LocalDateTime,
)
