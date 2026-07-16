package me.rcendrow.settlement.domain

import java.time.LocalDateTime
import java.util.*

data class Account(val id: UUID, val owner: String, val createdAt: LocalDateTime)
