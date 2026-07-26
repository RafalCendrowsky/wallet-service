package me.rcendrow.wallet.domain

import java.time.LocalDateTime
import java.util.*

data class CustomerIdentity(
    val customerId: UUID,
    val issuer: String,
    val externalId: String,
    val email: String?,
    val createdAt: LocalDateTime,
)
