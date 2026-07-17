package me.rcendrow.settlement.application.exception

import me.rcendrow.settlement.domain.AccountStatus
import java.util.*

class InvalidAccountStatusTransitionException(
    accountId: UUID,
    current: AccountStatus,
    target: AccountStatus,
) : RuntimeException("Cannot transition account $accountId from $current to $target")
