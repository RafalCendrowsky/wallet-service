package me.rcendrow.wallet.persistence

import me.rcendrow.jooq.generated.tables.references.TRANSFER
import me.rcendrow.wallet.application.exception.DuplicateIdempotencyKeyException
import me.rcendrow.wallet.domain.Transfer
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository
import java.util.*

@Repository
class TransferRepository(private val db: DSLContext) {

    fun findById(id: UUID): Transfer? {
        return db.selectFrom(TRANSFER)
            .where(TRANSFER.ID.eq(id))
            .fetchOneInto(Transfer::class.java)
    }

    fun findByIdempotencyKey(key: String): Transfer? {
        return db.selectFrom(TRANSFER)
            .where(TRANSFER.IDEMPOTENCY_KEY.eq(key))
            .fetchOneInto(Transfer::class.java)
    }

    fun findByAccountId(accountId: UUID, pageable: Pageable): Page<Transfer> {
        val records = db.selectFrom(TRANSFER)
            .where(TRANSFER.FROM_ACCOUNT.eq(accountId).or(TRANSFER.TO_ACCOUNT.eq(accountId)))
            .orderBy(TRANSFER.CREATED_AT.desc())
            .limit(pageable.pageSize)
            .offset(pageable.offset)
            .fetchInto(Transfer::class.java)

        val total = db.select(DSL.count())
            .from(TRANSFER)
            .where(TRANSFER.FROM_ACCOUNT.eq(accountId).or(TRANSFER.TO_ACCOUNT.eq(accountId)))
            .fetchOneInto(Long::class.java)!!

        return PageImpl(records, pageable, total)
    }

    fun create(transfer: Transfer): Transfer {
        try {
            return db.insertInto(TRANSFER)
                .set(TRANSFER.ID, transfer.id)
                .set(TRANSFER.FROM_ACCOUNT, transfer.fromAccount)
                .set(TRANSFER.TO_ACCOUNT, transfer.toAccount)
                .set(TRANSFER.AMOUNT, transfer.amount)
                .set(TRANSFER.IDEMPOTENCY_KEY, transfer.idempotencyKey)
                .set(TRANSFER.CREATED_AT, transfer.createdAt)
                .returning()
                .fetchSingleInto(Transfer::class.java)
        } catch (e: DataIntegrityViolationException) {
            val existing = findByIdempotencyKey(transfer.idempotencyKey)
            if (existing != null) {
                throw DuplicateIdempotencyKeyException(transfer.idempotencyKey, existing)
            }
            throw e
        }
    }

    fun update(transfer: Transfer): Transfer {
        return db.update(TRANSFER)
            .set(TRANSFER.AMOUNT, transfer.amount)
            .where(TRANSFER.ID.eq(transfer.id))
            .returning()
            .fetchSingleInto(Transfer::class.java)
    }
}
