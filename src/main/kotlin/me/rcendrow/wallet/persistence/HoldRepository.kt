package me.rcendrow.wallet.persistence

import me.rcendrow.jooq.generated.tables.Hold.Companion.HOLD
import me.rcendrow.wallet.domain.Hold
import me.rcendrow.wallet.domain.HoldStatus
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

@Repository
class HoldRepository(private val db: DSLContext) {

    fun findByCustomerIdAndId(customerId: UUID, id: UUID): Hold? {
        return db.selectFrom(HOLD)
            .where(HOLD.ID.eq(id))
            .and(HOLD.CUSTOMER_ID.eq(customerId))
            .fetchSingleInto(Hold::class.java)
    }

    fun sumActiveAmount(walletId: UUID): BigDecimal {
        return db.select(DSL.coalesce(DSL.sum(HOLD.AMOUNT), BigDecimal.ZERO))
            .from(HOLD)
            .where(HOLD.FROM_WALLET.eq(walletId))
            .and(HOLD.STATUS.eq(HoldStatus.ACTIVE.name))
            .fetchOneInto(BigDecimal::class.java)!!
    }

    fun create(hold: Hold): Hold {
        return db.insertInto(HOLD)
            .set(HOLD.ID, hold.id)
            .set(HOLD.FROM_WALLET, hold.fromWallet)
            .set(HOLD.TO_WALLET, hold.toWallet)
            .set(HOLD.CUSTOMER_ID, hold.customerId)
            .set(HOLD.AMOUNT, hold.amount)
            .set(HOLD.STATUS, hold.status.name)
            .set(HOLD.EXPIRES_AT, hold.expiresAt)
            .set(HOLD.CREATED_AT, hold.createdAt)
            .returning()
            .fetchSingleInto(Hold::class.java)
    }

    fun updateStatus(hold: Hold, status: HoldStatus): Hold {
        return db.update(HOLD)
            .set(HOLD.STATUS, status.name)
            .where(HOLD.ID.eq(hold.id))
            .and(HOLD.STATUS.eq(HoldStatus.ACTIVE.name))
            .returning()
            .fetchSingleInto(Hold::class.java)
    }

    fun releaseExpiredActiveHolds() {
        db.update(HOLD)
            .set(HOLD.STATUS, HoldStatus.RELEASED.name)
            .where(HOLD.STATUS.eq(HoldStatus.ACTIVE.name))
            .and(HOLD.EXPIRES_AT.le(LocalDateTime.now()))
            .execute()
    }
}
