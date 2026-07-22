package me.rcendrow.settlement.persistence

import me.rcendrow.jooq.generated.tables.AccountBalance.Companion.ACCOUNT_BALANCE
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

@Repository
class AccountBalanceRepository(private val db: DSLContext) {

    fun findBalance(accountId: UUID): BigDecimal? {
        return db.select(ACCOUNT_BALANCE.BALANCE)
            .from(ACCOUNT_BALANCE)
            .where(ACCOUNT_BALANCE.ACCOUNT_ID.eq(accountId))
            .fetchOneInto(BigDecimal::class.java)
    }

    fun upsert(accountId: UUID, balance: BigDecimal) {
        db.insertInto(ACCOUNT_BALANCE)
            .set(ACCOUNT_BALANCE.ACCOUNT_ID, accountId)
            .set(ACCOUNT_BALANCE.BALANCE, balance)
            .set(ACCOUNT_BALANCE.UPDATED_AT, LocalDateTime.now())
            .onConflict(ACCOUNT_BALANCE.ACCOUNT_ID)
            .doUpdate()
            .set(ACCOUNT_BALANCE.BALANCE, balance)
            .set(ACCOUNT_BALANCE.UPDATED_AT, LocalDateTime.now())
            .execute()
    }

    fun rebuildBalances(balances: Map<UUID, BigDecimal>) {
        db.truncate(ACCOUNT_BALANCE).execute()
        val now = LocalDateTime.now()
        val insertStep = db.insertInto(
            ACCOUNT_BALANCE,
            ACCOUNT_BALANCE.ACCOUNT_ID,
            ACCOUNT_BALANCE.BALANCE,
            ACCOUNT_BALANCE.UPDATED_AT
        )
        balances.forEach { (accountId, balance) ->
            insertStep.values(accountId, balance, now)
        }
        insertStep.execute()
    }
}
