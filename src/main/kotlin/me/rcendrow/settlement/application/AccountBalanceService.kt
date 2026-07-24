package me.rcendrow.settlement.application

import me.rcendrow.settlement.domain.account.AccountBalance
import me.rcendrow.settlement.persistence.HoldRepository
import me.rcendrow.settlement.persistence.account.AccountBalanceQueueRepository
import me.rcendrow.settlement.persistence.account.AccountBalanceRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class AccountBalanceService(
    private val accountBalanceRepository: AccountBalanceRepository,
    private val accountBalanceQueueRepository: AccountBalanceQueueRepository,
    private val holdRepository: HoldRepository
) {

    fun markAccountForRefresh(accountId: UUID) {
        accountBalanceQueueRepository.insert(accountId)
    }

    @Transactional(readOnly = true)
    fun findBalance(accountId: UUID): AccountBalance {
        val balance = accountBalanceRepository.findCurrentBalance(accountId)
        val activeHolds = holdRepository.sumActiveAmount(accountId)
        return AccountBalance(accountId, balance, activeHolds)
    }

    @Transactional
    fun refreshBalance(batchSize: Int) {
        val batch = accountBalanceQueueRepository.claimOldestBatch(batchSize)
        if (batch.isNotEmpty()) {
            accountBalanceRepository.refreshBalances(batch)
        }
    }
}
