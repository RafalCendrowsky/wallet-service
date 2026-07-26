package me.rcendrow.wallet.application

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(
    name = ["scheduler.wallet-balance.enabled"],
    havingValue = "true",
    matchIfMissing = true
)
class WalletBalanceScheduler(val walletBalanceService: WalletBalanceService) {

    @Scheduled(fixedDelay = 500)
    fun refreshBalance() {
        walletBalanceService.refreshBalance(500)
    }
}
