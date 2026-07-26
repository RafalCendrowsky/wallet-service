package me.rcendrow.wallet.application

import com.fasterxml.uuid.Generators
import me.rcendrow.wallet.domain.LedgerEntry
import me.rcendrow.wallet.domain.Transfer
import me.rcendrow.wallet.persistence.LedgerEntryRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class LedgerService(private val ledgerEntryRepository: LedgerEntryRepository) {

    @Transactional
    fun createEntries(transfer: Transfer): List<LedgerEntry> {
        val debitEntry = createEntry(transfer, true)
        val creditEntry = createEntry(transfer, false)
        return listOf(debitEntry, creditEntry)
    }

    private fun createEntry(transfer: Transfer, debit: Boolean): LedgerEntry {
        val entry = LedgerEntry(
            id = Generators.timeBasedEpochRandomGenerator().generate(),
            transferId = transfer.id,
            walletId = if (debit) transfer.fromWallet else transfer.toWallet,
            amount = if (debit) -transfer.amount else transfer.amount,
            createdAt = transfer.createdAt
        )
        return ledgerEntryRepository.create(entry)
    }
}
