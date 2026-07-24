package me.rcendrow.wallet.api.dto

import jakarta.validation.constraints.NotNull
import java.util.*

data class CaptureHoldRequest(
    @field:NotNull
    val toAccount: UUID?,
)
