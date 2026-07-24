package me.rcendrow.wallet.api.dto

import me.rcendrow.wallet.domain.Customer
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
