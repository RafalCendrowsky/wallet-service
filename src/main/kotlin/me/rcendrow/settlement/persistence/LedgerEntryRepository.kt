package me.rcendrow.settlement.persistence

import me.rcendrow.jooq.generated.tables.LedgerEntry.Companion.LEDGER_ENTRY
import me.rcendrow.settlement.domain.LedgerEntry
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.util.*

@Repository
class LedgerEntryRepository(private val db: DSLContext) {

    fun findById(id: UUID): LedgerEntry? {
        return db.selectFrom(LEDGER_ENTRY)
            .where(LEDGER_ENTRY.ID.eq(id))
            .fetchOneInto(LedgerEntry::class.java)
    }

    fun findBalance(accountId: UUID): BigDecimal {
        return db.select(DSL.coalesce(DSL.sum(LEDGER_ENTRY.AMOUNT), BigDecimal.ZERO))
            .from(LEDGER_ENTRY)
            .where(LEDGER_ENTRY.ACCOUNT_ID.eq(accountId))
            .fetchOneInto(BigDecimal::class.java)!!
    }

    fun findAllBalances(): Map<UUID, BigDecimal> {
        return db.select(
            LEDGER_ENTRY.ACCOUNT_ID,
            DSL.coalesce(DSL.sum(LEDGER_ENTRY.AMOUNT), BigDecimal.ZERO).`as`("balance")
        )
            .from(LEDGER_ENTRY)
            .groupBy(LEDGER_ENTRY.ACCOUNT_ID)
            .fetch()
            .associate { record ->
                record[LEDGER_ENTRY.ACCOUNT_ID]!! to record["balance", BigDecimal::class.java]!!
            }
    }

    fun create(entry: LedgerEntry): LedgerEntry {
        return db.insertInto(LEDGER_ENTRY)
            .set(LEDGER_ENTRY.ID, entry.id)
            .set(LEDGER_ENTRY.ACCOUNT_ID, entry.accountId)
            .set(LEDGER_ENTRY.TRANSFER_ID, entry.transferId)
            .set(LEDGER_ENTRY.AMOUNT, entry.amount)
            .set(LEDGER_ENTRY.CREATED_AT, entry.createdAt)
            .returning()
            .fetchSingleInto(LedgerEntry::class.java)
    }
}
