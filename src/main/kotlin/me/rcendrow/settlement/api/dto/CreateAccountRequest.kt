package me.rcendrow.settlement.api.dto

import jakarta.validation.constraints.NotBlank

data class CreateAccountRequest(
    @field:NotBlank
    val owner: String,
)
