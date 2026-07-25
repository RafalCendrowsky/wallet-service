package me.rcendrow.wallet.application

import com.fasterxml.uuid.Generators
import me.rcendrow.wallet.application.exception.DuplicateIdempotencyKeyException
import me.rcendrow.wallet.domain.Transfer
import me.rcendrow.wallet.domain.wallet.Wallet
import me.rcendrow.wallet.domain.wallet.WalletStatus
import me.rcendrow.wallet.domain.wallet.CustomerWallet
import me.rcendrow.wallet.domain.wallet.ServiceWalletRole
import me.rcendrow.wallet.persistence.TransferRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

@Service
class TransferService(
    private val transferRepository: TransferRepository,
    private val walletService: WalletService,
    private val ledgerService: LedgerService,
    private val walletBalanceService: WalletBalanceService,
) {

    @Transactional
    fun createDeposit(walletId: UUID, amount: BigDecimal, idempotencyKey: String): Transfer {
        val wallet = walletService.getCustomerWallet(walletId)
        val systemWallet = walletService.getServiceWalletByRole(ServiceWalletRole.EXTERNAL_SETTLEMENT)
        return createTransfer(
            fromWallet = systemWallet,
            toWallet = wallet,
            amount = amount,
            idempotencyKey = idempotencyKey,
        )
    }

    @Transactional
    fun createWithdrawal(walletId: UUID, amount: BigDecimal, idempotencyKey: String): Transfer {
        val wallet = walletService.getCustomerWallet(walletId)
        val systemWallet = walletService.getServiceWalletByRole(ServiceWalletRole.EXTERNAL_SETTLEMENT)
        return createTransfer(
            fromWallet = wallet,
            toWallet = systemWallet,
            amount = amount,
            idempotencyKey = idempotencyKey,
        )
    }

    @Transactional
    fun createTransfer(
        fromWallet: UUID,
        toWallet: UUID,
        amount: BigDecimal,
        idempotencyKey: String,
    ): Transfer {
        val from = walletService.getCustomerWallet(fromWallet)
        val to = walletService.getCustomerWallet(toWallet)
        return createTransfer(
            fromWallet = from,
            toWallet = to,
            amount = amount,
            idempotencyKey = idempotencyKey,
        )
    }

    private fun createTransfer(
        fromWallet: Wallet,
        toWallet: Wallet,
        amount: BigDecimal,
        idempotencyKey: String,
    ): Transfer {
        fromWallet.verifyStatus(WalletStatus.ACTIVE)
        toWallet.verifyStatusNot(WalletStatus.CLOSED)

        if (fromWallet.id == toWallet.id) {
            throw IllegalArgumentException("Self-transfer not allowed")
        }

        if (amount <= BigDecimal.ZERO) {
            throw IllegalArgumentException("Amount must be positive")
        }

        transferRepository.findByIdempotencyKey(idempotencyKey)?.let { return it }

        if (fromWallet is CustomerWallet) {
            walletService.lockAndVerifyBalance(fromWallet, amount)
        }

        val transfer = Transfer(
            id = Generators.timeBasedEpochRandomGenerator().generate(),
            fromWallet = fromWallet.id,
            toWallet = toWallet.id,
            amount = amount,
            idempotencyKey = idempotencyKey,
            createdAt = LocalDateTime.now(),
        )

        try {
            transferRepository.create(transfer)
        } catch (e: DuplicateIdempotencyKeyException) {
            return e.existing
        }

        ledgerService.createCreditEntry(transfer)
        ledgerService.createDebitEntry(transfer)

        walletBalanceService.markWalletForRefresh(fromWallet.id)
        walletBalanceService.markWalletForRefresh(toWallet.id)
        return transfer
    }

    @Transactional(readOnly = true)
    fun getTransfer(id: UUID): Transfer {
        return transferRepository.findById(id)
            ?: throw IllegalArgumentException("Transfer not found: $id")
    }

    @Transactional(readOnly = true)
    fun getTransferByIdempotencyKey(key: String): Transfer? {
        return transferRepository.findByIdempotencyKey(key)
    }

    @Transactional(readOnly = true)
    fun getWalletTransfers(walletId: UUID, pageable: Pageable): Page<Transfer> {
        walletService.getCustomerWallet(walletId)
        return transferRepository.findByWalletId(walletId, pageable)
    }
}
