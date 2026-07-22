package me.rcendrow.settlement.api

import jakarta.validation.Valid
import me.rcendrow.settlement.api.dto.CreateDepositRequest
import me.rcendrow.settlement.api.dto.CreateWithdrawalRequest
import me.rcendrow.settlement.api.dto.TransferResponse
import me.rcendrow.settlement.application.TransferService
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
            accountId = request.accountId!!,
            amount = request.amount!!,
            idempotencyKey = request.idempotencyKey!!,
        ).let { TransferResponse.from(it) }
    }

    @PostMapping("/withdrawals")
    @ResponseStatus(HttpStatus.CREATED)
    fun withdraw(@Valid @RequestBody request: CreateWithdrawalRequest): TransferResponse {
        return transferService.createWithdrawal(
            accountId = request.accountId!!,
            amount = request.amount!!,
            idempotencyKey = request.idempotencyKey!!,
        ).let { TransferResponse.from(it) }
    }
}
