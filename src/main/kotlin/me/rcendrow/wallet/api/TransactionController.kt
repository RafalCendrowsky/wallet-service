package me.rcendrow.wallet.api

import jakarta.validation.Valid
import me.rcendrow.wallet.api.dto.CreateDepositRequest
import me.rcendrow.wallet.api.dto.CreateWithdrawalRequest
import me.rcendrow.wallet.api.dto.TransferResponse
import me.rcendrow.wallet.application.TransferService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping
class TransactionController(
    private val transferService: TransferService,
) {

    @PostMapping("/deposits")
    @ResponseStatus(HttpStatus.CREATED)
    fun deposit(@Valid @RequestBody request: CreateDepositRequest): TransferResponse {
        return transferService.createDeposit(
            walletId = request.walletId!!,
            amount = request.amount!!,
            idempotencyKey = request.idempotencyKey!!,
        ).let { TransferResponse.from(it) }
    }

    @PostMapping("/withdrawals")
    @ResponseStatus(HttpStatus.CREATED)
    fun withdraw(@Valid @RequestBody request: CreateWithdrawalRequest): TransferResponse {
        return transferService.createWithdrawal(
            walletId = request.walletId!!,
            amount = request.amount!!,
            idempotencyKey = request.idempotencyKey!!,
        ).let { TransferResponse.from(it) }
    }
}
