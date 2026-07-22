package me.rcendrow.settlement.persistence

import me.rcendrow.jooq.generated.tables.Account.Companion.ACCOUNT
import me.rcendrow.jooq.generated.tables.references.CUSTOMER_ACCOUNT
import me.rcendrow.settlement.domain.account.AccountStatus
import me.rcendrow.settlement.domain.account.CustomerAccount
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Repository
class CustomerAccountRepository(private val db: DSLContext) {

    fun findAllByCustomerId(customerId: UUID): List<CustomerAccount> {
        return db.select(*(ACCOUNT.fields() + CUSTOMER_ACCOUNT.CUSTOMER_ID))
            .from(ACCOUNT)
            .innerJoin(CUSTOMER_ACCOUNT).on(CUSTOMER_ACCOUNT.ACCOUNT_ID.eq(ACCOUNT.ID))
            .where(CUSTOMER_ACCOUNT.CUSTOMER_ID.eq(customerId))
            .fetchInto(CustomerAccount::class.java)
    }

    fun findById(id: UUID): CustomerAccount? {
        return db.select(*(ACCOUNT.fields() + CUSTOMER_ACCOUNT.CUSTOMER_ID))
            .from(ACCOUNT)
            .innerJoin(CUSTOMER_ACCOUNT).on(CUSTOMER_ACCOUNT.ACCOUNT_ID.eq(ACCOUNT.ID))
            .where(ACCOUNT.ID.eq(id))
            .fetchOneInto(CustomerAccount::class.java)
    }

    fun lockAccount(id: UUID) {
        db.select(ACCOUNT.ID)
            .from(ACCOUNT)
            .where(ACCOUNT.ID.eq(id))
            .forUpdate()
            .execute()
    }

    @Transactional
    fun create(account: CustomerAccount): CustomerAccount {
        db.insertInto(ACCOUNT)
            .set(ACCOUNT.ID, account.id)
            .set(ACCOUNT.TYPE, account.type.name)
            .set(ACCOUNT.STATUS, account.status.name)
            .set(ACCOUNT.CREATED_AT, account.createdAt)
            .execute()
        db.insertInto(CUSTOMER_ACCOUNT)
            .set(CUSTOMER_ACCOUNT.ACCOUNT_ID, account.id)
            .set(CUSTOMER_ACCOUNT.CUSTOMER_ID, account.customerId)
            .execute()
        return account
    }

    fun updateStatus(account: CustomerAccount, status: AccountStatus): CustomerAccount {
        db.update(ACCOUNT)
            .set(ACCOUNT.STATUS, status.name)
            .where(ACCOUNT.ID.eq(account.id))
            .and(ACCOUNT.STATUS.ne(status.name)).execute()
        return account.copy(status = status)
    }
}
