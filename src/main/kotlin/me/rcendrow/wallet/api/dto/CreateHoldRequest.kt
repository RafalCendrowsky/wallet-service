package me.rcendrow.wallet.api.dto

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Future
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

data class CreateHoldRequest(
    @field:NotNull
    val accountId: UUID?,

    @field:NotNull
    @field:DecimalMin("0.01")
    val amount: BigDecimal?,

    @field:NotNull
    @field:Future
    val expiresAt: LocalDateTime?,
)
