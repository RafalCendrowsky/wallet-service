package me.rcendrow.settlement.domain.account

import java.time.LocalDateTime
import java.util.*

data class ServiceAccount(
    override val id: UUID,
    override val status: AccountStatus,
    override val createdAt: LocalDateTime,
    val role: ServiceAccountRole
) : Account {
    override val type: AccountType = AccountType.SERVICE
}
