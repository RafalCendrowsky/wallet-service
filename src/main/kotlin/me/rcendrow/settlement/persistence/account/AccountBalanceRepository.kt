package me.rcendrow.settlement.persistence.account

import me.rcendrow.jooq.generated.tables.AccountBalance.Companion.ACCOUNT_BALANCE
import me.rcendrow.jooq.generated.tables.LedgerEntry.Companion.LEDGER_ENTRY
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.util.*

@Repository
class AccountBalanceRepository(private val db: DSLContext) {
    fun findCurrentBalance(accountId: UUID): BigDecimal {
        val pendingAmount = db.select(DSL.coalesce(DSL.sum(LEDGER_ENTRY.AMOUNT), BigDecimal.ZERO))
            .from(LEDGER_ENTRY)
            .where(LEDGER_ENTRY.ACCOUNT_ID.eq(ACCOUNT_BALANCE.ACCOUNT_ID))
            .and(
                DSL.or(
                    ACCOUNT_BALANCE.LAST_ENTRY_ID.isNull,
                    LEDGER_ENTRY.ID.gt(ACCOUNT_BALANCE.LAST_ENTRY_ID)
                )
            )
            .asField<BigDecimal>()

        return db.select(ACCOUNT_BALANCE.BALANCE.plus(pendingAmount))
            .from(ACCOUNT_BALANCE)
            .where(ACCOUNT_BALANCE.ACCOUNT_ID.eq(accountId))
            .fetchSingleInto(BigDecimal::class.java)
    }

    fun create(accountId: UUID) {
        db.insertInto(ACCOUNT_BALANCE)
            .set(ACCOUNT_BALANCE.ACCOUNT_ID, accountId)
            .set(ACCOUNT_BALANCE.BALANCE, BigDecimal.ZERO)
            .execute()
    }

    fun refreshBalances(accountIds: List<UUID>) {
        val pending = db
            .select(
                LEDGER_ENTRY.ACCOUNT_ID,
                DSL.coalesce(DSL.sum(LEDGER_ENTRY.AMOUNT), BigDecimal.ZERO).`as`("delta"),
                DSL.field(
                    "((array_agg({0} ORDER BY {1} DESC))[1])",
                    LEDGER_ENTRY.ID.getDataType(),
                    LEDGER_ENTRY.ID,
                    LEDGER_ENTRY.CREATED_AT
                ).`as`("last_id")
            )
            .from(LEDGER_ENTRY)
            .join(ACCOUNT_BALANCE).on(ACCOUNT_BALANCE.ACCOUNT_ID.eq(LEDGER_ENTRY.ACCOUNT_ID))
            .where(LEDGER_ENTRY.ACCOUNT_ID.`in`(accountIds))
            .and(ACCOUNT_BALANCE.LAST_ENTRY_ID.isNull.or(LEDGER_ENTRY.ID.gt(ACCOUNT_BALANCE.LAST_ENTRY_ID)))
            .groupBy(LEDGER_ENTRY.ACCOUNT_ID)
            .asTable("pending")


        db.update(ACCOUNT_BALANCE)
            .set(ACCOUNT_BALANCE.BALANCE, ACCOUNT_BALANCE.BALANCE.plus(pending.field("delta", BigDecimal::class.java)))
            .set(ACCOUNT_BALANCE.LAST_ENTRY_ID, pending.field("last_id", UUID::class.java))
            .from(pending)
            .where(ACCOUNT_BALANCE.ACCOUNT_ID.eq(pending.field(LEDGER_ENTRY.ACCOUNT_ID)))
            .execute()
    }
}
