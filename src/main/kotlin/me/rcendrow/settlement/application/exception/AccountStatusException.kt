package me.rcendrow.settlement.application.exception

import me.rcendrow.settlement.domain.account.AccountStatus
import java.util.*

class AccountStatusException(accountId: UUID, status: AccountStatus) :
    RuntimeException("Operation not permitted for account $accountId with status ${status.name}")
