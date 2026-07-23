package me.rcendrow.settlement.application

import com.fasterxml.uuid.Generators
import me.rcendrow.settlement.application.exception.DuplicateIdempotencyKeyException
import me.rcendrow.settlement.domain.Transfer
import me.rcendrow.settlement.domain.account.Account
import me.rcendrow.settlement.domain.account.AccountStatus
import me.rcendrow.settlement.domain.account.CustomerAccount
import me.rcendrow.settlement.domain.account.ServiceAccountRole
import me.rcendrow.settlement.persistence.TransferRepository
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
    private val accountService: AccountService,
    private val ledgerService: LedgerService,
    private val accountBalanceService: AccountBalanceService,
) {

    @Transactional
    fun createDeposit(accountId: UUID, amount: BigDecimal, idempotencyKey: String): Transfer {
        val account = accountService.getCustomerAccount(accountId)
        val systemAccount = accountService.getServiceAccountByRole(ServiceAccountRole.EXTERNAL_SETTLEMENT)
        return createTransfer(
            fromAccount = systemAccount,
            toAccount = account,
            amount = amount,
            idempotencyKey = idempotencyKey,
        )
    }

    @Transactional
    fun createWithdrawal(accountId: UUID, amount: BigDecimal, idempotencyKey: String): Transfer {
        val account = accountService.getCustomerAccount(accountId)
        val systemAccount = accountService.getServiceAccountByRole(ServiceAccountRole.EXTERNAL_SETTLEMENT)
        return createTransfer(
            fromAccount = account,
            toAccount = systemAccount,
            amount = amount,
            idempotencyKey = idempotencyKey,
        )
    }

    @Transactional
    fun createTransfer(
        fromAccount: UUID,
        toAccount: UUID,
        amount: BigDecimal,
        idempotencyKey: String,
    ): Transfer {
        val from = accountService.getCustomerAccount(fromAccount)
        val to = accountService.getCustomerAccount(toAccount)
        return createTransfer(
            fromAccount = from,
            toAccount = to,
            amount = amount,
            idempotencyKey = idempotencyKey,
        )
    }

    private fun createTransfer(
        fromAccount: Account,
        toAccount: Account,
        amount: BigDecimal,
        idempotencyKey: String,
    ): Transfer {
        fromAccount.verifyStatus(AccountStatus.ACTIVE)
        toAccount.verifyStatusNot(AccountStatus.CLOSED)

        if (amount <= BigDecimal.ZERO) {
            throw IllegalArgumentException("Amount must be positive")
        }

        transferRepository.findByIdempotencyKey(idempotencyKey)?.let { return it }

        if (fromAccount is CustomerAccount) {
            accountService.lockAndVerifyBalance(fromAccount, amount)
        }

        val transfer = Transfer(
            id = Generators.timeBasedEpochRandomGenerator().generate(),
            fromAccount = fromAccount.id,
            toAccount = toAccount.id,
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

        accountBalanceService.markAccountForRefresh(fromAccount.id)
        accountBalanceService.markAccountForRefresh(toAccount.id)
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
    fun getAccountTransfers(accountId: UUID, pageable: Pageable): Page<Transfer> {
        accountService.getCustomerAccount(accountId)
        return transferRepository.findByAccountId(accountId, pageable)
    }
}
