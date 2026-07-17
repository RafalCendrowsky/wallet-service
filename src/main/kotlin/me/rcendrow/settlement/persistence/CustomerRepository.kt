package me.rcendrow.settlement.persistence

import me.rcendrow.jooq.generated.tables.Customer.Companion.CUSTOMER
import me.rcendrow.settlement.domain.Customer
import org.jooq.DSLContext
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Repository
import java.util.*

@Repository
class CustomerRepository(private val db: DSLContext) {

    fun findById(id: UUID): Customer? {
        return db.selectFrom(CUSTOMER)
            .where(CUSTOMER.ID.eq(id))
            .fetchOneInto(Customer::class.java)
    }

    fun findByEmail(email: String): Customer? {
        return db.selectFrom(CUSTOMER)
            .where(CUSTOMER.EMAIL.eq(email))
            .fetchOneInto(Customer::class.java)
    }

    fun create(customer: Customer): Customer {
        try {
            return db.insertInto(CUSTOMER)
                .set(CUSTOMER.ID, customer.id)
                .set(CUSTOMER.EMAIL, customer.email)
                .set(CUSTOMER.CREATED_AT, customer.createdAt)
                .returning()
                .fetchSingleInto(Customer::class.java)
        } catch (e: DuplicateKeyException) {
            throw IllegalArgumentException("Customer with email ${customer.email} already exists")
        }
    }
}
