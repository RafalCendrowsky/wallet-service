package me.rcendrow.wallet.infrastructure.kafka

import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.util.*

@Component
class WalletEventProducer(
    private val kafkaTemplate: KafkaTemplate<String, Any>,
) {

    fun sendWithdrawalInitiated(holdId: UUID, customerId: UUID, walletId: UUID, amount: BigDecimal) {
        val event = WithdrawalInitiatedEvent(holdId, customerId, walletId, amount)
        kafkaTemplate.send("wallet.withdrawal.initiated", holdId.toString(), event)
    }
}
