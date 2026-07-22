package me.rcendrow.settlement.api

import jakarta.validation.Valid
import me.rcendrow.settlement.api.dto.*
import me.rcendrow.settlement.application.HoldService
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
            accountId = request.accountId!!,
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
        return holdService.captureHold(id, request.toAccount!!).let { TransferResponse.from(it) }
    }

    @PostMapping("/{id}/release")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun releaseHold(@PathVariable id: UUID) {
        holdService.releaseHold(id)
    }
}
