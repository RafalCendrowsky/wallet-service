package me.rcendrow.wallet.api

import me.rcendrow.wallet.api.dto.*
import me.rcendrow.wallet.application.TransferService
import me.rcendrow.wallet.application.WalletService
import me.rcendrow.wallet.domain.CustomerPrincipal
import me.rcendrow.wallet.domain.wallet.WalletStatus
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/wallets")
class WalletController(
    private val walletService: WalletService,
    private val transferService: TransferService,
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createWallet(@AuthenticationPrincipal principal: CustomerPrincipal): WalletResponse {
        val wallet = walletService.findOrCreateCustomerWallet(principal.customerId)
        return WalletResponse.from(wallet)
    }

    @GetMapping
    fun getCustomerWallets(@AuthenticationPrincipal principal: CustomerPrincipal): List<WalletResponse> {
        val wallets = walletService.findWalletsByCustomer(principal.customerId)
        return wallets.map { WalletResponse.from(it) }
    }

    @GetMapping("/{id}")
    fun getWallet(
        @PathVariable id: UUID,
        @AuthenticationPrincipal principal: CustomerPrincipal,
    ): WalletResponse {
        val wallet = walletService.getCustomerWallet(principal.customerId, id)
        return WalletResponse.from(wallet)
    }

    @GetMapping("/{id}/balance")
    fun getBalance(
        @PathVariable id: UUID,
        @AuthenticationPrincipal principal: CustomerPrincipal,
    ): WalletBalanceResponse {
        val balance = walletService.getBalance(principal.customerId, id)
        return WalletBalanceResponse.from(balance)
    }

    @GetMapping("/{id}/transfers")
    fun getWalletTransfers(
        @PathVariable id: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @AuthenticationPrincipal principal: CustomerPrincipal,
    ): PageResponse<TransferResponse> {
        val pageable = PageRequest.of(page, size)
        val transfers = transferService.getWalletTransfers(principal.customerId, id, pageable)
        return transfers.map { TransferResponse.from(it) }.toResponse()
    }

    @PutMapping("/{id}/status")
    fun suspendWallet(@PathVariable id: UUID, @RequestParam status: WalletStatus): WalletResponse {
        val wallet = walletService.updateWalletStatus(id, status)
        return WalletResponse.from(wallet)
    }
}
