package me.rcendrow.wallet.api

import jakarta.validation.Valid
import me.rcendrow.wallet.api.dto.WalletResponse
import me.rcendrow.wallet.api.dto.CreateCustomerRequest
import me.rcendrow.wallet.api.dto.CustomerResponse
import me.rcendrow.wallet.application.WalletService
import me.rcendrow.wallet.application.CustomerService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/customers")
class CustomerController(
    private val customerService: CustomerService,
    private val walletService: WalletService,
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createCustomer(@Valid @RequestBody request: CreateCustomerRequest): CustomerResponse {
        return customerService.createCustomer(request.email).let { CustomerResponse.from(it) }
    }

    @GetMapping("/{id}")
    fun getCustomer(@PathVariable id: UUID): CustomerResponse {
        return customerService.getCustomer(id).let { CustomerResponse.from(it) }
    }

    @GetMapping("/{id}/wallets")
    fun getCustomerWallets(@PathVariable id: UUID): List<WalletResponse> {
        return walletService.findWalletsByCustomer(id).map { WalletResponse.from(it) }
    }
}
