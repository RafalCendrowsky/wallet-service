package me.rcendrow.wallet.domain

import java.time.LocalDateTime
import java.util.*

data class Customer(
    val id: UUID,
    val handle: String,
    val displayName: String,
    val createdAt: LocalDateTime,
)
