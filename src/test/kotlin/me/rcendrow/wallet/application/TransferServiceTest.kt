package me.rcendrow.wallet.application

import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import me.rcendrow.wallet.application.exception.DuplicateIdempotencyKeyException
import me.rcendrow.wallet.application.exception.InsufficientFundsException
import me.rcendrow.wallet.domain.Customer
import me.rcendrow.wallet.domain.Transfer
import me.rcendrow.wallet.domain.wallet.ServiceRole
import me.rcendrow.wallet.domain.wallet.Wallet
import me.rcendrow.wallet.domain.wallet.WalletOwner
import me.rcendrow.wallet.domain.wallet.WalletOwnerType
import me.rcendrow.wallet.domain.wallet.WalletStatus
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
    private val customerService: CustomerService = mockk()
    private val service = TransferService(
        transferRepository,
        walletService,
        ledgerService,
        walletBalanceService,
        customerService,
    )

    @AfterEach
    fun tearDown() {
        clearMocks(transferRepository, walletService, ledgerService, walletBalanceService, customerService)
    }

    private fun activeWallet(id: UUID, ownerId: UUID = UUID.randomUUID()) = Wallet(
        id = id,
        owner = WalletOwner(
            id = ownerId,
            displayName = "display",
            label = "handle",
            type = WalletOwnerType.CUSTOMER,
        ),
        status = WalletStatus.ACTIVE,
        createdAt = LocalDateTime.now()
    )

    private val toCustomerId = UUID.randomUUID()
    private val toHandle = "recipient"
    private val toCustomer = Customer(toCustomerId, toHandle, toHandle, LocalDateTime.now())

    @Test
    fun `should reject zero amount`() {
        val customerId = UUID.randomUUID()
        val fromId = UUID.randomUUID()
        every { walletService.getCustomerWallet(customerId, fromId) } returns activeWallet(fromId, customerId)
        every { customerService.getCustomer(toCustomerId) } returns toCustomer
        every { walletService.getCustomerWallet(toCustomerId) } returns activeWallet(UUID.randomUUID(), toCustomerId)

        assertThatThrownBy {
            service.createTransfer(
                fromCustomerId = customerId,
                fromWallet = fromId,
                toCustomerId = toCustomerId,
                amount = BigDecimal.ZERO,
                idempotencyKey = UUID.randomUUID().toString(),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Amount must be positive")
    }

    @Test
    fun `should reject negative amount`() {
        val customerId = UUID.randomUUID()
        val fromId = UUID.randomUUID()
        every { walletService.getCustomerWallet(customerId, fromId) } returns activeWallet(fromId, customerId)
        every { customerService.getCustomer(toCustomerId) } returns toCustomer
        every { walletService.getCustomerWallet(toCustomerId) } returns activeWallet(UUID.randomUUID(), toCustomerId)

        assertThatThrownBy {
            service.createTransfer(
                fromCustomerId = customerId,
                fromWallet = fromId,
                toCustomerId = toCustomerId,
                amount = BigDecimal("-10.00"),
                idempotencyKey = UUID.randomUUID().toString(),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Amount must be positive")
    }

    @Test
    fun `should create transfer when sender has sufficient balance`() {
        val customerId = UUID.randomUUID()
        val fromId = UUID.randomUUID()
        val toId = UUID.randomUUID()
        val key = UUID.randomUUID().toString()
        val from = activeWallet(fromId, customerId)
        val to = activeWallet(toId, toCustomerId)
        every { walletService.getCustomerWallet(customerId, fromId) } returns from
        every { customerService.getCustomer(toCustomerId) } returns toCustomer
        every { walletService.getCustomerWallet(toCustomerId) } returns to
        every { transferRepository.findByIdempotencyKey(key) } returns null
        every { walletService.lockAndVerifyBalance(from, BigDecimal("50.00")) } returns Unit
        every { transferRepository.create(any()) } answers { firstArg() }
        every { ledgerService.createEntries(any()) } returns mockk()
        every { walletBalanceService.markWalletForRefresh(any()) } returns Unit

        val result = service.createTransfer(customerId, fromId, toCustomerId, BigDecimal("50.00"), key)

        assertThat(result.fromWallet).isEqualTo(fromId)
        assertThat(result.toWallet).isEqualTo(toId)
        assertThat(result.amount).isEqualByComparingTo(BigDecimal("50.00"))
        assertThat(result.idempotencyKey).isEqualTo(key)
        verify { transferRepository.create(any()) }
        verify { ledgerService.createEntries(any()) }
        verify { walletBalanceService.markWalletForRefresh(fromId) }
        verify { walletBalanceService.markWalletForRefresh(toId) }
    }

    @Test
    fun `should throw InsufficientFundsException when balance is less than amount`() {
        val customerId = UUID.randomUUID()
        val fromId = UUID.randomUUID()
        val from = activeWallet(fromId, customerId)
        every { walletService.getCustomerWallet(customerId, fromId) } returns from
        every { customerService.getCustomer(toCustomerId) } returns toCustomer
        every { walletService.getCustomerWallet(toCustomerId) } returns activeWallet(UUID.randomUUID(), toCustomerId)
        every { transferRepository.findByIdempotencyKey(any()) } returns null
        every { walletService.lockAndVerifyBalance(from, BigDecimal("50.00")) } throws
                InsufficientFundsException(fromId, BigDecimal("30.00"), BigDecimal("50.00"))

        assertThatThrownBy {
            service.createTransfer(customerId, fromId, toCustomerId, BigDecimal("50.00"), UUID.randomUUID().toString())
        }.isInstanceOf(InsufficientFundsException::class.java)
    }

    @Test
    fun `should return existing transfer for duplicate idempotency key`() {
        val customerId = UUID.randomUUID()
        val fromId = UUID.randomUUID()
        val key = UUID.randomUUID().toString()
        val existing = mockk<Transfer>()
        every { walletService.getCustomerWallet(customerId, fromId) } returns activeWallet(fromId, customerId)
        every { customerService.getCustomer(toCustomerId) } returns toCustomer
        every { walletService.getCustomerWallet(toCustomerId) } returns activeWallet(UUID.randomUUID(), toCustomerId)
        every { transferRepository.findByIdempotencyKey(key) } returns existing

        val result = service.createTransfer(customerId, fromId, toCustomerId, BigDecimal("50.00"), key)

        assertThat(result).isSameAs(existing)
        verify(exactly = 0) { transferRepository.create(any()) }
    }

    @Test
    fun `should return existing transfer when repository throws DuplicateIdempotencyKeyException`() {
        val customerId = UUID.randomUUID()
        val fromId = UUID.randomUUID()
        val key = UUID.randomUUID().toString()
        val existing = mockk<Transfer>()
        val from = activeWallet(fromId, customerId)
        every { walletService.getCustomerWallet(customerId, fromId) } returns from
        every { customerService.getCustomer(toCustomerId) } returns toCustomer
        every { walletService.getCustomerWallet(toCustomerId) } returns activeWallet(UUID.randomUUID(), toCustomerId)
        every { transferRepository.findByIdempotencyKey(key) } returns null
        every { walletService.lockAndVerifyBalance(from, BigDecimal("50.00")) } returns Unit
        every { transferRepository.create(any()) } throws DuplicateIdempotencyKeyException(key, existing)

        val result = service.createTransfer(customerId, fromId, toCustomerId, BigDecimal("50.00"), key)

        assertThat(result).isSameAs(existing)
        verify(exactly = 0) { ledgerService.createEntries(any()) }
    }

    @Test
    fun `should reject self-transfer`() {
        val customerId = UUID.randomUUID()
        val walletId = UUID.randomUUID()
        val wallet = activeWallet(walletId, customerId)
        every { walletService.getCustomerWallet(customerId, walletId) } returns wallet
        every { customerService.getCustomer(customerId) } returns Customer(customerId, "self", "self", LocalDateTime.now())
        every { walletService.getCustomerWallet(customerId) } returns wallet

        assertThatThrownBy {
            service.createTransfer(
                fromCustomerId = customerId,
                fromWallet = walletId,
                toCustomerId = customerId,
                amount = BigDecimal("50.00"),
                idempotencyKey = UUID.randomUUID().toString(),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("Self-transfer")
    }

    @Test
    fun `should skip balance check for system wallet deposits`() {
        val systemWallet = Wallet(
            id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
            owner = WalletOwner(
                id = UUID.randomUUID(),
                displayName = "settlement",
                label = ServiceRole.EXTERNAL_SETTLEMENT.name,
                type = WalletOwnerType.SERVICE,
            ),
            status = WalletStatus.ACTIVE,
            createdAt = LocalDateTime.now(),
        )
        val customerId = UUID.randomUUID()
        val toId = UUID.randomUUID()
        val to = activeWallet(toId, customerId)
        every { walletService.getServiceWalletByRole(ServiceRole.EXTERNAL_SETTLEMENT) } returns systemWallet
        every { walletService.getCustomerWallet(customerId, toId) } returns to
        every { transferRepository.findByIdempotencyKey(any()) } returns null
        every { transferRepository.create(any()) } answers { firstArg() }
        every { ledgerService.createEntries(any()) } returns mockk()
        every { walletBalanceService.markWalletForRefresh(any()) } returns Unit

        service.createDeposit(customerId, toId, BigDecimal("50.00"), UUID.randomUUID().toString())

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
        val customerId = UUID.randomUUID()
        val walletId = UUID.randomUUID()
        val pageable = PageRequest.of(0, 10)
        val page: Page<Transfer> = PageImpl(emptyList())
        every { walletService.getCustomerWallet(customerId, walletId) } returns mockk()
        every { transferRepository.findByWalletId(walletId, pageable) } returns page

        val result = service.getWalletTransfers(customerId, walletId, pageable)

        assertThat(result).isSameAs(page)
        verify { walletService.getCustomerWallet(customerId, walletId) }
    }
}
