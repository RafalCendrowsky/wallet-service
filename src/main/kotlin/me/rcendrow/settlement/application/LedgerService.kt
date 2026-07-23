package me.rcendrow.settlement.application

import com.fasterxml.uuid.Generators
import me.rcendrow.settlement.domain.LedgerEntry
import me.rcendrow.settlement.domain.Transfer
import me.rcendrow.settlement.persistence.LedgerEntryRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class LedgerService(private val ledgerEntryRepository: LedgerEntryRepository) {

    @Transactional
    fun createDebitEntry(transfer: Transfer): LedgerEntry = createEntry(transfer, true)

    @Transactional
    fun createCreditEntry(transfer: Transfer): LedgerEntry = createEntry(transfer, false)

    private fun createEntry(transfer: Transfer, debit: Boolean): LedgerEntry {
        return LedgerEntry(
            id = Generators.timeBasedEpochRandomGenerator().generate(),
            transferId = transfer.id,
            accountId = if (debit) transfer.fromAccount else transfer.toAccount,
            amount = if (debit) -transfer.amount else transfer.amount,
            createdAt = transfer.createdAt
        ).let { ledgerEntryRepository.create(it) }
    }
}
