package me.rcendrow.settlement.domain

import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

data class LedgerEntry(
    val id: UUID,
    val transferId: UUID,
    val accountId: UUID,
    val type: EntryType,
    val amount: BigDecimal,
    val createdAt: LocalDateTime,
)
