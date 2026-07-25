package me.rcendrow.wallet.application

import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import me.rcendrow.wallet.application.exception.WalletStatusException
import me.rcendrow.wallet.application.exception.InsufficientFundsException
import me.rcendrow.wallet.application.exception.NotFoundException
import me.rcendrow.wallet.domain.wallet.WalletBalance
import me.rcendrow.wallet.domain.wallet.WalletStatus
import me.rcendrow.wallet.domain.wallet.CustomerWallet
import me.rcendrow.wallet.persistence.wallet.WalletBalanceRepository
import me.rcendrow.wallet.persistence.wallet.CustomerWalletRepository
import me.rcendrow.wallet.persistence.wallet.ServiceWalletRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

class WalletServiceTest {

    private val customerWalletRepository: CustomerWalletRepository = mockk()
    private val serviceWalletRepository: ServiceWalletRepository = mockk()
    private val customerService: CustomerService = mockk()
    private val walletBalanceService: WalletBalanceService = mockk()
    private val walletBalanceRepository: WalletBalanceRepository = mockk()
    private val service =
        WalletService(
            customerService,
            walletBalanceService,
            customerWalletRepository,
            serviceWalletRepository,
            walletBalanceRepository
        )

    @AfterEach
    fun tearDown() {
        clearMocks(
            customerWalletRepository,
            serviceWalletRepository,
            customerService,
            walletBalanceService,
            walletBalanceRepository
        )
    }

    @Test
    fun `should create wallet`() {
        val customerId = UUID.randomUUID()
        every { customerService.getCustomer(customerId) } returns mockk()
        every { customerWalletRepository.create(any()) } answers { firstArg() }
        every { walletBalanceRepository.create(any()) } returns Unit

        val result = service.createCustomerWallet(customerId)

        assertThat(result.customerId).isEqualTo(customerId)
        assertThat(result.status).isEqualTo(WalletStatus.ACTIVE)
        assertThat(result.id).isNotNull
        assertThat(result.createdAt).isNotNull
        verify { customerWalletRepository.create(result) }
        verify { walletBalanceRepository.create(result.id) }
    }

    @Test
    fun `should return wallet by id`() {
        val id = UUID.randomUUID()
        val wallet = walletWithStatus(WalletStatus.ACTIVE)
        every { customerWalletRepository.findById(id) } returns wallet

        val result = service.getCustomerWallet(id)

        assertThat(result).isEqualTo(wallet)
    }

    @Test
    fun `should throw NotFoundException for unknown wallet`() {
        val id = UUID.randomUUID()
        every { customerWalletRepository.findById(id) } returns null

        assertThatThrownBy { service.getCustomerWallet(id) }
            .isInstanceOf(NotFoundException::class.java)
    }

    @Test
    fun `should return balance with available balance`() {
        val wallet = walletWithStatus(WalletStatus.ACTIVE)
        every { customerWalletRepository.findById(wallet.id) } returns wallet
        every { walletBalanceService.findBalance(wallet.id) } returns WalletBalance(
            walletId = wallet.id,
            balance = BigDecimal("100.00"),
            activeHolds = BigDecimal("30.00")
        )

        val result = service.getBalance(wallet.id)

        assertThat(result.balance).isEqualByComparingTo(BigDecimal("100.00"))
        assertThat(result.availableBalance).isEqualByComparingTo(BigDecimal("70.00"))
    }

    @Test
    fun `should suspend active wallet`() {
        val wallet = walletWithStatus(WalletStatus.ACTIVE)
        every { customerWalletRepository.findById(wallet.id) } returns wallet
        every {
            customerWalletRepository.updateStatus(
                wallet,
                WalletStatus.SUSPENDED
            )
        } returns wallet.copy(status = WalletStatus.SUSPENDED)

        val result = service.updateWalletStatus(wallet.id, WalletStatus.SUSPENDED)

        assertThat(result.status).isEqualTo(WalletStatus.SUSPENDED)
    }

    @Test
    fun `should allow updating status of non-closed wallet`() {
        val wallet = walletWithStatus(WalletStatus.SUSPENDED)
        every { customerWalletRepository.findById(wallet.id) } returns wallet
        every {
            customerWalletRepository.updateStatus(
                wallet,
                WalletStatus.SUSPENDED
            )
        } returns wallet.copy(status = WalletStatus.SUSPENDED)

        val result = service.updateWalletStatus(wallet.id, WalletStatus.SUSPENDED)

        assertThat(result.status).isEqualTo(WalletStatus.SUSPENDED)
    }

    @Test
    fun `should close active wallet`() {
        val wallet = walletWithStatus(WalletStatus.ACTIVE)
        every { customerWalletRepository.findById(wallet.id) } returns wallet
        every {
            customerWalletRepository.updateStatus(
                wallet,
                WalletStatus.CLOSED
            )
        } returns wallet.copy(status = WalletStatus.CLOSED)

        val result = service.updateWalletStatus(wallet.id, WalletStatus.CLOSED)

        assertThat(result.status).isEqualTo(WalletStatus.CLOSED)
    }

    @Test
    fun `should close suspended wallet`() {
        val wallet = walletWithStatus(WalletStatus.SUSPENDED)
        every { customerWalletRepository.findById(wallet.id) } returns wallet
        every {
            customerWalletRepository.updateStatus(
                wallet,
                WalletStatus.CLOSED
            )
        } returns wallet.copy(status = WalletStatus.CLOSED)

        val result = service.updateWalletStatus(wallet.id, WalletStatus.CLOSED)

        assertThat(result.status).isEqualTo(WalletStatus.CLOSED)
    }

    @Test
    fun `should not close already closed wallet`() {
        val wallet = walletWithStatus(WalletStatus.CLOSED)
        every { customerWalletRepository.findById(wallet.id) } returns wallet

        assertThatThrownBy { service.updateWalletStatus(wallet.id, WalletStatus.CLOSED) }
            .isInstanceOf(WalletStatusException::class.java)
    }

    @Test
    fun `should activate suspended wallet`() {
        val wallet = walletWithStatus(WalletStatus.SUSPENDED)
        every { customerWalletRepository.findById(wallet.id) } returns wallet
        every {
            customerWalletRepository.updateStatus(
                wallet,
                WalletStatus.ACTIVE
            )
        } returns wallet.copy(status = WalletStatus.ACTIVE)

        val result = service.updateWalletStatus(wallet.id, WalletStatus.ACTIVE)

        assertThat(result.status).isEqualTo(WalletStatus.ACTIVE)
    }

    @Test
    fun `should allow reactivating active wallet`() {
        val wallet = walletWithStatus(WalletStatus.ACTIVE)
        every { customerWalletRepository.findById(wallet.id) } returns wallet
        every {
            customerWalletRepository.updateStatus(
                wallet,
                WalletStatus.ACTIVE
            )
        } returns wallet.copy(status = WalletStatus.ACTIVE)

        val result = service.updateWalletStatus(wallet.id, WalletStatus.ACTIVE)

        assertThat(result.status).isEqualTo(WalletStatus.ACTIVE)
    }

    @Test
    fun `should pass verification when balance is sufficient`() {
        val wallet = walletWithStatus(WalletStatus.ACTIVE)
        every { customerWalletRepository.findById(wallet.id) } returns wallet
        every { customerWalletRepository.lockWallet(wallet.id) } returns Unit
        every { walletBalanceService.findBalance(wallet.id) } returns WalletBalance(
            walletId = wallet.id,
            balance = BigDecimal("100.00"),
            activeHolds = BigDecimal.ZERO
        )

        service.lockAndVerifyBalance(wallet, BigDecimal("50.00"))

        verify { customerWalletRepository.lockWallet(wallet.id) }
        verify { walletBalanceService.findBalance(wallet.id) }
    }

    @Test
    fun `should throw InsufficientFundsException when balance is insufficient`() {
        val wallet = walletWithStatus(WalletStatus.ACTIVE)
        every { customerWalletRepository.findById(wallet.id) } returns wallet
        every { customerWalletRepository.lockWallet(wallet.id) } returns Unit
        every { walletBalanceService.findBalance(wallet.id) } returns WalletBalance(
            walletId = wallet.id,
            balance = BigDecimal("30.00"),
            activeHolds = BigDecimal.ZERO
        )

        assertThatThrownBy {
            service.lockAndVerifyBalance(wallet, BigDecimal("50.00"))
        }.isInstanceOf(InsufficientFundsException::class.java)
    }

    fun walletWithStatus(status: WalletStatus): CustomerWallet {
        return CustomerWallet(
            id = UUID.randomUUID(),
            customerId = UUID.randomUUID(),
            status = status,
            createdAt = LocalDateTime.now()
        )
    }
}
