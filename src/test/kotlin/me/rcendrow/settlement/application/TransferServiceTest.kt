package me.rcendrow.settlement.application

import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import me.rcendrow.settlement.application.exception.DuplicateIdempotencyKeyException
import me.rcendrow.settlement.application.exception.InsufficientFundsException
import me.rcendrow.settlement.domain.Transfer
import me.rcendrow.settlement.domain.account.AccountStatus
import me.rcendrow.settlement.domain.account.CustomerAccount
import me.rcendrow.settlement.domain.account.ServiceAccount
import me.rcendrow.settlement.domain.account.ServiceAccountRole
import me.rcendrow.settlement.persistence.TransferRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

class TransferServiceTest {

    private val transferRepository: TransferRepository = mockk()
    private val accountService: AccountService = mockk()
    private val ledgerService: LedgerService = mockk()
    private val accountBalanceService: AccountBalanceService = mockk()
    private val service = TransferService(
        transferRepository,
        accountService,
        ledgerService,
        accountBalanceService
    )

    @AfterEach
    fun tearDown() {
        clearMocks(transferRepository, accountService, ledgerService, accountBalanceService)
    }

    private fun activeAccount(id: UUID) = CustomerAccount(
        id = id, customerId = UUID.randomUUID(), status = AccountStatus.ACTIVE, createdAt = LocalDateTime.now()
    )

    @Test
    fun `should reject zero amount`() {
        val fromId = UUID.randomUUID()
        val toId = UUID.randomUUID()
        every { accountService.getCustomerAccount(fromId) } returns activeAccount(fromId)
        every { accountService.getCustomerAccount(toId) } returns activeAccount(toId)

        assertThatThrownBy {
            service.createTransfer(
                fromAccount = fromId,
                toAccount = toId,
                amount = BigDecimal.ZERO,
                idempotencyKey = UUID.randomUUID().toString(),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Amount must be positive")
    }

    @Test
    fun `should reject negative amount`() {
        val fromId = UUID.randomUUID()
        val toId = UUID.randomUUID()
        every { accountService.getCustomerAccount(fromId) } returns activeAccount(fromId)
        every { accountService.getCustomerAccount(toId) } returns activeAccount(toId)

        assertThatThrownBy {
            service.createTransfer(
                fromAccount = fromId,
                toAccount = toId,
                amount = BigDecimal("-10.00"),
                idempotencyKey = UUID.randomUUID().toString(),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Amount must be positive")
    }

    @Test
    fun `should create transfer when sender has sufficient balance`() {
        val fromId = UUID.randomUUID()
        val toId = UUID.randomUUID()
        val key = UUID.randomUUID().toString()
        val from = activeAccount(fromId)
        val to = activeAccount(toId)
        every { accountService.getCustomerAccount(fromId) } returns from
        every { accountService.getCustomerAccount(toId) } returns to
        every { transferRepository.findByIdempotencyKey(key) } returns null
        every { accountService.lockAndVerifyBalance(from, BigDecimal("50.00")) } returns Unit
        every { transferRepository.create(any()) } answers { firstArg() }
        every { ledgerService.createCreditEntry(any()) } returns mockk()
        every { ledgerService.createDebitEntry(any()) } returns mockk()

        val result = service.createTransfer(fromId, toId, BigDecimal("50.00"), key)

        assertThat(result.fromAccount).isEqualTo(fromId)
        assertThat(result.toAccount).isEqualTo(toId)
        assertThat(result.amount).isEqualByComparingTo(BigDecimal("50.00"))
        assertThat(result.idempotencyKey).isEqualTo(key)
        verify { transferRepository.create(any()) }
        verify { ledgerService.createCreditEntry(any()) }
        verify { ledgerService.createDebitEntry(any()) }
        verify { accountBalanceService.markAccountForRefresh(fromId) }
        verify { accountBalanceService.markAccountForRefresh(toId) }
    }

    @Test
    fun `should throw InsufficientFundsException when balance is less than amount`() {
        val fromId = UUID.randomUUID()
        val toId = UUID.randomUUID()
        val from = activeAccount(fromId)
        val to = activeAccount(toId)
        every { accountService.getCustomerAccount(fromId) } returns from
        every { accountService.getCustomerAccount(toId) } returns to
        every { transferRepository.findByIdempotencyKey(any()) } returns null
        every { accountService.lockAndVerifyBalance(from, BigDecimal("50.00")) } throws
                InsufficientFundsException(fromId, BigDecimal("30.00"), BigDecimal("50.00"))

        assertThatThrownBy {
            service.createTransfer(fromId, toId, BigDecimal("50.00"), UUID.randomUUID().toString())
        }.isInstanceOf(InsufficientFundsException::class.java)
    }

    @Test
    fun `should return existing transfer for duplicate idempotency key`() {
        val fromId = UUID.randomUUID()
        val toId = UUID.randomUUID()
        val key = UUID.randomUUID().toString()
        val existing = mockk<Transfer>()
        every { accountService.getCustomerAccount(fromId) } returns activeAccount(fromId)
        every { accountService.getCustomerAccount(toId) } returns activeAccount(toId)
        every { transferRepository.findByIdempotencyKey(key) } returns existing

        val result = service.createTransfer(fromId, toId, BigDecimal("50.00"), key)

        assertThat(result).isSameAs(existing)
        verify(exactly = 0) { transferRepository.create(any()) }
    }

    @Test
    fun `should return existing transfer when repository throws DuplicateIdempotencyKeyException`() {
        val fromId = UUID.randomUUID()
        val toId = UUID.randomUUID()
        val key = UUID.randomUUID().toString()
        val existing = mockk<Transfer>()
        val from = activeAccount(fromId)
        val to = activeAccount(toId)
        every { accountService.getCustomerAccount(fromId) } returns from
        every { accountService.getCustomerAccount(toId) } returns to
        every { transferRepository.findByIdempotencyKey(key) } returns null
        every { accountService.lockAndVerifyBalance(from, BigDecimal("50.00")) } returns Unit
        every { transferRepository.create(any()) } throws DuplicateIdempotencyKeyException(key, existing)

        val result = service.createTransfer(fromId, toId, BigDecimal("50.00"), key)

        assertThat(result).isSameAs(existing)
        verify(exactly = 0) { ledgerService.createCreditEntry(any()) }
        verify(exactly = 0) { ledgerService.createDebitEntry(any()) }
    }

    @Test
    fun `should skip balance check for system account deposits`() {
        val systemAccount = ServiceAccount(
            id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
            status = AccountStatus.ACTIVE,
            createdAt = LocalDateTime.now(),
            role = ServiceAccountRole.EXTERNAL_SETTLEMENT
        )
        val toId = UUID.randomUUID()
        val to = activeAccount(toId)
        every { accountService.getServiceAccountByRole(ServiceAccountRole.EXTERNAL_SETTLEMENT) } returns systemAccount
        every { accountService.getCustomerAccount(toId) } returns to
        every { transferRepository.findByIdempotencyKey(any()) } returns null
        every { transferRepository.create(any()) } answers { firstArg() }
        every { ledgerService.createCreditEntry(any()) } returns mockk()
        every { ledgerService.createDebitEntry(any()) } returns mockk()

        service.createDeposit(toId, BigDecimal("50.00"), UUID.randomUUID().toString())

        verify(exactly = 0) { accountBalanceService.findBalance(systemAccount.id) }
    }

    @Test
    fun `should get transfer by id`() {
        val id = UUID.randomUUID()
        val transfer = mockk<Transfer>()
        every { transferRepository.findById(id) } returns transfer

        val result = service.getTransfer(id)

        assertThat(result).isSameAs(transfer)
    }

    @Test
    fun `should throw when transfer not found`() {
        val id = UUID.randomUUID()
        every { transferRepository.findById(id) } returns null

        assertThatThrownBy { service.getTransfer(id) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("not found")
    }

    @Test
    fun `should get account transfers with pagination`() {
        val accountId = UUID.randomUUID()
        val pageable = PageRequest.of(0, 10)
        val page: Page<Transfer> = PageImpl(emptyList())
        every { accountService.getCustomerAccount(accountId) } returns mockk()
        every { transferRepository.findByAccountId(accountId, pageable) } returns page

        val result = service.getAccountTransfers(accountId, pageable)

        assertThat(result).isSameAs(page)
        verify { accountService.getCustomerAccount(accountId) }
    }
}
