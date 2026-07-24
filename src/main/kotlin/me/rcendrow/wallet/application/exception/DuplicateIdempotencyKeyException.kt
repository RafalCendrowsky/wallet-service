package me.rcendrow.wallet.application.exception

import me.rcendrow.wallet.domain.Transfer

class DuplicateIdempotencyKeyException(val key: String, val existing: Transfer) :
    RuntimeException("Duplicate idempotency key: $key")
