package me.rcendrow.settlement.api.dto

import me.rcendrow.settlement.domain.account.Account
import me.rcendrow.settlement.domain.account.AccountStatus
import java.time.LocalDateTime
import java.util.*

data class AccountResponse(
    val id: UUID,
    val status: AccountStatus,
    val createdAt: LocalDateTime,
) {
    companion object {
        fun from(account: Account) = AccountResponse(
            id = account.id,
            status = account.status,
            createdAt = account.createdAt,
        )
    }
}
