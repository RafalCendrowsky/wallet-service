package me.rcendrow.settlement.application

import com.fasterxml.uuid.Generators
import me.rcendrow.settlement.application.exception.DuplicateIdempotencyKeyException
import me.rcendrow.settlement.application.exception.InsufficientFundsException
import me.rcendrow.settlement.domain.EntryType
import me.rcendrow.settlement.domain.Transfer
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
) {

    @Transactional
    fun createTransfer(
        fromAccount: UUID,
        toAccount: UUID,
        amount: BigDecimal,
        idempotencyKey: String,
    ): Transfer {
        accountService.lockAccount(fromAccount)
        accountService.getAccount(toAccount)

        if (amount <= BigDecimal.ZERO) {
            throw IllegalArgumentException("Amount must be positive")
        }

        transferRepository.findByIdempotencyKey(idempotencyKey)?.let { return it }

        ledgerService.findBalance(fromAccount).takeIf { it < amount }
            ?.let { throw InsufficientFundsException(fromAccount, it, amount) }

        val transfer = Transfer(
            id = Generators.timeBasedEpochRandomGenerator().generate(),
            fromAccount = fromAccount,
            toAccount = toAccount,
            amount = amount,
            idempotencyKey = idempotencyKey,
            createdAt = LocalDateTime.now(),
        )

        try {
            transferRepository.create(transfer)
        } catch (e: DuplicateIdempotencyKeyException) {
            return e.existing
        }

        ledgerService.createEntry(transfer, EntryType.CREDIT)
        ledgerService.createEntry(transfer, EntryType.DEBIT)

        return transfer
    }

    @Transactional(readOnly = true)
    fun getTransfer(id: UUID): Transfer {
        return transferRepository.findById(id)
            ?: throw IllegalArgumentException("Transfer not found: $id")
    }

    @Transactional(readOnly = true)
    fun getAccountTransfers(accountId: UUID, pageable: Pageable): Page<Transfer> {
        accountService.getAccount(accountId)
        return transferRepository.findByAccountId(accountId, pageable)
    }
}
