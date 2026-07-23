package me.rcendrow.settlement.application

import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import me.rcendrow.settlement.application.exception.AccountStatusException
import me.rcendrow.settlement.application.exception.InsufficientFundsException
import me.rcendrow.settlement.domain.Hold
import me.rcendrow.settlement.domain.HoldStatus
import me.rcendrow.settlement.domain.Transfer
import me.rcendrow.settlement.domain.account.AccountBalance
import me.rcendrow.settlement.domain.account.AccountStatus
import me.rcendrow.settlement.domain.account.CustomerAccount
import me.rcendrow.settlement.persistence.HoldRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

class HoldServiceTest {

    private val holdRepository: HoldRepository = mockk()
    private val accountService: AccountService = mockk()
    private val transferService: TransferService = mockk()
    private val service = HoldService(holdRepository, accountService, transferService)

    @AfterEach
    fun tearDown() {
        clearMocks(holdRepository, accountService, transferService)
    }

    @Test
    fun `should place hold when sufficient available balance`() {
        val accountId = UUID.randomUUID()
        val expiresAt = LocalDateTime.now().plusDays(1)
        val account = CustomerAccount(
            id = accountId,
            customerId = UUID.randomUUID(),
            status = AccountStatus.ACTIVE,
            createdAt = LocalDateTime.now()
        )
        every { accountService.getCustomerAccount(accountId) } returns account
        every { accountService.lockAndVerifyBalance(account, BigDecimal("30.00")) } returns Unit
        every { holdRepository.create(any()) } answers { firstArg() }

        val result = service.placeHold(accountId, BigDecimal("30.00"), expiresAt)

        assertThat(result.accountId).isEqualTo(accountId)
        assertThat(result.amount).isEqualByComparingTo(BigDecimal("30.00"))
        assertThat(result.status).isEqualTo(HoldStatus.ACTIVE)
        assertThat(result.expiresAt).isEqualTo(expiresAt)
        verify { holdRepository.create(result) }
    }

    @Test
    fun `should reject hold for non-active account`() {
        val accountId = UUID.randomUUID()
        val account = CustomerAccount(
            id = accountId,
            customerId = UUID.randomUUID(),
            status = AccountStatus.SUSPENDED,
            createdAt = LocalDateTime.now()
        )
        every { accountService.getCustomerAccount(accountId) } returns account

        assertThatThrownBy {
            service.placeHold(accountId, BigDecimal("30.00"), LocalDateTime.now().plusDays(1))
        }.isInstanceOf(AccountStatusException::class.java)
    }

    @Test
    fun `should reject hold when available balance insufficient`() {
        val accountId = UUID.randomUUID()
        val account = CustomerAccount(
            id = accountId,
            customerId = UUID.randomUUID(),
            status = AccountStatus.ACTIVE,
            createdAt = LocalDateTime.now()
        )
        every { accountService.getCustomerAccount(accountId) } returns account
        every { accountService.lockAndVerifyBalance(account, BigDecimal("30.00")) } throws
            InsufficientFundsException(accountId, BigDecimal("20.00"), BigDecimal("30.00"))

        assertThatThrownBy {
            service.placeHold(accountId, BigDecimal("30.00"), LocalDateTime.now().plusDays(1))
        }.isInstanceOf(InsufficientFundsException::class.java)
    }

    @Test
    fun `should capture hold and create transfer`() {
        val holdId = UUID.randomUUID()
        val accountId = UUID.randomUUID()
        val toAccount = UUID.randomUUID()
        val hold = Hold(
            holdId,
            accountId,
            BigDecimal("50.00"),
            HoldStatus.ACTIVE,
            LocalDateTime.now().plusDays(1),
            LocalDateTime.now()
        )
        val transfer = mockk<Transfer>()
        every { holdRepository.findById(holdId) } returns hold
        every {
            transferService.createTransfer(
                accountId,
                toAccount,
                BigDecimal("50.00"),
                "hold-$holdId"
            )
        } returns transfer
        every { holdRepository.updateStatus(hold, HoldStatus.CAPTURED) } returns hold

        val result = service.captureHold(holdId, toAccount)

        assertThat(result).isSameAs(transfer)
        verify { holdRepository.updateStatus(hold, HoldStatus.CAPTURED) }
    }

    @Test
    fun `should release hold`() {
        val holdId = UUID.randomUUID()
        val hold = Hold(
            holdId,
            UUID.randomUUID(),
            BigDecimal("50.00"),
            HoldStatus.ACTIVE,
            LocalDateTime.now().plusDays(1),
            LocalDateTime.now()
        )
        every { holdRepository.findById(holdId) } returns hold
        every { holdRepository.updateStatus(hold, HoldStatus.RELEASED) } returns hold

        service.releaseHold(holdId)

        verify { holdRepository.updateStatus(hold, HoldStatus.RELEASED) }
    }

    @Test
    fun `should release expired holds`() {
        val holdId = UUID.randomUUID()
        val expiredHold = Hold(
            holdId,
            UUID.randomUUID(),
            BigDecimal("50.00"),
            HoldStatus.ACTIVE,
            LocalDateTime.now().minusMinutes(1),
            LocalDateTime.now()
        )
        every { holdRepository.findExpiredActiveHolds() } returns listOf(expiredHold)
        every { holdRepository.updateStatus(expiredHold, HoldStatus.RELEASED) } returns expiredHold

        service.releaseExpiredHolds()

        verify { holdRepository.updateStatus(expiredHold, HoldStatus.RELEASED) }
    }
}
