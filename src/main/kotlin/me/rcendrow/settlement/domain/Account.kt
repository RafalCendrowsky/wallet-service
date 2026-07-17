package me.rcendrow.settlement.domain

import java.time.LocalDateTime
import java.util.*

data class Account(
    val id: UUID,
    val customerId: UUID,
    val status: AccountStatus,
    val createdAt: LocalDateTime,
)
