package me.rcendrow.wallet.persistence.wallet

import me.rcendrow.jooq.generated.tables.WalletBalance.Companion.WALLET_BALANCE
import me.rcendrow.jooq.generated.tables.LedgerEntry.Companion.LEDGER_ENTRY
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.util.*

@Repository
class WalletBalanceRepository(private val db: DSLContext) {
    fun findCurrentBalance(walletId: UUID): BigDecimal {
        val pendingAmount = db.select(DSL.coalesce(DSL.sum(LEDGER_ENTRY.AMOUNT), BigDecimal.ZERO))
            .from(LEDGER_ENTRY)
            .where(LEDGER_ENTRY.WALLET_ID.eq(WALLET_BALANCE.WALLET_ID))
            .and(
                DSL.or(
                    WALLET_BALANCE.LAST_ENTRY_ID.isNull,
                    LEDGER_ENTRY.ID.gt(WALLET_BALANCE.LAST_ENTRY_ID)
                )
            )
            .asField<BigDecimal>()

        return db.select(WALLET_BALANCE.BALANCE.plus(pendingAmount))
            .from(WALLET_BALANCE)
            .where(WALLET_BALANCE.WALLET_ID.eq(walletId))
            .fetchSingleInto(BigDecimal::class.java)
    }

    fun create(walletId: UUID) {
        db.insertInto(WALLET_BALANCE)
            .set(WALLET_BALANCE.WALLET_ID, walletId)
            .set(WALLET_BALANCE.BALANCE, BigDecimal.ZERO)
            .execute()
    }

    fun refreshBalances(walletIds: List<UUID>) {
        val pending = db
            .select(
                LEDGER_ENTRY.WALLET_ID,
                DSL.coalesce(DSL.sum(LEDGER_ENTRY.AMOUNT), BigDecimal.ZERO).`as`("delta"),
                DSL.field(
                    "((array_agg({0} ORDER BY {1} DESC))[1])",
                    LEDGER_ENTRY.ID.getDataType(),
                    LEDGER_ENTRY.ID,
                    LEDGER_ENTRY.CREATED_AT
                ).`as`("last_id")
            )
            .from(LEDGER_ENTRY)
            .join(WALLET_BALANCE).on(WALLET_BALANCE.WALLET_ID.eq(LEDGER_ENTRY.WALLET_ID))
            .where(LEDGER_ENTRY.WALLET_ID.`in`(walletIds))
            .and(WALLET_BALANCE.LAST_ENTRY_ID.isNull.or(LEDGER_ENTRY.ID.gt(WALLET_BALANCE.LAST_ENTRY_ID)))
            .groupBy(LEDGER_ENTRY.WALLET_ID)
            .asTable("pending")


        db.update(WALLET_BALANCE)
            .set(WALLET_BALANCE.BALANCE, WALLET_BALANCE.BALANCE.plus(pending.field("delta", BigDecimal::class.java)))
            .set(WALLET_BALANCE.LAST_ENTRY_ID, pending.field("last_id", UUID::class.java))
            .from(pending)
            .where(WALLET_BALANCE.WALLET_ID.eq(pending.field(LEDGER_ENTRY.WALLET_ID)))
            .execute()
    }
}
