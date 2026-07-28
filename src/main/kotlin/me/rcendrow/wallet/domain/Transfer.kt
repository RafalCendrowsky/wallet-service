package me.rcendrow.wallet.domain

import me.rcendrow.wallet.domain.wallet.WalletOwner
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

data class Transfer(
    val id: UUID,
    val fromWallet: UUID,
    val fromOwner: WalletOwner?,
    val toWallet: UUID,
    val toOwner: WalletOwner?,
    val amount: BigDecimal,
    val idempotencyKey: String,
    val createdAt: LocalDateTime,
) {
    companion object {
        fun from(
            id: UUID?,
            fromWallet: UUID?,
            fromOwner: WalletOwner?,
            toWallet: UUID?,
            toOwner: WalletOwner?,
            amount: BigDecimal?,
            idempotencyKey: String?,
            createdAt: LocalDateTime?
        ): Transfer? {
            if (id == null) return null
            return Transfer(
                id,
                requireNotNull(fromWallet),
                fromOwner,
                requireNotNull(toWallet),
                toOwner,
                requireNotNull(amount),
                requireNotNull(idempotencyKey),
                requireNotNull(createdAt)
            )
        }
    }
}
