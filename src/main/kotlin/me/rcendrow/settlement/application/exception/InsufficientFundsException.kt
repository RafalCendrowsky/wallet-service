package me.rcendrow.settlement.application.exception

import java.math.BigDecimal
import java.util.*

class InsufficientFundsException(
    accountId: UUID,
    balance: BigDecimal,
    requested: BigDecimal,
) : RuntimeException("Insufficient funds in account $accountId: balance=$balance, requested=$requested")
