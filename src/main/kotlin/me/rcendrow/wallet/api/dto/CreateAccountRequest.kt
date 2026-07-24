package me.rcendrow.wallet.api.dto

import jakarta.validation.constraints.NotNull
import java.util.*

data class CreateAccountRequest(
    @field:NotNull
    val customerId: UUID?,
)
