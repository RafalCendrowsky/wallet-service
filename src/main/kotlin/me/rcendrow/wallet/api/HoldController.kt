package me.rcendrow.wallet.api

import jakarta.validation.Valid
import me.rcendrow.wallet.api.dto.CreateHoldRequest
import me.rcendrow.wallet.api.dto.HoldResponse
import me.rcendrow.wallet.api.dto.TransferResponse
import me.rcendrow.wallet.application.HoldService
import me.rcendrow.wallet.application.WalletService
import me.rcendrow.wallet.domain.CustomerPrincipal
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/holds")
class HoldController(
    private val holdService: HoldService,
    private val walletService: WalletService,
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun placeHold(
        @Valid @RequestBody request: CreateHoldRequest,
        @AuthenticationPrincipal principal: CustomerPrincipal,
    ): HoldResponse {
        walletService.getCustomerWallet(principal.customerId, request.fromWallet!!)
        val hold = holdService.placeHold(
            customerId = principal.customerId,
            fromWallet = request.fromWallet,
            toCustomerHandle = request.toCustomerHandle!!,
            amount = request.amount!!,
            expiresAt = request.expiresAt!!,
        )
        return HoldResponse.from(hold)
    }

    @GetMapping("/{id}")
    fun getHold(
        @PathVariable id: UUID,
        @AuthenticationPrincipal principal: CustomerPrincipal,
    ): HoldResponse {
        val hold = holdService.getHold(principal.customerId, id)
        walletService.getCustomerWallet(principal.customerId, hold.fromWallet)
        return HoldResponse.from(hold)
    }

    @PostMapping("/{id}/capture")
    fun captureHold(
        @PathVariable id: UUID,
        @AuthenticationPrincipal principal: CustomerPrincipal,
    ): TransferResponse {
        val transfer = holdService.captureHold(principal.customerId, id)
        return TransferResponse.from(transfer)
    }

    @PostMapping("/{id}/release")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun releaseHold(
        @PathVariable id: UUID,
        @AuthenticationPrincipal principal: CustomerPrincipal,
    ) {
        val hold = holdService.getHold(principal.customerId, id)
        walletService.getCustomerWallet(principal.customerId, hold.fromWallet)
        holdService.releaseHold(principal.customerId, id)
    }
}
