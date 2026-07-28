package me.rcendrow.wallet.domain

import me.rcendrow.wallet.domain.wallet.ServiceRole
import java.util.*

data class Service(
    val id: UUID,
    val role: ServiceRole,
    val displayName: String,
)
