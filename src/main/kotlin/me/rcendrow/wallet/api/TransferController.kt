package me.rcendrow.wallet.api

import jakarta.validation.Valid
import me.rcendrow.wallet.api.dto.CreateTransferRequest
import me.rcendrow.wallet.api.dto.TransferResponse
import me.rcendrow.wallet.application.TransferService
import me.rcendrow.wallet.application.WalletService
import me.rcendrow.wallet.domain.CustomerPrincipal
import me.rcendrow.wallet.persistence.TransactionService
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/transfers")
class TransferController(
    private val transferService: TransferService,
    private val walletService: WalletService,
    private val transactionService: TransactionService,
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createTransfer(
        @Valid @RequestBody request: CreateTransferRequest,
        @AuthenticationPrincipal principal: CustomerPrincipal,
    ): TransferResponse {
        walletService.getCustomerWallet(principal.customerId, request.fromWallet!!)
        val transfer = transferService.createTransfer(
            fromCustomerId = principal.customerId,
            fromWallet = request.fromWallet,
            toCustomerId = request.toCustomerId!!,
            amount = request.amount!!,
            idempotencyKey = request.idempotencyKey!!,
        )
        return TransferResponse.from(transfer)
    }

    @GetMapping("/{id}")
    fun getTransfer(@PathVariable id: UUID): TransferResponse {
        val transfer = transferService.getTransfer(id)
        return TransferResponse.from(transfer)
    }
}
