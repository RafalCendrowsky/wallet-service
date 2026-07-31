package me.rcendrow.wallet.infrastructure.kafka

import java.math.BigDecimal
import java.util.*

data class DepositCompletedEvent(
    val customerId: UUID,
    val walletId: UUID,
    val amount: BigDecimal,
    val idempotencyKey: String,
)

data class WithdrawalInitiatedEvent(
    val holdId: UUID,
    val customerId: UUID,
    val walletId: UUID,
    val amount: BigDecimal,
)

data class WithdrawalCompletedEvent(
    val holdId: UUID,
)
