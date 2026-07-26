package me.rcendrow.wallet.application

import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import me.rcendrow.wallet.application.exception.WalletStatusException
import me.rcendrow.wallet.application.exception.InsufficientFundsException
import me.rcendrow.wallet.domain.Hold
import me.rcendrow.wallet.domain.HoldStatus
import me.rcendrow.wallet.domain.Transfer
import me.rcendrow.wallet.domain.wallet.WalletStatus
import me.rcendrow.wallet.domain.wallet.CustomerWallet
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
    private val service = HoldService(holdRepository, walletService, transferService)

    @AfterEach
    fun tearDown() {
        clearMocks(holdRepository, walletService, transferService)
    }

    @Test
    fun `should place hold when sufficient available balance`() {
        val customerId = UUID.randomUUID()
        val walletId = UUID.randomUUID()
        val expiresAt = LocalDateTime.now().plusDays(1)
        val wallet = CustomerWallet(
            id = walletId,
            customerId = customerId,
            status = WalletStatus.ACTIVE,
            createdAt = LocalDateTime.now()
        )
        every { walletService.getCustomerWallet(customerId, walletId) } returns wallet
        every { walletService.lockAndVerifyBalance(wallet, BigDecimal("30.00")) } returns Unit
        every { holdRepository.create(any()) } answers { firstArg() }

        val result = service.placeHold(customerId, walletId, BigDecimal("30.00"), expiresAt)

        assertThat(result.walletId).isEqualTo(walletId)
        assertThat(result.amount).isEqualByComparingTo(BigDecimal("30.00"))
        assertThat(result.status).isEqualTo(HoldStatus.ACTIVE)
        assertThat(result.expiresAt).isEqualTo(expiresAt)
        verify { holdRepository.create(result) }
    }

    @Test
    fun `should reject hold for non-active wallet`() {
        val customerId = UUID.randomUUID()
        val walletId = UUID.randomUUID()
        val wallet = CustomerWallet(
            id = walletId,
            customerId = customerId,
            status = WalletStatus.SUSPENDED,
            createdAt = LocalDateTime.now()
        )
        every { walletService.getCustomerWallet(customerId, walletId) } returns wallet

        assertThatThrownBy {
            service.placeHold(customerId, walletId, BigDecimal("30.00"), LocalDateTime.now().plusDays(1))
        }.isInstanceOf(WalletStatusException::class.java)
    }

    @Test
    fun `should reject hold when available balance insufficient`() {
        val customerId = UUID.randomUUID()
        val walletId = UUID.randomUUID()
        val wallet = CustomerWallet(
            id = walletId,
            customerId = customerId,
            status = WalletStatus.ACTIVE,
            createdAt = LocalDateTime.now()
        )
        every { walletService.getCustomerWallet(customerId, walletId) } returns wallet
        every { walletService.lockAndVerifyBalance(wallet, BigDecimal("30.00")) } throws
                InsufficientFundsException(walletId, BigDecimal("20.00"), BigDecimal("30.00"))

        assertThatThrownBy {
            service.placeHold(customerId, walletId, BigDecimal("30.00"), LocalDateTime.now().plusDays(1))
        }.isInstanceOf(InsufficientFundsException::class.java)
    }

    @Test
    fun `should capture hold and create transfer`() {
        val customerId = UUID.randomUUID()
        val holdId = UUID.randomUUID()
        val walletId = UUID.randomUUID()
        val toHandle = "recipient"
        val hold = Hold(
            holdId,
            walletId,
            BigDecimal("50.00"),
            HoldStatus.ACTIVE,
            LocalDateTime.now().plusDays(1),
            LocalDateTime.now()
        )
        val transfer = mockk<Transfer>()
        every { holdRepository.findById(holdId) } returns hold
        every { walletService.getCustomerWallet(customerId, walletId) } returns CustomerWallet(
            id = walletId, customerId = customerId, status = WalletStatus.ACTIVE, createdAt = LocalDateTime.now()
        )
        every {
            transferService.createTransfer(
                customerId,
                walletId,
                toHandle,
                BigDecimal("50.00"),
                "hold-$holdId"
            )
        } returns transfer
        every { holdRepository.updateStatus(hold, HoldStatus.CAPTURED) } returns hold

        val result = service.captureHold(customerId, holdId, toHandle)

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
