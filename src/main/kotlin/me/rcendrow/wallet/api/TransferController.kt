package me.rcendrow.wallet.api

import jakarta.validation.Valid
import me.rcendrow.wallet.api.dto.CreateDepositRequest
import me.rcendrow.wallet.api.dto.CreateTransferRequest
import me.rcendrow.wallet.api.dto.CreateWithdrawalRequest
import me.rcendrow.wallet.api.dto.TransferResponse
import me.rcendrow.wallet.application.TransferService
import me.rcendrow.wallet.application.WalletService
import me.rcendrow.wallet.domain.CustomerPrincipal
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/transfers")
class TransferController(
    private val transferService: TransferService,
    private val walletService: WalletService,
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
        val view = transferService.getTransfer(transfer.id)
        return TransferResponse.from(view)
    }

    @GetMapping("/{id}")
    fun getTransfer(@PathVariable id: UUID): TransferResponse {
        val view = transferService.getTransfer(id)
        return TransferResponse.from(view)
    }

    @PostMapping("/deposits")
    @ResponseStatus(HttpStatus.CREATED)
    fun deposit(@Valid @RequestBody request: CreateDepositRequest): TransferResponse {
        val transfer = transferService.createDeposit(
            customerId = request.customerId!!,
            walletId = request.walletId!!,
            amount = request.amount!!,
            idempotencyKey = request.idempotencyKey!!,
        )
        val view = transferService.getTransfer(transfer.id)
        return TransferResponse.from(view)
    }

    @PostMapping("/withdrawals")
    @ResponseStatus(HttpStatus.CREATED)
    fun withdraw(@Valid @RequestBody request: CreateWithdrawalRequest): TransferResponse {
        val transfer = transferService.createWithdrawal(
            customerId = request.customerId!!,
            walletId = request.walletId!!,
            amount = request.amount!!,
            idempotencyKey = request.idempotencyKey!!,
        )
        val view = transferService.getTransfer(transfer.id)
        return TransferResponse.from(view)
    }
}
