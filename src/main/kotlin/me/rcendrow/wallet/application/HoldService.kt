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
) {

    @Transactional
    fun placeHold(customerId: UUID, walletId: UUID, amount: BigDecimal, expiresAt: LocalDateTime): Hold {
        val wallet = walletService.getCustomerWallet(customerId, walletId)
        if (wallet.status != WalletStatus.ACTIVE) {
            throw WalletStatusException(walletId, wallet.status)
        }
        walletService.lockAndVerifyBalance(wallet, amount)

        val hold = Hold(
            id = Generators.timeBasedEpochRandomGenerator().generate(),
            walletId = walletId,
            amount = amount,
            status = HoldStatus.ACTIVE,
            expiresAt = expiresAt,
            createdAt = LocalDateTime.now(),
        )
        return holdRepository.create(hold)
    }

    @Transactional
    fun captureHold(customerId: UUID, holdId: UUID, toCustomerHandle: String): Transfer {
        val hold = findById(holdId)
        when (hold.status) {
            HoldStatus.CAPTURED, HoldStatus.RELEASED -> throw HoldStatusException(hold)
            HoldStatus.ACTIVE -> if (hold.expiresAt < LocalDateTime.now()) {
                val released = holdRepository.updateStatus(hold, HoldStatus.RELEASED)
                throw HoldStatusException(released)
            }
        }

        val transfer = transferService.createTransfer(
            fromCustomerId = customerId,
            fromWallet = hold.walletId,
            toCustomerHandle = toCustomerHandle,
            amount = hold.amount,
            idempotencyKey = "hold-$holdId",
        )

        holdRepository.updateStatus(hold, HoldStatus.CAPTURED)
        return transfer
    }

    @Transactional
    fun releaseHold(holdId: UUID) {
        val hold = findById(holdId)

        if (hold.status != HoldStatus.ACTIVE) {
            throw HoldStatusException(hold)
        }

        holdRepository.updateStatus(hold, HoldStatus.RELEASED)
    }

    @Transactional(readOnly = true)
    fun getHold(holdId: UUID): Hold = findById(holdId)

    @Transactional
    @Scheduled(fixedRate = 60_000)
    fun releaseExpiredHolds() {
        val expired = holdRepository.findExpiredActiveHolds()
        expired.forEach { holdRepository.updateStatus(it, HoldStatus.RELEASED) }
    }

    private fun findById(id: UUID): Hold {
        return holdRepository.findById(id) ?: throw NotFoundException("Hold", id)
    }
}
