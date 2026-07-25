package me.rcendrow.wallet.api

import jakarta.validation.Valid
import me.rcendrow.wallet.api.dto.*
import me.rcendrow.wallet.application.WalletService
import me.rcendrow.wallet.application.TransferService
import me.rcendrow.wallet.domain.wallet.WalletStatus
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
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
    fun createWallet(@Valid @RequestBody request: CreateWalletRequest): WalletResponse {
        return walletService.createCustomerWallet(request.customerId!!).let { WalletResponse.from(it) }
    }

    @GetMapping("/{id}")
    fun getWallet(@PathVariable id: UUID): WalletResponse {
        return walletService.getCustomerWallet(id).let { WalletResponse.from(it) }
    }

    @GetMapping("/{id}/balance")
    fun getBalance(@PathVariable id: UUID): WalletBalanceResponse {
        return walletService.getBalance(id).let { WalletBalanceResponse.from(it) }
    }

    @GetMapping("/{id}/transfers")
    fun getWalletTransfers(
        @PathVariable id: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): PageResponse<TransferResponse> {
        val pageable = PageRequest.of(page, size)
        return transferService.getWalletTransfers(id, pageable).map { TransferResponse.from(it) }.toResponse()
    }

    @PutMapping("/{id}/status")
    fun suspendWallet(@PathVariable id: UUID, @RequestParam status: WalletStatus): WalletResponse {
        return walletService.updateWalletStatus(id, status).let { WalletResponse.from(it) }
    }
}
