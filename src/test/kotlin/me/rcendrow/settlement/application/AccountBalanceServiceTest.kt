package me.rcendrow.settlement.application

import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import me.rcendrow.settlement.domain.account.AccountBalance
import me.rcendrow.settlement.persistence.AccountBalanceRepository
import me.rcendrow.settlement.persistence.HoldRepository
import me.rcendrow.settlement.persistence.LedgerRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.*

class AccountBalanceServiceTest {

    private val accountBalanceRepository: AccountBalanceRepository = mockk()
    private val ledgerRepository: LedgerRepository = mockk()
    private val holdRepository: HoldRepository = mockk()
    private val service = AccountBalanceService(accountBalanceRepository, ledgerRepository, holdRepository)

    @AfterEach
    fun tearDown() {
        clearMocks(accountBalanceRepository, ledgerRepository, holdRepository)
    }

    @Test
    fun `should find balance from repository`() {
        val accountId = UUID.randomUUID()
        every { accountBalanceRepository.findBalance(accountId) } returns BigDecimal("100.00")
        every { holdRepository.sumActiveAmount(accountId) } returns BigDecimal("30.00")

        val result = service.findBalance(accountId)

        assertThat(result.balance).isEqualByComparingTo(BigDecimal("100.00"))
        assertThat(result.availableBalance).isEqualByComparingTo(BigDecimal("70.00"))
        verify { accountBalanceRepository.findBalance(accountId) }
        verify { holdRepository.sumActiveAmount(accountId) }
    }

    @Test
    fun `should fallback to ledger when no balance in repository`() {
        val accountId = UUID.randomUUID()
        every { accountBalanceRepository.findBalance(accountId) } returns null
        every { ledgerRepository.findBalance(accountId) } returns BigDecimal("50.00")
        every { holdRepository.sumActiveAmount(accountId) } returns BigDecimal("10.00")

        val result = service.findBalance(accountId)

        assertThat(result.balance).isEqualByComparingTo(BigDecimal("50.00"))
        assertThat(result.availableBalance).isEqualByComparingTo(BigDecimal("40.00"))
    }

    @Test
    fun `should sync balance`() {
        val accountId = UUID.randomUUID()
        every { ledgerRepository.findBalance(accountId) } returns BigDecimal("200.00")
        every { accountBalanceRepository.upsert(accountId, BigDecimal("200.00")) } returns Unit

        service.sync(accountId)

        verify { ledgerRepository.findBalance(accountId) }
        verify { accountBalanceRepository.upsert(accountId, BigDecimal("200.00")) }
    }

    @Test
    fun `should rebuild all balances`() {
        val balances = mapOf(
            UUID.randomUUID() to BigDecimal("100.00"),
            UUID.randomUUID() to BigDecimal("200.00")
        )
        every { ledgerRepository.findAllBalances() } returns balances
        every { accountBalanceRepository.rebuildBalances(balances) } returns Unit

        service.rebuild()

        verify { ledgerRepository.findAllBalances() }
        verify { accountBalanceRepository.rebuildBalances(balances) }
    }
}
