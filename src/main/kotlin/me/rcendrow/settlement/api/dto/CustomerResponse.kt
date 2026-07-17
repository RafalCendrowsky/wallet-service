package me.rcendrow.settlement.api.dto

import me.rcendrow.settlement.domain.Customer
import java.time.LocalDateTime
import java.util.*

data class CustomerResponse(
    val id: UUID,
    val email: String,
    val createdAt: LocalDateTime,
) {
    companion object {
        fun from(customer: Customer) = CustomerResponse(
            id = customer.id,
            email = customer.email,
            createdAt = customer.createdAt,
        )
    }
}
