package me.rcendrow.settlement.application

import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import me.rcendrow.settlement.persistence.HoldRepository
import me.rcendrow.settlement.persistence.account.AccountBalanceQueueRepository
import me.rcendrow.settlement.persistence.account.AccountBalanceRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.*

class AccountBalanceServiceTest {

    private val accountBalanceRepository: AccountBalanceRepository = mockk()
    private val accountBalanceQueueRepository: AccountBalanceQueueRepository = mockk()
    private val holdRepository: HoldRepository = mockk()
    private val service = AccountBalanceService(accountBalanceRepository, accountBalanceQueueRepository, holdRepository)

    @AfterEach
    fun tearDown() {
        clearMocks(accountBalanceRepository, accountBalanceQueueRepository, holdRepository)
    }

    @Test
    fun `should find balance from repository`() {
        val accountId = UUID.randomUUID()
        every { accountBalanceRepository.findCurrentBalance(accountId) } returns BigDecimal("100.00")
        every { holdRepository.sumActiveAmount(accountId) } returns BigDecimal("30.00")

        val result = service.findBalance(accountId)

        assertThat(result.balance).isEqualByComparingTo(BigDecimal("100.00"))
        assertThat(result.availableBalance).isEqualByComparingTo(BigDecimal("70.00"))
        verify { accountBalanceRepository.findCurrentBalance(accountId) }
        verify { holdRepository.sumActiveAmount(accountId) }
    }

    @Test
    fun `should mark account as dirty`() {
        val accountId = UUID.randomUUID()
        every { accountBalanceQueueRepository.insert(accountId) } returns Unit

        service.markAccountForRefresh(accountId)

        verify { accountBalanceQueueRepository.insert(accountId) }
    }

    @Test
    fun `should refresh balances from queued accounts`() {
        val accountIds = listOf(UUID.randomUUID(), UUID.randomUUID())
        every { accountBalanceQueueRepository.claimOldestBatch(any()) } returns accountIds
        every { accountBalanceRepository.refreshBalances(accountIds) } returns Unit

        service.refreshBalance(2)

        verify { accountBalanceQueueRepository.claimOldestBatch(any()) }
        verify { accountBalanceRepository.refreshBalances(accountIds) }
    }

    @Test
    fun `should handle empty queue gracefully`() {
        val emptyList = emptyList<UUID>()
        every { accountBalanceQueueRepository.claimOldestBatch(any()) } returns emptyList
        every { accountBalanceRepository.refreshBalances(emptyList) } returns Unit

        service.refreshBalance(2)

        verify { accountBalanceQueueRepository.claimOldestBatch(any()) }
        verify(exactly = 0) { accountBalanceRepository.refreshBalances(any()) }
    }
}
