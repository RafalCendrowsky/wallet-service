package me.rcendrow.wallet.domain.wallet

import me.rcendrow.wallet.application.exception.WalletStatusException
import java.time.LocalDateTime
import java.util.*

sealed interface Wallet {
    val id: UUID
    val type: WalletType
    val status: WalletStatus
    val createdAt: LocalDateTime

    fun verifyStatus(status: WalletStatus) {
        verifyStatus { it == status }
    }

    fun verifyStatusNot(status: WalletStatus) {
        verifyStatus { it != status }
    }

    private fun verifyStatus(predicate: (WalletStatus) -> Boolean) {
        if (!predicate(status)) {
            throw WalletStatusException(id, status)
        }
    }
}
