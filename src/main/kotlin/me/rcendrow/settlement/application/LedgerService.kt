package me.rcendrow.settlement.application

import com.fasterxml.uuid.Generators
import me.rcendrow.settlement.domain.EntryType
import me.rcendrow.settlement.domain.LedgerEntry
import me.rcendrow.settlement.domain.Transfer
import me.rcendrow.settlement.persistence.LedgerRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class LedgerService(private val ledgerRepository: LedgerRepository) {

    @Transactional(readOnly = true)
    fun findBalance(accountId: UUID) = ledgerRepository.findBalance(accountId)

    @Transactional
    fun createEntry(transfer: Transfer, type: EntryType): LedgerEntry {
        return LedgerEntry(
            id = Generators.timeBasedEpochRandomGenerator().generate(),
            transferId = transfer.id,
            accountId = if (type == EntryType.DEBIT) transfer.fromAccount else transfer.toAccount,
            type = type,
            amount = transfer.amount,
            createdAt = transfer.createdAt
        ).let { ledgerRepository.create(it) }
    }
}
