package me.rcendrow.settlement.application.exception

import me.rcendrow.settlement.domain.Transfer

class DuplicateIdempotencyKeyException(val key: String, val existing: Transfer) :
    RuntimeException("Duplicate idempotency key: $key")
