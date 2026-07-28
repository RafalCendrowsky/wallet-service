package me.rcendrow.wallet.api.dto

import me.rcendrow.wallet.domain.Customer
import java.time.LocalDateTime
import java.util.*

data class CustomerResponse(
    val id: UUID,
    val handle: String,
    val displayName: String,
    val createdAt: LocalDateTime,
) {
    companion object {
        fun from(customer: Customer) = CustomerResponse(
            id = customer.id,
            handle = customer.handle,
            displayName = customer.displayName,
            createdAt = customer.createdAt,
        )
    }
}
