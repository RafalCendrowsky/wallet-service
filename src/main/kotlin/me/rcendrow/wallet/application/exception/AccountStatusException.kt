package me.rcendrow.wallet.application.exception

import me.rcendrow.wallet.domain.account.AccountStatus
import java.util.*

class AccountStatusException(accountId: UUID, status: AccountStatus) :
    RuntimeException("Operation not permitted for account $accountId with status ${status.name}")
