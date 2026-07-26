package me.rcendrow.wallet.domain

import java.util.*

data class CustomerPrincipal(
    val customerId: UUID,
    override val issuer: String,
    override val externalId: String,
    override val email: String?,
) : UserPrincipal
