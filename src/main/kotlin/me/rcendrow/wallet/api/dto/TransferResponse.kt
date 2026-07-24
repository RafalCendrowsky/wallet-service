package me.rcendrow.wallet.api.dto

import me.rcendrow.wallet.domain.Transfer
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

data class TransferResponse(
    val id: UUID,
    val fromAccount: UUID,
    val toAccount: UUID,
    val amount: BigDecimal,
    val idempotencyKey: String,
    val createdAt: LocalDateTime,
) {
    companion object {
        fun from(transfer: Transfer) = TransferResponse(
            id = transfer.id,
            fromAccount = transfer.fromAccount,
            toAccount = transfer.toAccount,
            amount = transfer.amount,
            idempotencyKey = transfer.idempotencyKey,
            createdAt = transfer.createdAt,
        )
    }
}
