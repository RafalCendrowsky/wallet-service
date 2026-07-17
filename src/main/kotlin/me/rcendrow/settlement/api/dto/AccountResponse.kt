package me.rcendrow.settlement.api.dto

import me.rcendrow.settlement.domain.Account
import me.rcendrow.settlement.domain.AccountStatus
import java.time.LocalDateTime
import java.util.*

data class AccountResponse(
    val id: UUID,
    val customerId: UUID,
    val status: AccountStatus,
    val createdAt: LocalDateTime,
) {
    companion object {
        fun from(account: Account) = AccountResponse(
            id = account.id,
            customerId = account.customerId,
            status = account.status,
            createdAt = account.createdAt,
        )
    }
}
