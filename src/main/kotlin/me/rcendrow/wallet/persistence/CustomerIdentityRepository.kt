package me.rcendrow.wallet.persistence

import me.rcendrow.jooq.generated.tables.CustomerIdentity.Companion.CUSTOMER_IDENTITY
import me.rcendrow.wallet.domain.CustomerIdentity
import org.jooq.DSLContext
import org.springframework.stereotype.Repository

@Repository
class CustomerIdentityRepository(private val db: DSLContext) {

    fun findByIssuerAndExternalId(issuer: String, externalId: String): CustomerIdentity? {
        return db.selectFrom(CUSTOMER_IDENTITY)
            .where(CUSTOMER_IDENTITY.ISSUER.eq(issuer).and(CUSTOMER_IDENTITY.EXTERNAL_ID.eq(externalId)))
            .fetchOneInto(CustomerIdentity::class.java)
    }

    fun create(identity: CustomerIdentity): CustomerIdentity {
        return db.insertInto(CUSTOMER_IDENTITY)
            .set(CUSTOMER_IDENTITY.CUSTOMER_ID, identity.customerId)
            .set(CUSTOMER_IDENTITY.ISSUER, identity.issuer)
            .set(CUSTOMER_IDENTITY.EXTERNAL_ID, identity.externalId)
            .set(CUSTOMER_IDENTITY.EMAIL, identity.email)
            .set(CUSTOMER_IDENTITY.CREATED_AT, identity.createdAt)
            .returning()
            .fetchSingleInto(CustomerIdentity::class.java)
    }
}
