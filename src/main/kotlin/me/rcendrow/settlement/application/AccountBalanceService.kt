package me.rcendrow.settlement.application

import me.rcendrow.settlement.domain.account.AccountBalance
import me.rcendrow.settlement.persistence.AccountBalanceRepository
import me.rcendrow.settlement.persistence.HoldRepository
import me.rcendrow.settlement.persistence.LedgerRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class AccountBalanceService(
    private val accountBalanceRepository: AccountBalanceRepository,
    private val ledgerRepository: LedgerRepository,
    private val holdRepository: HoldRepository
) {

    @Transactional
    fun sync(accountId: UUID) {
        val balance = ledgerRepository.findBalance(accountId)
        accountBalanceRepository.upsert(accountId, balance)
    }

    @Transactional(readOnly = true)
    fun findBalance(accountId: UUID): AccountBalance {
        val balance = accountBalanceRepository.findBalance(accountId) ?: ledgerRepository.findBalance(accountId)
        val activeHolds = holdRepository.sumActiveAmount(accountId)
        return AccountBalance(accountId, balance, activeHolds)
    }

    @Transactional
    fun rebuild() {
        val allBalances = ledgerRepository.findAllBalances()
        accountBalanceRepository.rebuildBalances(allBalances)
    }
}
