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
        return transferService.createTransfer(
            fromCustomerId = principal.customerId,
            fromWallet = request.fromWallet,
            toCustomerHandle = request.toCustomerHandle!!,
            amount = request.amount!!,
            idempotencyKey = request.idempotencyKey!!,
        ).let { TransferResponse.from(it) }
    }

    @GetMapping("/{id}")
    fun getTransfer(@PathVariable id: UUID): TransferResponse {
        return transferService.getTransfer(id).let { TransferResponse.from(it) }
    }

    @PostMapping("/deposits")
    @ResponseStatus(HttpStatus.CREATED)
    fun deposit(@Valid @RequestBody request: CreateDepositRequest): TransferResponse {
        return transferService.createDeposit(
            customerId = request.customerId!!,
            walletId = request.walletId!!,
            amount = request.amount!!,
            idempotencyKey = request.idempotencyKey!!,
        ).let { TransferResponse.from(it) }
    }

    @PostMapping("/withdrawals")
    @ResponseStatus(HttpStatus.CREATED)
    fun withdraw(@Valid @RequestBody request: CreateWithdrawalRequest): TransferResponse {
        return transferService.createWithdrawal(
            customerId = request.customerId!!,
            walletId = request.walletId!!,
            amount = request.amount!!,
            idempotencyKey = request.idempotencyKey!!,
        ).let { TransferResponse.from(it) }
    }
}
