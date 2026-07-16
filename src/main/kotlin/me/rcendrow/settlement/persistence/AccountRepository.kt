package me.rcendrow.settlement.persistence

import me.rcendrow.jooq.generated.tables.Account.Companion.ACCOUNT
import me.rcendrow.settlement.domain.Account
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.util.*

@Repository
class AccountRepository(private val db: DSLContext) {
    fun findById(id: UUID): Account? {
        return db.selectFrom(ACCOUNT)
            .where(ACCOUNT.ID.eq(id))
            .fetchOneInto(Account::class.java)
    }

    fun lock(accountId: UUID) {
        db.select(ACCOUNT.ID)
            .from(ACCOUNT)
            .where(ACCOUNT.ID.eq(accountId))
            .forUpdate()
            .execute()
    }

    fun create(account: Account): Account {
        return db.insertInto(ACCOUNT)
            .set(ACCOUNT.ID, account.id)
            .set(ACCOUNT.OWNER, account.owner)
            .set(ACCOUNT.CREATED_AT, account.createdAt)
            .returning()
            .fetchSingleInto(Account::class.java)
    }

    fun update(account: Account): Account {
        return db.update(ACCOUNT)
            .set(ACCOUNT.OWNER, account.owner)
            .where(ACCOUNT.ID.eq(account.id))
            .returning()
            .fetchSingleInto(Account::class.java)
    }
}
