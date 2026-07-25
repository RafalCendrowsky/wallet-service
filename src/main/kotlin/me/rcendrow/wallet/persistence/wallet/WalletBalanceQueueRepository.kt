package me.rcendrow.wallet.persistence.wallet

import me.rcendrow.jooq.generated.tables.WalletBalanceQueue.Companion.WALLET_BALANCE_QUEUE
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.util.*

@Repository
class WalletBalanceQueueRepository(private val db: DSLContext) {

    fun insert(walletId: UUID) {
        db.insertInto(WALLET_BALANCE_QUEUE)
            .set(WALLET_BALANCE_QUEUE.WALLET_ID, walletId)
            .onConflictDoNothing()
            .execute()
    }

    fun claimOldestBatch(limit: Int = 1): List<UUID> {
        val queue = db.select(WALLET_BALANCE_QUEUE.WALLET_ID)
            .from(WALLET_BALANCE_QUEUE)
            .orderBy(WALLET_BALANCE_QUEUE.CREATED_AT.asc())
            .limit(limit)
            .forUpdate()
            .skipLocked()
            .asTable("queue")

        return db.delete(WALLET_BALANCE_QUEUE)
            .using(queue)
            .where(WALLET_BALANCE_QUEUE.WALLET_ID.eq(queue.field(WALLET_BALANCE_QUEUE.WALLET_ID)))
            .returning(WALLET_BALANCE_QUEUE.WALLET_ID)
            .fetch(WALLET_BALANCE_QUEUE.WALLET_ID)
            .map { it!! }
    }

}
