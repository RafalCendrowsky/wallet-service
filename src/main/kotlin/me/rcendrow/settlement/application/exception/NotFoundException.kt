package me.rcendrow.settlement.application.exception

import java.util.*

class NotFoundException(entity: String, id: UUID) : RuntimeException("$entity not found: $id")
