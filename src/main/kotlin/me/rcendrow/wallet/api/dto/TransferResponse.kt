package me.rcendrow.wallet.api.dto

import me.rcendrow.wallet.domain.Transfer
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

data class TransferResponse(
    val id: UUID,
    val from: WalletOwnerResponse?,
    val to: WalletOwnerResponse?,
    val amount: BigDecimal,
    val idempotencyKey: String,
    val createdAt: LocalDateTime,
) {
    companion object {
        fun from(transfer: Transfer) = TransferResponse(
            id = transfer.id,
            from = transfer.fromOwner?.let { WalletOwnerResponse.from(it) },
            to = transfer.toOwner?.let { WalletOwnerResponse.from(it) },
            amount = transfer.amount,
            idempotencyKey = transfer.idempotencyKey,
            createdAt = transfer.createdAt,
        )
    }
}
