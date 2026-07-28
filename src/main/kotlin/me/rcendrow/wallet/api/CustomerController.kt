package me.rcendrow.wallet.api

import jakarta.validation.Valid
import me.rcendrow.wallet.api.dto.CreateCustomerRequest
import me.rcendrow.wallet.api.dto.CustomerResponse
import me.rcendrow.wallet.application.CustomerService
import me.rcendrow.wallet.domain.CustomerPrincipal
import me.rcendrow.wallet.domain.UserPrincipal
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/customers")
class CustomerController(private val customerService: CustomerService) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createCustomer(
        @Valid @RequestBody request: CreateCustomerRequest,
        @AuthenticationPrincipal principal: UserPrincipal,
    ): CustomerResponse {
        val customer =
            customerService.createCustomer(
                request.handle!!,
                request.displayName!!,
                principal.email,
                principal.issuer,
                principal.externalId
            )
        return CustomerResponse.from(customer)
    }

    @GetMapping
    fun getCustomer(@AuthenticationPrincipal principal: CustomerPrincipal): CustomerResponse {
        val customer = customerService.getCustomer(principal.customerId)
        return CustomerResponse.from(customer)
    }

    @GetMapping("/by-handle/{handle}")
    fun getCustomerByHandle(@PathVariable handle: String): CustomerResponse {
        val customer = customerService.getCustomerByHandle(handle)
        return CustomerResponse.from(customer)
    }
}
