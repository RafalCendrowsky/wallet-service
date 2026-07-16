package me.rcendrow.settlement.api.dto

import java.math.BigDecimal
import java.util.*

data class BalanceResponse(
    val accountId: UUID,
    val balance: BigDecimal,
)
