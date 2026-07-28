package me.rcendrow.wallet.domain.wallet

import me.rcendrow.wallet.application.exception.WalletStatusException
import java.time.LocalDateTime
import java.util.*

data class Wallet(
    val id: UUID,
    val owner: WalletOwner,
    val status: WalletStatus,
    val createdAt: LocalDateTime,
) {
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

    companion object {
        fun from(id: UUID?, owner: WalletOwner?, status: String?, createdAt: LocalDateTime?): Wallet? {
            if (id == null) return null
            return Wallet(
                id = id,
                owner = requireNotNull(owner),
                status = requireNotNull(status).let { WalletStatus.valueOf(it) },
                createdAt = requireNotNull(createdAt)
            )
        }
    }
}
