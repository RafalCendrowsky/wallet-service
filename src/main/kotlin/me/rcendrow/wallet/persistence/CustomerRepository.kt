package me.rcendrow.wallet.persistence

import me.rcendrow.jooq.generated.tables.Customer.Companion.CUSTOMER
import me.rcendrow.wallet.domain.Customer
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.util.*

@Repository
class CustomerRepository(private val db: DSLContext) {

    fun findById(id: UUID): Customer? {
        return db.selectFrom(CUSTOMER)
            .where(CUSTOMER.ID.eq(id))
            .fetchOneInto(Customer::class.java)
    }

    fun findByHandle(handle: String): Customer? {
        return db.selectFrom(CUSTOMER)
            .where(CUSTOMER.HANDLE.eq(handle))
            .fetchOneInto(Customer::class.java)
    }

    fun create(customer: Customer): Customer {
        return db.insertInto(CUSTOMER)
            .set(CUSTOMER.ID, customer.id)
            .set(CUSTOMER.HANDLE, customer.handle)
            .set(CUSTOMER.CREATED_AT, customer.createdAt)
            .returning()
            .fetchSingleInto(Customer::class.java)
    }
}
