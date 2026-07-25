package me.rcendrow.wallet.application

import com.fasterxml.uuid.Generators
import me.rcendrow.wallet.application.exception.WalletStatusException
import me.rcendrow.wallet.application.exception.HoldStatusException
import me.rcendrow.wallet.application.exception.NotFoundException
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
    fun placeHold(walletId: UUID, amount: BigDecimal, expiresAt: LocalDateTime): Hold {
        val wallet = walletService.getCustomerWallet(walletId)
        if (wallet.status != WalletStatus.ACTIVE) {
            throw WalletStatusException(walletId, wallet.status)
        }
        walletService.lockAndVerifyBalance(wallet, amount)

        return Hold(
            id = Generators.timeBasedEpochRandomGenerator().generate(),
            walletId = walletId,
            amount = amount,
            status = HoldStatus.ACTIVE,
            expiresAt = expiresAt,
            createdAt = LocalDateTime.now(),
        ).let { holdRepository.create(it) }
    }

    @Transactional
    fun captureHold(holdId: UUID, toWallet: UUID): Transfer {
        val hold = findById(holdId)

        when (hold.status) {
            HoldStatus.CAPTURED, HoldStatus.RELEASED -> throw HoldStatusException(holdId, hold.status)
            HoldStatus.ACTIVE -> if (hold.expiresAt < LocalDateTime.now()) {
                holdRepository.updateStatus(hold, HoldStatus.RELEASED)
                    .apply { throw HoldStatusException(id, status) }
            }
        }


        val transfer = transferService.createTransfer(
            fromWallet = hold.walletId,
            toWallet = toWallet,
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
            throw IllegalArgumentException("Hold $holdId is ${hold.status}, expected ACTIVE")
        }

        holdRepository.updateStatus(hold, HoldStatus.RELEASED)
    }

    @Transactional
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
