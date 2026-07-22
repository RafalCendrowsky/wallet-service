package me.rcendrow.settlement.domain.account

import java.math.BigDecimal
import java.util.*

data class AccountBalance(
    val accountId: UUID,
    val balance: BigDecimal,
    val activeHolds: BigDecimal,
) {
    val availableBalance: BigDecimal = balance.subtract(activeHolds)
}
