package me.rcendrow.wallet.application

import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import me.rcendrow.wallet.application.exception.InsufficientFundsException
import me.rcendrow.wallet.application.exception.NotFoundException
import me.rcendrow.wallet.application.exception.WalletStatusException
import me.rcendrow.wallet.domain.Customer
import me.rcendrow.wallet.domain.wallet.Wallet
import me.rcendrow.wallet.domain.wallet.WalletBalance
import me.rcendrow.wallet.domain.wallet.WalletOwner
import me.rcendrow.wallet.domain.wallet.WalletStatus
import me.rcendrow.wallet.persistence.ServiceRepository
import me.rcendrow.wallet.persistence.wallet.WalletBalanceRepository
import me.rcendrow.wallet.persistence.wallet.WalletRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

class WalletServiceTest {

    private val customerService: CustomerService = mockk()
    private val walletRepository: WalletRepository = mockk()
    private val walletBalanceService: WalletBalanceService = mockk()
    private val walletBalanceRepository: WalletBalanceRepository = mockk()
    private val serviceRepository: ServiceRepository = mockk()
    private val service =
        WalletService(
            customerService,
            walletBalanceService,
            walletRepository,
            walletBalanceRepository,
            serviceRepository,
        )

    @AfterEach
    fun tearDown() {
        clearMocks(
            customerService,
            walletRepository,
            walletBalanceService,
            walletBalanceRepository,
        )
    }

    @Test
    fun `should create wallet`() {
        val customerId = UUID.randomUUID()
        val customer =
            Customer(id = customerId, handle = "handle", displayName = "display", createdAt = LocalDateTime.now())
        every { customerService.getCustomer(customerId) } returns customer
        every { walletRepository.create(any()) } answers { firstArg() }
        every { walletBalanceRepository.create(any()) } returns Unit

        val result = service.createCustomerWallet(customerId)

        assertThat(result.owner.id).isEqualTo(customerId)
        assertThat(result.status).isEqualTo(WalletStatus.ACTIVE)
        assertThat(result.id).isNotNull
        assertThat(result.createdAt).isNotNull
        verify { walletRepository.create(result) }
        verify { walletBalanceRepository.create(result.id) }
    }

    @Test
    fun `should return wallet by customer id and wallet id`() {
        val customerId = UUID.randomUUID()
        val id = UUID.randomUUID()
        val wallet = walletWithOwner(customerId, WalletStatus.ACTIVE)
        every { walletRepository.findByCustomerIdAndWalletId(customerId, id) } returns wallet

        val result = service.getCustomerWallet(customerId, id)

        assertThat(result).isEqualTo(wallet)
    }

    @Test
    fun `should throw NotFoundException for unknown wallet`() {
        val customerId = UUID.randomUUID()
        val id = UUID.randomUUID()
        every { walletRepository.findByCustomerIdAndWalletId(customerId, id) } returns null

        assertThatThrownBy { service.getCustomerWallet(customerId, id) }
            .isInstanceOf(NotFoundException::class.java)
    }

    @Test
    fun `should return wallet by customer id`() {
        val customerId = UUID.randomUUID()
        val wallet = walletWithOwner(customerId, WalletStatus.ACTIVE)
        every { walletRepository.findByCustomerId(customerId) } returns wallet

        val result = service.getCustomerWallet(customerId)

        assertThat(result).isEqualTo(wallet)
    }

    @Test
    fun `should throw NotFoundException when customer has no wallet`() {
        val customerId = UUID.randomUUID()
        every { walletRepository.findByCustomerId(customerId) } returns null

        assertThatThrownBy { service.getCustomerWallet(customerId) }
            .isInstanceOf(NotFoundException::class.java)
    }

    @Test
    fun `should return wallet by wallet id`() {
        val wallet = walletWithOwner(UUID.randomUUID(), WalletStatus.ACTIVE)
        every { walletRepository.findById(wallet.id) } returns wallet

        val result = service.getCustomerWalletById(wallet.id)

        assertThat(result).isEqualTo(wallet)
    }

    @Test
    fun `should throw NotFoundException for unknown wallet id`() {
        val walletId = UUID.randomUUID()
        every { walletRepository.findById(walletId) } returns null

        assertThatThrownBy { service.getCustomerWalletById(walletId) }
            .isInstanceOf(NotFoundException::class.java)
    }

    @Test
    fun `should return balance with available balance`() {
        val customerId = UUID.randomUUID()
        val wallet = walletWithOwner(customerId, WalletStatus.ACTIVE)
        every { walletRepository.findByCustomerIdAndWalletId(customerId, wallet.id) } returns wallet
        every { walletBalanceService.findBalance(wallet.id) } returns WalletBalance(
            walletId = wallet.id,
            balance = BigDecimal("100.00"),
            activeHolds = BigDecimal("30.00")
        )

        val result = service.getBalance(customerId, wallet.id)

        assertThat(result.balance).isEqualByComparingTo(BigDecimal("100.00"))
        assertThat(result.availableBalance).isEqualByComparingTo(BigDecimal("70.00"))
    }

    @Test
    fun `should suspend active wallet by customer`() {
        val customerId = UUID.randomUUID()
        val wallet = walletWithOwner(customerId, WalletStatus.ACTIVE)
        every { walletRepository.findByCustomerIdAndWalletId(customerId, wallet.id) } returns wallet
        every {
            walletRepository.updateStatus(
                wallet,
                WalletStatus.SUSPENDED
            )
        } returns wallet.copy(status = WalletStatus.SUSPENDED)

        val result = service.updateWalletStatus(customerId, wallet.id, WalletStatus.SUSPENDED)

        assertThat(result.status).isEqualTo(WalletStatus.SUSPENDED)
    }

    @Test
    fun `should suspend active wallet by admin`() {
        val wallet = walletWithOwner(UUID.randomUUID(), WalletStatus.ACTIVE)
        every { walletRepository.findById(wallet.id) } returns wallet
        every {
            walletRepository.updateStatus(
                wallet,
                WalletStatus.SUSPENDED
            )
        } returns wallet.copy(status = WalletStatus.SUSPENDED)

        val result = service.updateWalletStatus(wallet.id, WalletStatus.SUSPENDED)

        assertThat(result.status).isEqualTo(WalletStatus.SUSPENDED)
    }

    @Test
    fun `should allow updating status of non-closed wallet`() {
        val customerId = UUID.randomUUID()
        val wallet = walletWithOwner(customerId, WalletStatus.SUSPENDED)
        every { walletRepository.findByCustomerIdAndWalletId(customerId, wallet.id) } returns wallet
        every {
            walletRepository.updateStatus(
                wallet,
                WalletStatus.SUSPENDED
            )
        } returns wallet.copy(status = WalletStatus.SUSPENDED)

        val result = service.updateWalletStatus(customerId, wallet.id, WalletStatus.SUSPENDED)

        assertThat(result.status).isEqualTo(WalletStatus.SUSPENDED)
    }

    @Test
    fun `should close active wallet`() {
        val customerId = UUID.randomUUID()
        val wallet = walletWithOwner(customerId, WalletStatus.ACTIVE)
        every { walletRepository.findByCustomerIdAndWalletId(customerId, wallet.id) } returns wallet
        every {
            walletRepository.updateStatus(
                wallet,
                WalletStatus.CLOSED
            )
        } returns wallet.copy(status = WalletStatus.CLOSED)

        val result = service.updateWalletStatus(customerId, wallet.id, WalletStatus.CLOSED)

        assertThat(result.status).isEqualTo(WalletStatus.CLOSED)
    }

    @Test
    fun `should close suspended wallet`() {
        val customerId = UUID.randomUUID()
        val wallet = walletWithOwner(customerId, WalletStatus.SUSPENDED)
        every { walletRepository.findByCustomerIdAndWalletId(customerId, wallet.id) } returns wallet
        every {
            walletRepository.updateStatus(
                wallet,
                WalletStatus.CLOSED
            )
        } returns wallet.copy(status = WalletStatus.CLOSED)

        val result = service.updateWalletStatus(customerId, wallet.id, WalletStatus.CLOSED)

        assertThat(result.status).isEqualTo(WalletStatus.CLOSED)
    }

    @Test
    fun `should not close already closed wallet`() {
        val customerId = UUID.randomUUID()
        val wallet = walletWithOwner(customerId, WalletStatus.CLOSED)
        every { walletRepository.findByCustomerIdAndWalletId(customerId, wallet.id) } returns wallet

        assertThatThrownBy { service.updateWalletStatus(customerId, wallet.id, WalletStatus.CLOSED) }
            .isInstanceOf(WalletStatusException::class.java)
    }

    @Test
    fun `should activate suspended wallet`() {
        val customerId = UUID.randomUUID()
        val wallet = walletWithOwner(customerId, WalletStatus.SUSPENDED)
        every { walletRepository.findByCustomerIdAndWalletId(customerId, wallet.id) } returns wallet
        every {
            walletRepository.updateStatus(
                wallet,
                WalletStatus.ACTIVE
            )
        } returns wallet.copy(status = WalletStatus.ACTIVE)

        val result = service.updateWalletStatus(customerId, wallet.id, WalletStatus.ACTIVE)

        assertThat(result.status).isEqualTo(WalletStatus.ACTIVE)
    }

    @Test
    fun `should allow reactivating active wallet`() {
        val customerId = UUID.randomUUID()
        val wallet = walletWithOwner(customerId, WalletStatus.ACTIVE)
        every { walletRepository.findByCustomerIdAndWalletId(customerId, wallet.id) } returns wallet
        every {
            walletRepository.updateStatus(
                wallet,
                WalletStatus.ACTIVE
            )
        } returns wallet.copy(status = WalletStatus.ACTIVE)

        val result = service.updateWalletStatus(customerId, wallet.id, WalletStatus.ACTIVE)

        assertThat(result.status).isEqualTo(WalletStatus.ACTIVE)
    }

    @Test
    fun `should pass verification when balance is sufficient`() {
        val customerId = UUID.randomUUID()
        val wallet = walletWithOwner(customerId, WalletStatus.ACTIVE)
        every { walletRepository.lockWallet(wallet.id) } returns Unit
        every { walletBalanceService.findBalance(wallet.id) } returns WalletBalance(
            walletId = wallet.id,
            balance = BigDecimal("100.00"),
            activeHolds = BigDecimal.ZERO
        )

        service.lockAndVerifyBalance(wallet, BigDecimal("50.00"))

        verify { walletRepository.lockWallet(wallet.id) }
        verify { walletBalanceService.findBalance(wallet.id) }
    }

    @Test
    fun `should throw InsufficientFundsException when balance is insufficient`() {
        val customerId = UUID.randomUUID()
        val wallet = walletWithOwner(customerId, WalletStatus.ACTIVE)
        every { walletRepository.lockWallet(wallet.id) } returns Unit
        every { walletBalanceService.findBalance(wallet.id) } returns WalletBalance(
            walletId = wallet.id,
            balance = BigDecimal("30.00"),
            activeHolds = BigDecimal.ZERO
        )

        assertThatThrownBy {
            service.lockAndVerifyBalance(wallet, BigDecimal("50.00"))
        }.isInstanceOf(InsufficientFundsException::class.java)
    }

    private fun walletWithOwner(customerId: UUID, status: WalletStatus): Wallet {
        return Wallet(
            id = UUID.randomUUID(),
            owner = WalletOwner(
                id = customerId,
                displayName = "display",
                label = "handle",
                type = me.rcendrow.wallet.domain.wallet.WalletOwnerType.CUSTOMER,
            ),
            status = status,
            createdAt = LocalDateTime.now()
        )
    }
}
