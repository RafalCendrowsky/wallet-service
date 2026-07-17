package me.rcendrow.settlement.api

import jakarta.validation.Valid
import me.rcendrow.settlement.api.dto.AccountResponse
import me.rcendrow.settlement.api.dto.CreateCustomerRequest
import me.rcendrow.settlement.api.dto.CustomerResponse
import me.rcendrow.settlement.application.AccountService
import me.rcendrow.settlement.application.CustomerService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/customers")
class CustomerController(
    private val customerService: CustomerService,
    private val accountService: AccountService,
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

    @GetMapping("/{id}/accounts")
    fun getCustomerAccounts(@PathVariable id: UUID): List<AccountResponse> {
        return accountService.findAccountsByCustomer(id).map { AccountResponse.from(it) }
    }
}
