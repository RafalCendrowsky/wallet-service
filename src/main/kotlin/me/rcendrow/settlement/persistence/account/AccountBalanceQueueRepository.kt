package me.rcendrow.settlement.persistence.account

import me.rcendrow.jooq.generated.tables.AccountBalanceQueue.Companion.ACCOUNT_BALANCE_QUEUE
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.util.*

@Repository
class AccountBalanceQueueRepository(private val db: DSLContext) {

    fun insert(accountId: UUID) {
        db.insertInto(ACCOUNT_BALANCE_QUEUE)
            .set(ACCOUNT_BALANCE_QUEUE.ACCOUNT_ID, accountId)
            .onConflictDoNothing()
            .execute()
    }

    fun claimOldestBatch(limit: Int = 1): List<UUID> {
        return db.deleteFrom(ACCOUNT_BALANCE_QUEUE)
            .where(
                ACCOUNT_BALANCE_QUEUE.ACCOUNT_ID.`in`(
                    db.select(ACCOUNT_BALANCE_QUEUE.ACCOUNT_ID)
                        .from(ACCOUNT_BALANCE_QUEUE)
                        .orderBy(ACCOUNT_BALANCE_QUEUE.CREATED_AT.desc())
                        .limit(limit)
                        .forUpdate()
                        .skipLocked()
                        .fetch()
                )
            )
            .returning()
            .fetch(ACCOUNT_BALANCE_QUEUE.ACCOUNT_ID)
            .map { it!! }
    }

}
