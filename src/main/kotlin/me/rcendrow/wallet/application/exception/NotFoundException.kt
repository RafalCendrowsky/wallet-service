package me.rcendrow.wallet.application.exception

import java.util.*

class NotFoundException(entity: String, id: Any) : RuntimeException("$entity not found: $id")
