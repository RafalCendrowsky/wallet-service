package me.rcendrow.wallet.api.dto

import me.rcendrow.wallet.domain.wallet.WalletBalance
import java.math.BigDecimal
import java.util.*

data class WalletBalanceResponse(
    val walletId: UUID,
    val balance: BigDecimal,
    val activeHolds: BigDecimal,
    val availableBalance: BigDecimal,
) {
    companion object {
        fun from(walletBalance: WalletBalance) = WalletBalanceResponse(
            walletId = walletBalance.walletId,
            balance = walletBalance.balance,
            activeHolds = walletBalance.activeHolds,
            availableBalance = walletBalance.availableBalance,
        )
    }
}
