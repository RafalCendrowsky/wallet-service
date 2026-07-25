package me.rcendrow.wallet.api.dto

import me.rcendrow.wallet.domain.Transfer
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

data class TransferResponse(
    val id: UUID,
    val fromWallet: UUID,
    val toWallet: UUID,
    val amount: BigDecimal,
    val idempotencyKey: String,
    val createdAt: LocalDateTime,
) {
    companion object {
        fun from(transfer: Transfer) = TransferResponse(
            id = transfer.id,
            fromWallet = transfer.fromWallet,
            toWallet = transfer.toWallet,
            amount = transfer.amount,
            idempotencyKey = transfer.idempotencyKey,
            createdAt = transfer.createdAt,
        )
    }
}
