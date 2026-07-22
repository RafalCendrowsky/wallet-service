package me.rcendrow.settlement.persistence

import me.rcendrow.jooq.generated.tables.Ledger.Companion.LEDGER
import me.rcendrow.settlement.domain.EntryType
import me.rcendrow.settlement.domain.LedgerEntry
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.util.*

@Repository
class LedgerRepository(private val db: DSLContext) {

    fun findById(id: UUID): LedgerEntry? {
        return db.selectFrom(LEDGER)
            .where(LEDGER.ID.eq(id))
            .fetchOneInto(LedgerEntry::class.java)
    }

    fun findBalance(accountId: UUID): BigDecimal {
        return db.select(
            DSL.coalesce(
                DSL.sum(
                    DSL.`when`(LEDGER.TYPE.eq(EntryType.CREDIT.name), LEDGER.AMOUNT)
                        .otherwise(LEDGER.AMOUNT.neg())
                ),
                BigDecimal.ZERO
            )
        )
            .from(LEDGER)
            .where(LEDGER.ACCOUNT_ID.eq(accountId))
            .fetchOneInto(BigDecimal::class.java)!!
    }

    fun findAllBalances(): Map<UUID, BigDecimal> {
        return db.select(
            LEDGER.ACCOUNT_ID,
            DSL.coalesce(
                DSL.sum(
                    DSL.`when`(LEDGER.TYPE.eq(EntryType.CREDIT.name), LEDGER.AMOUNT)
                        .otherwise(LEDGER.AMOUNT.neg())
                ),
                BigDecimal.ZERO
            ).`as`("balance")
        )
            .from(LEDGER)
            .groupBy(LEDGER.ACCOUNT_ID)
            .fetch()
            .associate { record ->
                record[LEDGER.ACCOUNT_ID]!! to record["balance", BigDecimal::class.java]!!
            }
    }

    fun create(entry: LedgerEntry): LedgerEntry {
        return db.insertInto(LEDGER)
            .set(LEDGER.ID, entry.id)
            .set(LEDGER.ACCOUNT_ID, entry.accountId)
            .set(LEDGER.TRANSFER_ID, entry.transferId)
            .set(LEDGER.TYPE, entry.type.name)
            .set(LEDGER.AMOUNT, entry.amount)
            .set(LEDGER.CREATED_AT, entry.createdAt)
            .returning()
            .fetchSingleInto(LedgerEntry::class.java)
    }
}
