package me.rcendrow.wallet.domain.account

import me.rcendrow.wallet.application.exception.AccountStatusException
import java.time.LocalDateTime
import java.util.*

sealed interface Account {
    val id: UUID
    val type: AccountType
    val status: AccountStatus
    val createdAt: LocalDateTime

    fun verifyStatus(status: AccountStatus) {
        verifyStatus { it == status }
    }

    fun verifyStatusNot(status: AccountStatus) {
        verifyStatus { it != status }
    }

    private fun verifyStatus(predicate: (AccountStatus) -> Boolean) {
        if (!predicate(status)) {
            throw AccountStatusException(id, status)
        }
    }
}
