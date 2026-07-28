package me.rcendrow.wallet.persistence

import me.rcendrow.jooq.generated.tables.Hold.Companion.HOLD
import me.rcendrow.jooq.generated.tables.references.WALLET_OWNER_VIEW
import me.rcendrow.wallet.domain.Hold
import me.rcendrow.wallet.domain.HoldStatus
import org.jooq.DSLContext
import org.jooq.Records
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

@Repository
class HoldRepository(private val db: DSLContext) {

    private val fromOwner = WALLET_OWNER_VIEW.`as`("fromOwner")
    private val toOwner = WALLET_OWNER_VIEW.`as`("toOwner")

    fun findByOwnerIdAndId(ownerId: UUID, id: UUID): Hold? {
        return selectWithOwners()
            .where(HOLD.ID.eq(id))
            .and(toOwner.OWNER_ID.eq(ownerId))
            .fetchOne(Records.mapping(Hold::from))
    }

    fun sumActiveAmount(walletId: UUID): BigDecimal {
        return db.select(DSL.coalesce(DSL.sum(HOLD.AMOUNT), BigDecimal.ZERO))
            .from(HOLD)
            .where(HOLD.FROM_WALLET.eq(walletId))
            .and(HOLD.STATUS.eq(HoldStatus.ACTIVE.name))
            .fetchOneInto(BigDecimal::class.java)!!
    }

    fun create(hold: Hold): Hold {
        db.insertInto(HOLD)
            .set(HOLD.ID, hold.id)
            .set(HOLD.FROM_WALLET, hold.fromWallet)
            .set(HOLD.TO_WALLET, hold.toWallet)
            .set(HOLD.AMOUNT, hold.amount)
            .set(HOLD.STATUS, hold.status.name)
            .set(HOLD.EXPIRES_AT, hold.expiresAt)
            .set(HOLD.CREATED_AT, hold.createdAt)
            .execute()
        return hold
    }

    fun updateStatus(hold: Hold, status: HoldStatus): Hold {
        db.update(HOLD)
            .set(HOLD.STATUS, status.name)
            .where(HOLD.ID.eq(hold.id))
            .execute()
        return hold.copy(status = status)
    }

    fun releaseExpiredActiveHolds() {
        db.update(HOLD)
            .set(HOLD.STATUS, HoldStatus.RELEASED.name)
            .where(HOLD.STATUS.eq(HoldStatus.ACTIVE.name))
            .and(HOLD.EXPIRES_AT.le(LocalDateTime.now()))
            .execute()
    }

    private fun selectWithOwners() = db
        .select(
            HOLD.ID,
            HOLD.FROM_WALLET,
            fromOwner.ownerField(),
            HOLD.TO_WALLET,
            toOwner.ownerField(),
            HOLD.AMOUNT,
            HOLD.STATUS,
            HOLD.EXPIRES_AT,
            HOLD.CREATED_AT
        )
        .from(HOLD)
        .leftJoin(fromOwner).on(fromOwner.WALLET_ID.eq(HOLD.FROM_WALLET))
        .leftJoin(toOwner).on(toOwner.WALLET_ID.eq(HOLD.TO_WALLET))
}
