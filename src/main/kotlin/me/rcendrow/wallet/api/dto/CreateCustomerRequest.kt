package me.rcendrow.wallet.api.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

data class CreateCustomerRequest(
    @field:NotBlank @field:Email
    val email: String,
)
