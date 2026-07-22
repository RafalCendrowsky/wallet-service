package me.rcendrow.settlement.api.dto

import jakarta.validation.constraints.NotNull
import java.util.*

data class CaptureHoldRequest(
    @field:NotNull
    val toAccount: UUID?,
)
