package me.rcendrow.wallet.domain

data class PendingPrincipal(
    override val issuer: String,
    override val externalId: String,
    override val email: String?,
) : UserPrincipal
