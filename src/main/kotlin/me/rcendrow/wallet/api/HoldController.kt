package me.rcendrow.wallet.api

import jakarta.validation.Valid
import me.rcendrow.wallet.api.dto.CreateHoldRequest
import me.rcendrow.wallet.api.dto.HoldResponse
import me.rcendrow.wallet.api.dto.TransferResponse
import me.rcendrow.wallet.application.HoldService
import me.rcendrow.wallet.application.TransferService
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
    private val transferService: TransferService,
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
            fromWalletId = request.fromWallet,
            toCustomerId = request.toCustomerId!!,
            amount = request.amount!!,
            expiresAt = request.expiresAt!!,
        )
        val view = holdService.getHold(principal.customerId, hold.id)
        return HoldResponse.from(view)
    }

    @GetMapping("/{id}")
    fun getHold(
        @PathVariable id: UUID,
        @AuthenticationPrincipal principal: CustomerPrincipal,
    ): HoldResponse {
        val view = holdService.getHold(principal.customerId, id)
        return HoldResponse.from(view)
    }

    @PostMapping("/{id}/capture")
    fun captureHold(
        @PathVariable id: UUID,
        @AuthenticationPrincipal principal: CustomerPrincipal,
    ): TransferResponse {
        val transfer = holdService.captureHold(principal.customerId, id)
        val view = transferService.getTransfer(transfer.id)
        return TransferResponse.from(view)
    }

    @PostMapping("/{id}/release")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun releaseHold(
        @PathVariable id: UUID,
        @AuthenticationPrincipal principal: CustomerPrincipal,
    ) {
        holdService.releaseHold(principal.customerId, id)
    }
}
