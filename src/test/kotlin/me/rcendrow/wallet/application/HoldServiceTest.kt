package me.rcendrow.wallet.application

import io.mockk.*
import me.rcendrow.wallet.application.exception.InsufficientFundsException
import me.rcendrow.wallet.application.exception.WalletStatusException
import me.rcendrow.wallet.domain.Customer
import me.rcendrow.wallet.domain.Hold
import me.rcendrow.wallet.domain.HoldStatus
import me.rcendrow.wallet.domain.Transfer
import me.rcendrow.wallet.domain.wallet.CustomerWallet
import me.rcendrow.wallet.domain.wallet.WalletStatus
import me.rcendrow.wallet.persistence.HoldRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

class HoldServiceTest {

    private val holdRepository: HoldRepository = mockk()
    private val walletService: WalletService = mockk()
    private val transferService: TransferService = mockk()
    private val customerService: CustomerService = mockk()
    private val service = HoldService(holdRepository, walletService, transferService, customerService)

    @AfterEach
    fun tearDown() {
        clearMocks(holdRepository, walletService, transferService)
    }

    @Test
    fun `should place hold when sufficient available balance`() {
        val customerId = UUID.randomUUID()
        val fromWallet = UUID.randomUUID()
        val toCustomerHandle = "test-handle"
        val expiresAt = LocalDateTime.now().plusDays(1)
        val sender = CustomerWallet(
            id = fromWallet,
            customerId = customerId,
            status = WalletStatus.ACTIVE,
            createdAt = LocalDateTime.now()
        )
        val receiverCustomer = mockkClass(Customer::class)
        val receiver = CustomerWallet(
            id = UUID.randomUUID(),
            customerId = UUID.randomUUID(),
            status = WalletStatus.ACTIVE,
            createdAt = LocalDateTime.now()
        )
        every { receiverCustomer.id } returns receiver.customerId
        every { customerService.getCustomerByHandle(toCustomerHandle) } returns receiverCustomer
        every { walletService.getCustomerWallet(any()) } returns receiver
        every { walletService.getCustomerWallet(customerId, fromWallet) } returns sender
        every { walletService.lockAndVerifyBalance(sender, BigDecimal("30.00")) } returns Unit
        every { holdRepository.create(any()) } answers { firstArg() }

        val result = service.placeHold(customerId, fromWallet, toCustomerHandle, BigDecimal("30.00"), expiresAt)

        assertThat(result.fromWallet).isEqualTo(fromWallet)
        assertThat(result.toWallet).isEqualTo(receiver.id)
        assertThat(result.customerId).isEqualTo(customerId)
        assertThat(result.amount).isEqualByComparingTo(BigDecimal("30.00"))
        assertThat(result.status).isEqualTo(HoldStatus.ACTIVE)
        assertThat(result.expiresAt).isEqualTo(expiresAt)
        verify { holdRepository.create(result) }
    }

    @Test
    fun `should reject hold for non-active wallet`() {
        val customerId = UUID.randomUUID()
        val fromWallet = UUID.randomUUID()
        val wallet = CustomerWallet(
            id = fromWallet,
            customerId = customerId,
            status = WalletStatus.SUSPENDED,
            createdAt = LocalDateTime.now()
        )
        every { walletService.getCustomerWallet(customerId, fromWallet) } returns wallet

        assertThatThrownBy {
            service.placeHold(customerId, fromWallet, "handle", BigDecimal("30.00"), LocalDateTime.now().plusDays(1))
        }.isInstanceOf(WalletStatusException::class.java)
    }

    @Test
    fun `should reject hold when available balance insufficient`() {
        val customerId = UUID.randomUUID()
        val fromWallet = UUID.randomUUID()
        val toWallet = UUID.randomUUID()
        val wallet = CustomerWallet(
            id = fromWallet,
            customerId = customerId,
            status = WalletStatus.ACTIVE,
            createdAt = LocalDateTime.now()
        )
        every { walletService.getCustomerWallet(customerId, fromWallet) } returns wallet
        every { walletService.lockAndVerifyBalance(wallet, BigDecimal("30.00")) } throws
                InsufficientFundsException(fromWallet, BigDecimal("20.00"), BigDecimal("30.00"))

        assertThatThrownBy {
            service.placeHold(customerId, fromWallet, "handle", BigDecimal("30.00"), LocalDateTime.now().plusDays(1))
        }.isInstanceOf(InsufficientFundsException::class.java)
    }

    @Test
    fun `should capture hold and create transfer`() {
        val customerId = UUID.randomUUID()
        val holdId = UUID.randomUUID()
        val fromWallet = UUID.randomUUID()
        val toWallet = UUID.randomUUID()
        val hold = Hold(
            id = holdId,
            fromWallet = fromWallet,
            toWallet = toWallet,
            customerId = customerId,
            amount = BigDecimal("50.00"),
            status = HoldStatus.ACTIVE,
            expiresAt = LocalDateTime.now().plusDays(1),
            createdAt = LocalDateTime.now(),
        )
        val transfer = mockk<Transfer>()
        val from = CustomerWallet(
            id = fromWallet, customerId = customerId, status = WalletStatus.ACTIVE, createdAt = LocalDateTime.now()
        )
        val to = CustomerWallet(
            id = toWallet, customerId = UUID.randomUUID(), status = WalletStatus.ACTIVE, createdAt = LocalDateTime.now()
        )
        every { holdRepository.findByCustomerIdAndId(customerId, holdId) } returns hold
        every { walletService.getCustomerWallet(customerId, fromWallet) } returns from
        every { walletService.getCustomerWalletById(toWallet) } returns to
        every { transferService.createTransfer(from, to, BigDecimal("50.00"), "hold-$holdId") } returns transfer
        every { holdRepository.updateStatus(hold, HoldStatus.CAPTURED) } returns hold

        val result = service.captureHold(customerId, holdId)

        assertThat(result).isSameAs(transfer)
        verify { holdRepository.updateStatus(hold, HoldStatus.CAPTURED) }
    }

    @Test
    fun `should release hold`() {
        val customerId = UUID.randomUUID()
        val holdId = UUID.randomUUID()
        val hold = Hold(
            id = holdId,
            fromWallet = UUID.randomUUID(),
            toWallet = UUID.randomUUID(),
            customerId = customerId,
            amount = BigDecimal("50.00"),
            status = HoldStatus.ACTIVE,
            expiresAt = LocalDateTime.now().plusDays(1),
            createdAt = LocalDateTime.now(),
        )
        every { holdRepository.findByCustomerIdAndId(customerId, holdId) } returns hold
        every { holdRepository.updateStatus(hold, HoldStatus.RELEASED) } returns hold

        service.releaseHold(customerId, holdId)

        verify { holdRepository.updateStatus(hold, HoldStatus.RELEASED) }
    }

    @Test
    fun `should release expired holds`() {
        every { holdRepository.releaseExpiredActiveHolds() } returns Unit
        service.releaseExpiredHolds()

        verify(exactly = 1) { holdRepository.releaseExpiredActiveHolds() }
    }
}
