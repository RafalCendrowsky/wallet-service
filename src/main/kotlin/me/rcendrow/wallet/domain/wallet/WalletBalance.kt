package me.rcendrow.wallet.domain.wallet

import java.math.BigDecimal
import java.util.*

data class WalletBalance(
    val walletId: UUID,
    val balance: BigDecimal,
    val activeHolds: BigDecimal,
) {
    val availableBalance: BigDecimal = balance.subtract(activeHolds)
}
