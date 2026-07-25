package me.rcendrow.wallet.application

import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import me.rcendrow.wallet.persistence.HoldRepository
import me.rcendrow.wallet.persistence.wallet.WalletBalanceQueueRepository
import me.rcendrow.wallet.persistence.wallet.WalletBalanceRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.*

class WalletBalanceServiceTest {

    private val walletBalanceRepository: WalletBalanceRepository = mockk()
    private val walletBalanceQueueRepository: WalletBalanceQueueRepository = mockk()
    private val holdRepository: HoldRepository = mockk()
    private val service = WalletBalanceService(walletBalanceRepository, walletBalanceQueueRepository, holdRepository)

    @AfterEach
    fun tearDown() {
        clearMocks(walletBalanceRepository, walletBalanceQueueRepository, holdRepository)
    }

    @Test
    fun `should find balance from repository`() {
        val walletId = UUID.randomUUID()
        every { walletBalanceRepository.findCurrentBalance(walletId) } returns BigDecimal("100.00")
        every { holdRepository.sumActiveAmount(walletId) } returns BigDecimal("30.00")

        val result = service.findBalance(walletId)

        assertThat(result.balance).isEqualByComparingTo(BigDecimal("100.00"))
        assertThat(result.availableBalance).isEqualByComparingTo(BigDecimal("70.00"))
        verify { walletBalanceRepository.findCurrentBalance(walletId) }
        verify { holdRepository.sumActiveAmount(walletId) }
    }

    @Test
    fun `should mark wallet as dirty`() {
        val walletId = UUID.randomUUID()
        every { walletBalanceQueueRepository.insert(walletId) } returns Unit

        service.markWalletForRefresh(walletId)

        verify { walletBalanceQueueRepository.insert(walletId) }
    }

    @Test
    fun `should refresh balances from queued wallets`() {
        val walletIds = listOf(UUID.randomUUID(), UUID.randomUUID())
        every { walletBalanceQueueRepository.claimOldestBatch(any()) } returns walletIds
        every { walletBalanceRepository.refreshBalances(walletIds) } returns Unit

        service.refreshBalance(2)

        verify { walletBalanceQueueRepository.claimOldestBatch(any()) }
        verify { walletBalanceRepository.refreshBalances(walletIds) }
    }

    @Test
    fun `should handle empty queue gracefully`() {
        val emptyList = emptyList<UUID>()
        every { walletBalanceQueueRepository.claimOldestBatch(any()) } returns emptyList
        every { walletBalanceRepository.refreshBalances(emptyList) } returns Unit

        service.refreshBalance(2)

        verify { walletBalanceQueueRepository.claimOldestBatch(any()) }
        verify(exactly = 0) { walletBalanceRepository.refreshBalances(any()) }
    }
}
