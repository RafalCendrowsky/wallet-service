package me.rcendrow.settlement.api

import jakarta.validation.Valid
import me.rcendrow.settlement.api.dto.*
import me.rcendrow.settlement.application.AccountService
import me.rcendrow.settlement.application.TransferService
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/accounts")
class AccountController(private val accountService: AccountService, private val transferService: TransferService) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createAccount(@Valid @RequestBody request: CreateAccountRequest): AccountResponse {
        return accountService.createAccount(request.owner).let { AccountResponse.from(it) }
    }

    @GetMapping("/{id}")
    fun getAccount(@PathVariable id: UUID): AccountResponse {
        return accountService.getAccount(id).let { AccountResponse.from(it) }
    }

    @GetMapping("/{id}/balance")
    fun getBalance(@PathVariable id: UUID): BalanceResponse {
        val balance = accountService.getBalance(id)
        return BalanceResponse(accountId = id, balance = balance)
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
}
