package me.rcendrow.wallet.application

import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import me.rcendrow.wallet.application.exception.DuplicateIdempotencyKeyException
import me.rcendrow.wallet.application.exception.InsufficientFundsException
import me.rcendrow.wallet.domain.Transfer
import me.rcendrow.wallet.domain.wallet.WalletStatus
import me.rcendrow.wallet.domain.wallet.CustomerWallet
import me.rcendrow.wallet.domain.wallet.ServiceWallet
import me.rcendrow.wallet.domain.wallet.ServiceWalletRole
import me.rcendrow.wallet.persistence.TransferRepository
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
    private val walletService: WalletService = mockk()
    private val ledgerService: LedgerService = mockk()
    private val walletBalanceService: WalletBalanceService = mockk()
    private val service = TransferService(
        transferRepository,
        walletService,
        ledgerService,
        walletBalanceService
    )

    @AfterEach
    fun tearDown() {
        clearMocks(transferRepository, walletService, ledgerService, walletBalanceService)
    }

    private fun activeWallet(id: UUID) = CustomerWallet(
        id = id, customerId = UUID.randomUUID(), status = WalletStatus.ACTIVE, createdAt = LocalDateTime.now()
    )

    @Test
    fun `should reject zero amount`() {
        val fromId = UUID.randomUUID()
        val toId = UUID.randomUUID()
        every { walletService.getCustomerWallet(fromId) } returns activeWallet(fromId)
        every { walletService.getCustomerWallet(toId) } returns activeWallet(toId)

        assertThatThrownBy {
            service.createTransfer(
                fromWallet = fromId,
                toWallet = toId,
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
        every { walletService.getCustomerWallet(fromId) } returns activeWallet(fromId)
        every { walletService.getCustomerWallet(toId) } returns activeWallet(toId)

        assertThatThrownBy {
            service.createTransfer(
                fromWallet = fromId,
                toWallet = toId,
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
        val from = activeWallet(fromId)
        val to = activeWallet(toId)
        every { walletService.getCustomerWallet(fromId) } returns from
        every { walletService.getCustomerWallet(toId) } returns to
        every { transferRepository.findByIdempotencyKey(key) } returns null
        every { walletService.lockAndVerifyBalance(from, BigDecimal("50.00")) } returns Unit
        every { transferRepository.create(any()) } answers { firstArg() }
        every { ledgerService.createCreditEntry(any()) } returns mockk()
        every { ledgerService.createDebitEntry(any()) } returns mockk()
        every { walletBalanceService.markWalletForRefresh(any()) } returns Unit

        val result = service.createTransfer(fromId, toId, BigDecimal("50.00"), key)

        assertThat(result.fromWallet).isEqualTo(fromId)
        assertThat(result.toWallet).isEqualTo(toId)
        assertThat(result.amount).isEqualByComparingTo(BigDecimal("50.00"))
        assertThat(result.idempotencyKey).isEqualTo(key)
        verify { transferRepository.create(any()) }
        verify { ledgerService.createCreditEntry(any()) }
        verify { ledgerService.createDebitEntry(any()) }
        verify { walletBalanceService.markWalletForRefresh(fromId) }
        verify { walletBalanceService.markWalletForRefresh(toId) }
    }

    @Test
    fun `should throw InsufficientFundsException when balance is less than amount`() {
        val fromId = UUID.randomUUID()
        val toId = UUID.randomUUID()
        val from = activeWallet(fromId)
        val to = activeWallet(toId)
        every { walletService.getCustomerWallet(fromId) } returns from
        every { walletService.getCustomerWallet(toId) } returns to
        every { transferRepository.findByIdempotencyKey(any()) } returns null
        every { walletService.lockAndVerifyBalance(from, BigDecimal("50.00")) } throws
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
        every { walletService.getCustomerWallet(fromId) } returns activeWallet(fromId)
        every { walletService.getCustomerWallet(toId) } returns activeWallet(toId)
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
        val from = activeWallet(fromId)
        val to = activeWallet(toId)
        every { walletService.getCustomerWallet(fromId) } returns from
        every { walletService.getCustomerWallet(toId) } returns to
        every { transferRepository.findByIdempotencyKey(key) } returns null
        every { walletService.lockAndVerifyBalance(from, BigDecimal("50.00")) } returns Unit
        every { transferRepository.create(any()) } throws DuplicateIdempotencyKeyException(key, existing)

        val result = service.createTransfer(fromId, toId, BigDecimal("50.00"), key)

        assertThat(result).isSameAs(existing)
        verify(exactly = 0) { ledgerService.createCreditEntry(any()) }
        verify(exactly = 0) { ledgerService.createDebitEntry(any()) }
    }

    @Test
    fun `should reject self-transfer`() {
        val walletId = UUID.randomUUID()
        every { walletService.getCustomerWallet(walletId) } returns activeWallet(walletId)

        assertThatThrownBy {
            service.createTransfer(
                fromWallet = walletId,
                toWallet = walletId,
                amount = BigDecimal("50.00"),
                idempotencyKey = UUID.randomUUID().toString(),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Self-transfer")
    }

    @Test
    fun `should skip balance check for system wallet deposits`() {
        val systemWallet = ServiceWallet(
            id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
            status = WalletStatus.ACTIVE,
            createdAt = LocalDateTime.now(),
            role = ServiceWalletRole.EXTERNAL_SETTLEMENT
        )
        val toId = UUID.randomUUID()
        val to = activeWallet(toId)
        every { walletService.getServiceWalletByRole(ServiceWalletRole.EXTERNAL_SETTLEMENT) } returns systemWallet
        every { walletService.getCustomerWallet(toId) } returns to
        every { transferRepository.findByIdempotencyKey(any()) } returns null
        every { transferRepository.create(any()) } answers { firstArg() }
        every { ledgerService.createCreditEntry(any()) } returns mockk()
        every { ledgerService.createDebitEntry(any()) } returns mockk()
        every { walletBalanceService.markWalletForRefresh(any()) } returns Unit

        service.createDeposit(toId, BigDecimal("50.00"), UUID.randomUUID().toString())

        verify(exactly = 0) { walletBalanceService.findBalance(systemWallet.id) }
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
    fun `should get wallet transfers with pagination`() {
        val walletId = UUID.randomUUID()
        val pageable = PageRequest.of(0, 10)
        val page: Page<Transfer> = PageImpl(emptyList())
        every { walletService.getCustomerWallet(walletId) } returns mockk()
        every { transferRepository.findByWalletId(walletId, pageable) } returns page

        val result = service.getWalletTransfers(walletId, pageable)

        assertThat(result).isSameAs(page)
        verify { walletService.getCustomerWallet(walletId) }
    }
}
