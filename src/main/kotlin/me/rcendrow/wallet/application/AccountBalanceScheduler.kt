package me.rcendrow.wallet.application

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(
    name = ["scheduler.account-balance.enabled"],
    havingValue = "true",
    matchIfMissing = true
)
class AccountBalanceScheduler(val accountBalanceService: AccountBalanceService) {

    @Scheduled(fixedDelay = 10)
    fun refreshBalance() {
        accountBalanceService.refreshBalance(1000)
    }
}
