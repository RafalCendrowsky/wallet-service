package me.rcendrow.wallet.persistence

import me.rcendrow.wallet.application.HoldService
import me.rcendrow.wallet.application.WalletService
import me.rcendrow.wallet.domain.Hold
import me.rcendrow.wallet.domain.wallet.ServiceRole
import me.rcendrow.wallet.infrastructure.kafka.WalletEventProducer
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

@Service
class TransactionService(
    private val holdService: HoldService,
    private val walletService: WalletService,
    private val walletEventProducer: WalletEventProducer,
) {
    fun initiateWithdrawal(customerId: UUID, walletId: UUID, amount: BigDecimal): Hold {
        val customerWallet = walletService.getCustomerWallet(customerId, walletId)
        val serviceWallet = walletService.getServiceWalletByRole(ServiceRole.EXTERNAL_SETTLEMENT)
        val expiresAt = LocalDateTime.now().plusDays(14)
        val hold = holdService.placeHold(customerWallet, serviceWallet, amount, expiresAt)
        walletEventProducer.sendWithdrawalInitiated(hold.id, customerWallet.id, customerId, amount)
        return hold
    }
}
