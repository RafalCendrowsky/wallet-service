package me.rcendrow.wallet.api.dto

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal

data class CreateWithdrawalRequest(
    @field:NotNull
    @field:DecimalMin("0.01")
    val amount: BigDecimal?,
)
