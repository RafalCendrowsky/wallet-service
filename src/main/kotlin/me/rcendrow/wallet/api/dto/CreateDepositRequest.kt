package me.rcendrow.wallet.api.dto

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.util.*

data class CreateDepositRequest(
    @field:NotNull
    val accountId: UUID?,

    @field:NotNull
    @field:DecimalMin("0.01")
    val amount: BigDecimal?,

    @field:NotBlank
    val idempotencyKey: String?,
)
