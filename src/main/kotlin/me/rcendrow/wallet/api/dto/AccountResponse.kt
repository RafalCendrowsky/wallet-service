package me.rcendrow.wallet.api.dto

import me.rcendrow.wallet.domain.account.Account
import me.rcendrow.wallet.domain.account.AccountStatus
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
