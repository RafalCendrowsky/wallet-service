package me.rcendrow.wallet.domain

sealed interface UserPrincipal {
    val issuer: String
    val externalId: String
    val email: String?
}
