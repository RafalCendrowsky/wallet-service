package me.rcendrow.wallet.application

import me.rcendrow.wallet.domain.wallet.WalletBalance
import me.rcendrow.wallet.persistence.HoldRepository
import me.rcendrow.wallet.persistence.wallet.WalletBalanceQueueRepository
import me.rcendrow.wallet.persistence.wallet.WalletBalanceRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class WalletBalanceService(
    private val walletBalanceRepository: WalletBalanceRepository,
    private val walletBalanceQueueRepository: WalletBalanceQueueRepository,
    private val holdRepository: HoldRepository
) {

    @Transactional
    fun markWalletForRefresh(walletId: UUID) {
        walletBalanceQueueRepository.insert(walletId)
    }

    @Transactional(readOnly = true)
    fun findBalance(walletId: UUID): WalletBalance {
        val balance = walletBalanceRepository.findCurrentBalance(walletId)
        val activeHolds = holdRepository.sumActiveAmount(walletId)
        return WalletBalance(walletId, balance, activeHolds)
    }

    @Transactional
    fun refreshBalance(batchSize: Int) {
        val batch = walletBalanceQueueRepository.claimOldestBatch(batchSize)
        if (batch.isNotEmpty()) {
            walletBalanceRepository.refreshBalances(batch)
        }
    }
}
