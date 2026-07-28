package me.rcendrow.wallet.domain

import me.rcendrow.wallet.domain.wallet.WalletOwner
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

data class Hold(
    val id: UUID,
    val fromWallet: UUID,
    val fromOwner: WalletOwner?,
    val toWallet: UUID,
    val toOwner: WalletOwner?,
    val amount: BigDecimal,
    val status: HoldStatus,
    val expiresAt: LocalDateTime,
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
            status: String?,
            expiresAt: LocalDateTime?,
            createdAt: LocalDateTime?
        ): Hold? {
            if (id == null) return null
            return Hold(
                id,
                requireNotNull(fromWallet),
                fromOwner,
                requireNotNull(toWallet),
                toOwner,
                requireNotNull(amount),
                requireNotNull(status).let { HoldStatus.valueOf(it) },
                requireNotNull(expiresAt),
                requireNotNull(createdAt)
            )
        }
    }
}
