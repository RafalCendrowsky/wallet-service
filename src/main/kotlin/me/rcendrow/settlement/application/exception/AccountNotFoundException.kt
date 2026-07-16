package me.rcendrow.settlement.application.exception

import java.util.*

class AccountNotFoundException(id: UUID) : RuntimeException("Account not found: $id")
