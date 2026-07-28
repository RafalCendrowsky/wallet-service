package me.rcendrow.wallet.application

import com.fasterxml.uuid.Generators
import me.rcendrow.wallet.application.exception.HoldStatusException
import me.rcendrow.wallet.application.exception.NotFoundException
import me.rcendrow.wallet.application.exception.WalletStatusException
import me.rcendrow.wallet.domain.Hold
import me.rcendrow.wallet.domain.HoldStatus
import me.rcendrow.wallet.domain.Transfer
import me.rcendrow.wallet.domain.wallet.WalletStatus
import me.rcendrow.wallet.persistence.HoldRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

@Service
class HoldService(
    private val holdRepository: HoldRepository,
    private val walletService: WalletService,
    private val transferService: TransferService,
    private val customerService: CustomerService,
) {

    @Transactional
    fun placeHold(
        customerId: UUID,
        fromWallet: UUID,
        toCustomerHandle: String,
        amount: BigDecimal,
        expiresAt: LocalDateTime,
    ): Hold {
        val wallet = walletService.getCustomerWallet(customerId, fromWallet)
        if (wallet.status != WalletStatus.ACTIVE) {
            throw WalletStatusException(wallet.id, wallet.status)
        }
        walletService.lockAndVerifyBalance(wallet, amount)

        val toCustomer = customerService.getCustomerByHandle(toCustomerHandle)
        val toWallet = walletService.getCustomerWallet(toCustomer.id)

        val hold = Hold(
            id = Generators.timeBasedEpochRandomGenerator().generate(),
            fromWallet = fromWallet,
            toWallet = toWallet.id,
            customerId = customerId,
            amount = amount,
            status = HoldStatus.ACTIVE,
            expiresAt = expiresAt,
            createdAt = LocalDateTime.now(),
        )
        return holdRepository.create(hold)
    }

    @Transactional
    fun captureHold(customerId: UUID, holdId: UUID): Transfer {
        val hold = findByCustomerIdAndId(customerId, holdId)
        when (hold.status) {
            HoldStatus.CAPTURED, HoldStatus.RELEASED -> throw HoldStatusException(hold)
            HoldStatus.ACTIVE -> if (hold.expiresAt < LocalDateTime.now()) {
                val released = holdRepository.updateStatus(hold, HoldStatus.RELEASED)
                throw HoldStatusException(released)
            }
        }

        val from = walletService.getCustomerWallet(customerId, hold.fromWallet)
        val to = walletService.getCustomerWalletById(hold.toWallet)
        val transfer = transferService.createTransfer(
            fromWallet = from,
            toWallet = to,
            amount = hold.amount,
            idempotencyKey = "hold-$holdId",
        )

        holdRepository.updateStatus(hold, HoldStatus.CAPTURED)
        return transfer
    }

    @Transactional
    fun releaseHold(customerId: UUID, holdId: UUID) {
        val hold = findByCustomerIdAndId(customerId, holdId)

        if (hold.status != HoldStatus.ACTIVE) {
            throw HoldStatusException(hold)
        }

        holdRepository.updateStatus(hold, HoldStatus.RELEASED)
    }

    @Transactional(readOnly = true)
    fun getHold(customerId: UUID, holdId: UUID): Hold = findByCustomerIdAndId(customerId, holdId)

    @Transactional
    @Scheduled(fixedRate = 60_000)
    fun releaseExpiredHolds() {
        holdRepository.releaseExpiredActiveHolds()
    }

    private fun findByCustomerIdAndId(customerId: UUID, id: UUID): Hold {
        return holdRepository.findByCustomerIdAndId(customerId, id)
            ?: throw NotFoundException("Hold", id)
    }
}
