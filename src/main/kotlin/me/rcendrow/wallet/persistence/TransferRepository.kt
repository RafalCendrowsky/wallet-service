package me.rcendrow.wallet.persistence

import me.rcendrow.jooq.generated.tables.references.TRANSFER
import me.rcendrow.jooq.generated.tables.references.WALLET_OWNER_VIEW
import me.rcendrow.wallet.application.exception.DuplicateIdempotencyKeyException
import me.rcendrow.wallet.domain.Transfer
import org.jooq.DSLContext
import org.jooq.Records
import org.jooq.impl.DSL
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository
import java.util.*

@Repository
class TransferRepository(private val db: DSLContext) {
    private val fromOwner = WALLET_OWNER_VIEW.`as`("fromOwner")
    private val toOwner = WALLET_OWNER_VIEW.`as`("toOwner")

    fun findById(id: UUID): Transfer? {
        return selectWithOwners()
            .where(TRANSFER.ID.eq(id))
            .fetchOne(Records.mapping(Transfer::from))
    }

    fun findByIdempotencyKey(key: String): Transfer? {
        return selectWithOwners()
            .where(TRANSFER.IDEMPOTENCY_KEY.eq(key))
            .fetchOneInto(Transfer::class.java)
    }

    fun findByWalletId(walletId: UUID, pageable: Pageable): Page<Transfer> {
        val records = selectWithOwners()
            .where(TRANSFER.FROM_WALLET.eq(walletId).or(TRANSFER.TO_WALLET.eq(walletId)))
            .orderBy(TRANSFER.CREATED_AT.desc())
            .limit(pageable.pageSize)
            .offset(pageable.offset)
            .fetch(Records.mapping(Transfer::from))
            .filterNotNull()

        val total = db.select(DSL.count())
            .from(TRANSFER)
            .where(TRANSFER.FROM_WALLET.eq(walletId).or(TRANSFER.TO_WALLET.eq(walletId)))
            .fetchOneInto(Long::class.java)!!

        return PageImpl(records, pageable, total)
    }

    fun create(transfer: Transfer): Transfer {
        try {
            db.insertInto(TRANSFER)
                .set(TRANSFER.ID, transfer.id)
                .set(TRANSFER.FROM_WALLET, transfer.fromWallet)
                .set(TRANSFER.TO_WALLET, transfer.toWallet)
                .set(TRANSFER.AMOUNT, transfer.amount)
                .set(TRANSFER.IDEMPOTENCY_KEY, transfer.idempotencyKey)
                .set(TRANSFER.CREATED_AT, transfer.createdAt)
                .execute()
            return transfer
        } catch (e: DataIntegrityViolationException) {
            val existing = findByIdempotencyKey(transfer.idempotencyKey)
            if (existing != null) {
                throw DuplicateIdempotencyKeyException(transfer.idempotencyKey, existing)
            }
            throw e
        }
    }

    private fun selectWithOwners() = db
        .select(
            TRANSFER.ID,
            TRANSFER.FROM_WALLET,
            fromOwner.ownerField(),
            TRANSFER.TO_WALLET,
            toOwner.ownerField(),
            TRANSFER.AMOUNT,
            TRANSFER.IDEMPOTENCY_KEY,
            TRANSFER.CREATED_AT,
        ).from(TRANSFER)
        .leftJoin(fromOwner).on(fromOwner.WALLET_ID.eq(TRANSFER.FROM_WALLET))
        .leftJoin(toOwner).on(toOwner.WALLET_ID.eq(TRANSFER.TO_WALLET))
}
