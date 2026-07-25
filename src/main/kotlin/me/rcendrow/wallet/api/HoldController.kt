package me.rcendrow.wallet.api

import jakarta.validation.Valid
import me.rcendrow.wallet.api.dto.CaptureHoldRequest
import me.rcendrow.wallet.api.dto.CreateHoldRequest
import me.rcendrow.wallet.api.dto.HoldResponse
import me.rcendrow.wallet.api.dto.TransferResponse
import me.rcendrow.wallet.application.HoldService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/holds")
class HoldController(private val holdService: HoldService) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun placeHold(@Valid @RequestBody request: CreateHoldRequest): HoldResponse {
        return holdService.placeHold(
            walletId = request.walletId!!,
            amount = request.amount!!,
            expiresAt = request.expiresAt!!,
        ).let { HoldResponse.from(it) }
    }

    @GetMapping("/{id}")
    fun getHold(@PathVariable id: UUID): HoldResponse {
        return holdService.getHold(id).let { HoldResponse.from(it) }
    }

    @PostMapping("/{id}/capture")
    fun captureHold(
        @PathVariable id: UUID,
        @Valid @RequestBody request: CaptureHoldRequest,
    ): TransferResponse {
        return holdService.captureHold(id, request.toWallet!!).let { TransferResponse.from(it) }
    }

    @PostMapping("/{id}/release")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun releaseHold(@PathVariable id: UUID) {
        holdService.releaseHold(id)
    }
}
