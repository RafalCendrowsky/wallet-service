package me.rcendrow.wallet.persistence

import me.rcendrow.jooq.generated.tables.references.SERVICE_ACCOUNT
import me.rcendrow.wallet.domain.ServiceAccount
import me.rcendrow.wallet.domain.wallet.ServiceRole
import org.jooq.DSLContext
import org.springframework.stereotype.Repository

@Repository
class ServiceAccountRepository(private val db: DSLContext) {

    fun findAll(): List<ServiceAccount> {
        return db.selectFrom(SERVICE_ACCOUNT)
            .fetchInto(ServiceAccount::class.java)
    }

    fun findByServiceRole(role: ServiceRole): ServiceAccount? {
        return db.selectFrom(SERVICE_ACCOUNT)
            .where(SERVICE_ACCOUNT.ROLE.eq(role.name))
            .fetchOneInto(ServiceAccount::class.java)
    }

    fun create(serviceAccount: ServiceAccount): ServiceAccount {
        return db.insertInto(SERVICE_ACCOUNT)
            .set(SERVICE_ACCOUNT.ID, serviceAccount.id)
            .set(SERVICE_ACCOUNT.ROLE, serviceAccount.role.name)
            .set(SERVICE_ACCOUNT.DISPLAY_NAME, serviceAccount.displayName)
            .returning()
            .fetchSingleInto(ServiceAccount::class.java)
    }
}
