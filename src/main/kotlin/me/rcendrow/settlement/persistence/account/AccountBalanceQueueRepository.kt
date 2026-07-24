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
        val queue = db.select(ACCOUNT_BALANCE_QUEUE.ACCOUNT_ID)
            .from(ACCOUNT_BALANCE_QUEUE)
            .orderBy(ACCOUNT_BALANCE_QUEUE.CREATED_AT.asc())
            .limit(limit)
            .forUpdate()
            .skipLocked()
            .asTable("queue")

        return db.delete(ACCOUNT_BALANCE_QUEUE)
            .using(queue)
            .where(ACCOUNT_BALANCE_QUEUE.ACCOUNT_ID.eq(queue.field(ACCOUNT_BALANCE_QUEUE.ACCOUNT_ID)))
            .returning(ACCOUNT_BALANCE_QUEUE.ACCOUNT_ID)
            .fetch(ACCOUNT_BALANCE_QUEUE.ACCOUNT_ID)
            .map { it!! }
    }

}
