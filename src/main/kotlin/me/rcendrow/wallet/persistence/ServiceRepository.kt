package me.rcendrow.wallet.persistence

import me.rcendrow.jooq.generated.tables.references.SERVICE_ACCOUNT
import me.rcendrow.wallet.domain.Service
import me.rcendrow.wallet.domain.wallet.ServiceRole
import org.jooq.DSLContext
import org.springframework.stereotype.Repository

@Repository
class ServiceRepository(private val db: DSLContext) {

    fun findByServiceRole(role: ServiceRole): Service? {
        return db.selectFrom(SERVICE_ACCOUNT)
            .where(SERVICE_ACCOUNT.ROLE.eq(role.name))
            .fetchOneInto(Service::class.java)
    }
}
