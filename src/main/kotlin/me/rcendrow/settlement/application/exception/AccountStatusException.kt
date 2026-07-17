package me.rcendrow.settlement.application.exception

import me.rcendrow.settlement.domain.AccountStatus
import java.util.*

class AccountStatusException(accountId: UUID, status: AccountStatus) :
    RuntimeException("Account $accountId status ${status.name} is invalid for the operation")
