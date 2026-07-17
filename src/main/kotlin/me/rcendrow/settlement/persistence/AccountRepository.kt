package me.rcendrow.settlement.persistence

import me.rcendrow.jooq.generated.tables.Account.Companion.ACCOUNT
import me.rcendrow.settlement.domain.Account
import me.rcendrow.settlement.domain.AccountStatus
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

    fun findAllByCustomerId(customerId: UUID): List<Account> {
        return db.selectFrom(ACCOUNT)
            .where(ACCOUNT.OWNER_ID.eq(customerId))
            .fetchInto(Account::class.java)
    }

    fun create(account: Account): Account {
        return db.insertInto(ACCOUNT)
            .set(ACCOUNT.ID, account.id)
            .set(ACCOUNT.OWNER_ID, account.customerId)
            .set(ACCOUNT.STATUS, account.status.name)
            .set(ACCOUNT.CREATED_AT, account.createdAt)
            .returning()
            .fetchSingleInto(Account::class.java)
    }

    fun updateStatus(accountId: UUID, status: AccountStatus): Account {
        return db.update(ACCOUNT)
            .set(ACCOUNT.STATUS, status.name)
            .where(ACCOUNT.ID.eq(accountId))
            .returning()
            .fetchSingleInto(Account::class.java)
    }
}
