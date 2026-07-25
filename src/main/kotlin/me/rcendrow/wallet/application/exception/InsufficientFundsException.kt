package me.rcendrow.wallet.application.exception

import java.math.BigDecimal
import java.util.*

class InsufficientFundsException(
    walletId: UUID,
    balance: BigDecimal,
    requested: BigDecimal,
) : RuntimeException("Insufficient funds in wallet $walletId: balance=$balance, requested=$requested")
