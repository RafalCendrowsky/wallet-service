package me.rcendrow.settlement.api.dto

import me.rcendrow.settlement.domain.Account
import java.time.LocalDateTime
import java.util.*

data class AccountResponse(
    val id: UUID,
    val owner: String,
    val createdAt: LocalDateTime,
) {
    companion object {
        fun from(account: Account) = AccountResponse(
            id = account.id,
            owner = account.owner,
            createdAt = account.createdAt,
        )
    }
}
