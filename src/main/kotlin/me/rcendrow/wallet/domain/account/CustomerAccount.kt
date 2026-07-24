package me.rcendrow.wallet.domain.account

import java.time.LocalDateTime
import java.util.*

data class CustomerAccount(
    override val id: UUID,
    override val status: AccountStatus,
    override val createdAt: LocalDateTime,
    val customerId: UUID
) : Account {
    override val type: AccountType = AccountType.CUSTOMER
}
