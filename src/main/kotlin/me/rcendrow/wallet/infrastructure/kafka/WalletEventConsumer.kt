package me.rcendrow.wallet.infrastructure.kafka

import me.rcendrow.wallet.application.HoldService
import me.rcendrow.wallet.application.TransferService
import me.rcendrow.wallet.application.WalletService
import me.rcendrow.wallet.domain.wallet.ServiceRole
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class WalletEventConsumer(
    private val transferService: TransferService,
    private val holdService: HoldService,
    private val walletService: WalletService,
) {

    @KafkaListener(topics = ["wallet.deposit.completed"])
    fun handleDepositCompleted(event: DepositCompletedEvent) {
        transferService.createDeposit(
            customerId = event.customerId,
            walletId = event.walletId,
            amount = event.amount,
            idempotencyKey = event.idempotencyKey,
        )
    }

    @KafkaListener(topics = ["wallet.withdrawal.completed"])
    fun handleWithdrawalCompleted(event: WithdrawalCompletedEvent) {
        val serviceWallet = walletService.getServiceWalletByRole(ServiceRole.EXTERNAL_SETTLEMENT)
        holdService.captureHold(serviceWallet.owner.id, event.holdId)
    }
}
