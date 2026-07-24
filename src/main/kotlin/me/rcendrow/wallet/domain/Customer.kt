package me.rcendrow.wallet.domain

import java.time.LocalDateTime
import java.util.*

data class Customer(
    val id: UUID,
    val email: String,
    val createdAt: LocalDateTime,
)
