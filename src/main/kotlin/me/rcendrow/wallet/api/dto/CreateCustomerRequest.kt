package me.rcendrow.wallet.api.dto

import jakarta.validation.constraints.NotBlank

data class CreateCustomerRequest(
    @field:NotBlank
    val handle: String?,

    @field:NotBlank
    val displayName: String?,
)
