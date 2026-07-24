package me.rcendrow.wallet.api

import jakarta.validation.Valid
import me.rcendrow.wallet.api.dto.*
import me.rcendrow.wallet.application.AccountService
import me.rcendrow.wallet.application.TransferService
import me.rcendrow.wallet.domain.account.AccountStatus
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/accounts")
class AccountController(
    private val accountService: AccountService,
    private val transferService: TransferService,
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createAccount(@Valid @RequestBody request: CreateAccountRequest): AccountResponse {
        return accountService.createCustomerAccount(request.customerId!!).let { AccountResponse.from(it) }
    }

    @GetMapping("/{id}")
    fun getAccount(@PathVariable id: UUID): AccountResponse {
        return accountService.getCustomerAccount(id).let { AccountResponse.from(it) }
    }

    @GetMapping("/{id}/balance")
    fun getBalance(@PathVariable id: UUID): AccountBalanceResponse {
        return accountService.getBalance(id).let { AccountBalanceResponse.from(it) }
    }

    @GetMapping("/{id}/transfers")
    fun getAccountTransfers(
        @PathVariable id: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): PageResponse<TransferResponse> {
        val pageable = PageRequest.of(page, size)
        return transferService.getAccountTransfers(id, pageable).map { TransferResponse.from(it) }.toResponse()
    }

    @PutMapping("/{id}/status")
    fun suspendAccount(@PathVariable id: UUID, @RequestParam status: AccountStatus): AccountResponse {
        return accountService.updateAccountStatus(id, status).let { AccountResponse.from(it) }
    }
}
