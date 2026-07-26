package me.rcendrow.wallet.api.dto

import jakarta.validation.constraints.NotBlank

data class CaptureHoldRequest(
    @field:NotBlank
    val toCustomerHandle: String?,
)
