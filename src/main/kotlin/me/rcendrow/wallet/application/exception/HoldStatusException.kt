package me.rcendrow.wallet.application.exception

import me.rcendrow.wallet.domain.HoldStatus
import java.util.*

class HoldStatusException(holdId: UUID, status: HoldStatus) :
    RuntimeException("Operation not permitted for hold $holdId with status ${status.name}")
