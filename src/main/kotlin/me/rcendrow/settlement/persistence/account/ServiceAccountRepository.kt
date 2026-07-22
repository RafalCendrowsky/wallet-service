package me.rcendrow.settlement.persistence

import me.rcendrow.jooq.generated.tables.Account.Companion.ACCOUNT
import me.rcendrow.jooq.generated.tables.ServiceAccount.Companion.SERVICE_ACCOUNT
import me.rcendrow.settlement.domain.account.ServiceAccount
import me.rcendrow.settlement.domain.account.ServiceAccountRole
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class ServiceAccountRepository(private val db: DSLContext) {

    fun findAll(): List<ServiceAccount> {
        return db.select(*(ACCOUNT.fields() + SERVICE_ACCOUNT.ROLE))
            .from(ACCOUNT)
            .innerJoin(SERVICE_ACCOUNT).on(SERVICE_ACCOUNT.ACCOUNT_ID.eq(ACCOUNT.ID))
            .fetchInto(ServiceAccount::class.java)
    }

    fun findByRole(role: ServiceAccountRole): ServiceAccount {
        return db.select(*(ACCOUNT.fields() + SERVICE_ACCOUNT.ROLE))
            .from(ACCOUNT)
            .innerJoin(SERVICE_ACCOUNT).on(SERVICE_ACCOUNT.ACCOUNT_ID.eq(ACCOUNT.ID))
            .where(SERVICE_ACCOUNT.ROLE.eq(role.name))
            .fetchSingleInto(ServiceAccount::class.java)
    }

    @Transactional
    fun create(account: ServiceAccount): ServiceAccount {
        db.insertInto(ACCOUNT)
            .set(ACCOUNT.ID, account.id)
            .set(ACCOUNT.TYPE, account.type.name)
            .set(ACCOUNT.STATUS, account.status.name)
            .set(ACCOUNT.CREATED_AT, account.createdAt)
            .execute()
        db.insertInto(SERVICE_ACCOUNT)
            .set(SERVICE_ACCOUNT.ACCOUNT_ID, account.id)
            .set(SERVICE_ACCOUNT.ROLE, account.role.name)
            .execute()
        return account
    }
}
