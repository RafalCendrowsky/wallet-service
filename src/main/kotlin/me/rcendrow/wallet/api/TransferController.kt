package me.rcendrow.wallet.api

import jakarta.validation.Valid
import me.rcendrow.wallet.api.dto.CreateTransferRequest
import me.rcendrow.wallet.api.dto.TransferResponse
import me.rcendrow.wallet.application.TransferService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
class TransferController(
    private val transferService: TransferService,
) {

    @PostMapping("/transfers")
    @ResponseStatus(HttpStatus.CREATED)
    fun createTransfer(@Valid @RequestBody request: CreateTransferRequest): TransferResponse {
        return transferService.createTransfer(
            fromAccount = request.fromAccount!!,
            toAccount = request.toAccount!!,
            amount = request.amount!!,
            idempotencyKey = request.idempotencyKey!!,
        ).let { TransferResponse.from(it) }
    }

    @GetMapping("/transfers/{id}")
    fun getTransfer(@PathVariable id: UUID): TransferResponse {
        return transferService.getTransfer(id).let { TransferResponse.from(it) }
    }
}
