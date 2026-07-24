package me.rcendrow.wallet.api.dto

import me.rcendrow.wallet.domain.account.AccountBalance
import java.math.BigDecimal
import java.util.*

data class AccountBalanceResponse(
    val accountId: UUID,
    val balance: BigDecimal,
    val activeHolds: BigDecimal,
    val availableBalance: BigDecimal,
) {
    companion object {
        fun from(accountBalance: AccountBalance) = AccountBalanceResponse(
            accountId = accountBalance.accountId,
            balance = accountBalance.balance,
            activeHolds = accountBalance.activeHolds,
            availableBalance = accountBalance.availableBalance,
        )
    }
}
