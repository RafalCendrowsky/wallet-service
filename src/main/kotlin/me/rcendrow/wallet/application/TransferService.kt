package me.rcendrow.wallet.application

import com.fasterxml.uuid.Generators
import me.rcendrow.wallet.application.exception.DuplicateIdempotencyKeyException
import me.rcendrow.wallet.domain.Transfer
import me.rcendrow.wallet.domain.wallet.ServiceRole
import me.rcendrow.wallet.domain.wallet.Wallet
import me.rcendrow.wallet.domain.wallet.WalletOwnerType
import me.rcendrow.wallet.domain.wallet.WalletStatus
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
    private val customerService: CustomerService,
) {

    @Transactional
    fun createDeposit(customerId: UUID, walletId: UUID, amount: BigDecimal, idempotencyKey: String): Transfer {
        val wallet = walletService.getCustomerWallet(customerId, walletId)
        val systemWallet = walletService.getServiceWalletByRole(ServiceRole.EXTERNAL_SETTLEMENT)
        return createTransfer(
            fromWallet = systemWallet,
            toWallet = wallet,
            amount = amount,
            idempotencyKey = idempotencyKey,
        )
    }

    @Transactional
    fun createWithdrawal(customerId: UUID, walletId: UUID, amount: BigDecimal, idempotencyKey: String): Transfer {
        val wallet = walletService.getCustomerWallet(customerId, walletId)
        val systemWallet = walletService.getServiceWalletByRole(ServiceRole.EXTERNAL_SETTLEMENT)
        return createTransfer(
            fromWallet = wallet,
            toWallet = systemWallet,
            amount = amount,
            idempotencyKey = idempotencyKey,
        )
    }

    @Transactional
    fun createTransfer(
        fromCustomerId: UUID,
        fromWallet: UUID,
        toCustomerId: UUID,
        amount: BigDecimal,
        idempotencyKey: String,
    ): Transfer {
        val toCustomer = customerService.getCustomer(toCustomerId)
        val to = walletService.getCustomerWallet(toCustomer.id)
        val from = walletService.getCustomerWallet(fromCustomerId, fromWallet)
        return createTransfer(
            fromWallet = from,
            toWallet = to,
            amount = amount,
            idempotencyKey = idempotencyKey,
        )
    }

    internal fun createTransfer(
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

        if (fromWallet.owner.type == WalletOwnerType.CUSTOMER) {
            walletService.lockAndVerifyBalance(fromWallet, amount)
        }

        val transfer = Transfer(
            id = Generators.timeBasedEpochRandomGenerator().generate(),
            fromWallet = fromWallet.id,
            fromOwner = fromWallet.owner,
            toWallet = toWallet.id,
            toOwner = toWallet.owner,
            amount = amount,
            idempotencyKey = idempotencyKey,
            createdAt = LocalDateTime.now(),
        )

        try {
            transferRepository.create(transfer)
        } catch (e: DuplicateIdempotencyKeyException) {
            return e.existing
        }

        ledgerService.createEntries(transfer)

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
    fun getWalletTransfers(customerId: UUID, walletId: UUID, pageable: Pageable): Page<Transfer> {
        walletService.getCustomerWallet(customerId, walletId)
        return transferRepository.findByWalletId(walletId, pageable)
    }
}
