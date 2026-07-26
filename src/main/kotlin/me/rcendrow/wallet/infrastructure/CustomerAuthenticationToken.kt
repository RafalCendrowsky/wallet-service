package me.rcendrow.wallet.infrastructure

import me.rcendrow.wallet.domain.UserPrincipal
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken

class CustomerAuthenticationToken(
    jwt: Jwt,
    authorities: Collection<GrantedAuthority>,
    private val principal: UserPrincipal,
) : JwtAuthenticationToken(jwt, authorities) {
    override fun getPrincipal(): UserPrincipal = principal

    init {
        isAuthenticated = true
    }
}
