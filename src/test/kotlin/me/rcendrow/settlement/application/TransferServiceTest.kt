package me.rcendrow.settlement.application

import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import me.rcendrow.settlement.application.exception.DuplicateIdempotencyKeyException
import me.rcendrow.settlement.application.exception.InsufficientFundsException
import me.rcendrow.settlement.domain.EntryType
import me.rcendrow.settlement.domain.Transfer
import me.rcendrow.settlement.persistence.TransferRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.math.BigDecimal
import java.util.*

class TransferServiceTest {

    private val transferRepository: TransferRepository = mockk()
    private val accountService: AccountService = mockk()
    private val ledgerService: LedgerService = mockk()
    private val service = TransferService(transferRepository, accountService, ledgerService)

    @AfterEach
    fun tearDown() {
        clearMocks(transferRepository, accountService, ledgerService)
    }

    @Test
    fun `should reject zero amount`() {
        every { accountService.lockAccount(any()) } returns Unit
        every { accountService.getAccount(any()) } returns mockk()

        assertThatThrownBy {
            service.createTransfer(
                fromAccount = UUID.randomUUID(),
                toAccount = UUID.randomUUID(),
                amount = BigDecimal.ZERO,
                idempotencyKey = UUID.randomUUID().toString(),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Amount must be positive")
    }

    @Test
    fun `should reject negative amount`() {
        every { accountService.lockAccount(any()) } returns Unit
        every { accountService.getAccount(any()) } returns mockk()

        assertThatThrownBy {
            service.createTransfer(
                fromAccount = UUID.randomUUID(),
                toAccount = UUID.randomUUID(),
                amount = BigDecimal("-10.00"),
                idempotencyKey = UUID.randomUUID().toString(),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Amount must be positive")
    }

    @Test
    fun `should lock sender and verify receiver account`() {
        val fromAccount = UUID.randomUUID()
        val toAccount = UUID.randomUUID()
        every { accountService.lockAccount(fromAccount) } returns Unit
        every { accountService.getAccount(toAccount) } returns mockk()
        every { transferRepository.findByIdempotencyKey(any()) } returns null
        every { ledgerService.findBalance(fromAccount) } returns BigDecimal("100.00")
        every { transferRepository.create(any()) } answers { firstArg() }
        every { ledgerService.createEntry(any(), EntryType.DEBIT) } returns mockk()
        every { ledgerService.createEntry(any(), EntryType.CREDIT) } returns mockk()

        service.createTransfer(fromAccount, toAccount, BigDecimal("50.00"), UUID.randomUUID().toString())

        verify { accountService.lockAccount(fromAccount) }
        verify { accountService.getAccount(toAccount) }
    }

    @Test
    fun `should throw InsufficientFundsException when balance is less than amount`() {
        val fromAccount = UUID.randomUUID()
        val toAccount = UUID.randomUUID()
        every { accountService.lockAccount(fromAccount) } returns Unit
        every { accountService.getAccount(toAccount) } returns mockk()
        every { transferRepository.findByIdempotencyKey(any()) } returns null
        every { ledgerService.findBalance(fromAccount) } returns BigDecimal("30.00")

        assertThatThrownBy {
            service.createTransfer(fromAccount, toAccount, BigDecimal("50.00"), UUID.randomUUID().toString())
        }.isInstanceOf(InsufficientFundsException::class.java)
    }

    @Test
    fun `should create transfer when sender has sufficient balance`() {
        val fromAccount = UUID.randomUUID()
        val toAccount = UUID.randomUUID()
        val key = UUID.randomUUID().toString()
        every { accountService.lockAccount(fromAccount) } returns Unit
        every { accountService.getAccount(toAccount) } returns mockk()
        every { transferRepository.findByIdempotencyKey(key) } returns null
        every { ledgerService.findBalance(fromAccount) } returns BigDecimal("100.00")
        every { transferRepository.create(any()) } answers { firstArg() }
        every { ledgerService.createEntry(any(), EntryType.DEBIT) } returns mockk()
        every { ledgerService.createEntry(any(), EntryType.CREDIT) } returns mockk()

        val result = service.createTransfer(fromAccount, toAccount, BigDecimal("50.00"), key)

        assertThat(result.fromAccount).isEqualTo(fromAccount)
        assertThat(result.toAccount).isEqualTo(toAccount)
        assertThat(result.amount).isEqualByComparingTo(BigDecimal("50.00"))
        assertThat(result.idempotencyKey).isEqualTo(key)
        verify { transferRepository.create(any()) }
        verify { ledgerService.createEntry(any(), EntryType.DEBIT) }
        verify { ledgerService.createEntry(any(), EntryType.CREDIT) }
    }

    @Test
    fun `should return existing transfer for duplicate idempotency key`() {
        val fromAccount = UUID.randomUUID()
        val toAccount = UUID.randomUUID()
        val key = UUID.randomUUID().toString()
        val existing = mockk<Transfer>()
        every { accountService.lockAccount(fromAccount) } returns Unit
        every { accountService.getAccount(toAccount) } returns mockk()
        every { transferRepository.findByIdempotencyKey(key) } returns existing

        val result = service.createTransfer(fromAccount, toAccount, BigDecimal("50.00"), key)

        assertThat(result).isSameAs(existing)
        verify(exactly = 0) { transferRepository.create(any()) }
    }

    @Test
    fun `should return existing transfer when repository throws DuplicateIdempotencyKeyException`() {
        val fromAccount = UUID.randomUUID()
        val toAccount = UUID.randomUUID()
        val key = UUID.randomUUID().toString()
        val existing = mockk<Transfer>()
        every { accountService.lockAccount(fromAccount) } returns Unit
        every { accountService.getAccount(toAccount) } returns mockk()
        every { transferRepository.findByIdempotencyKey(key) } returns null
        every { ledgerService.findBalance(fromAccount) } returns BigDecimal("100.00")
        every { transferRepository.create(any()) } throws DuplicateIdempotencyKeyException(key, existing)

        val result = service.createTransfer(fromAccount, toAccount, BigDecimal("50.00"), key)

        assertThat(result).isSameAs(existing)
        verify(exactly = 0) { ledgerService.createEntry(any(), any()) }
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
            .hasMessageContaining("Transfer not found")
    }

    @Test
    fun `should get account transfers with pagination`() {
        val accountId = UUID.randomUUID()
        val pageable = PageRequest.of(0, 10)
        val page: Page<Transfer> = PageImpl(emptyList())
        every { accountService.getAccount(accountId) } returns mockk()
        every { transferRepository.findByAccountId(accountId, pageable) } returns page

        val result = service.getAccountTransfers(accountId, pageable)

        assertThat(result).isSameAs(page)
        verify { accountService.getAccount(accountId) }
    }
}
