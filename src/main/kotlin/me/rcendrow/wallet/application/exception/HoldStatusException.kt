package me.rcendrow.wallet.application.exception

import me.rcendrow.wallet.domain.Hold
import me.rcendrow.wallet.domain.HoldStatus
import java.util.*

class HoldStatusException(holdId: UUID, status: HoldStatus) :
    RuntimeException("Operation not permitted for hold $holdId with status ${status.name}") {
    constructor(hold: Hold) : this(hold.id, hold.status)
}
