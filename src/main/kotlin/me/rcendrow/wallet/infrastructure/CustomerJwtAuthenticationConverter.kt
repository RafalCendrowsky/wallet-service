package me.rcendrow.wallet.infrastructure

import me.rcendrow.wallet.application.exception.UnauthorizedAccessException
import me.rcendrow.wallet.domain.CustomerPrincipal
import me.rcendrow.wallet.domain.PendingPrincipal
import me.rcendrow.wallet.persistence.CustomerIdentityRepository
import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter
import org.springframework.stereotype.Component

@Component
class CustomerJwtAuthenticationConverter(
    private val customerIdentityRepository: CustomerIdentityRepository,
) : Converter<Jwt, AbstractAuthenticationToken> {

    private val defaultAuthoritiesConverter = JwtGrantedAuthoritiesConverter()

    override fun convert(jwt: Jwt): CustomerAuthenticationToken {
        val issuer = jwt.issuer?.toString() ?: throw UnauthorizedAccessException("Missing issuer in token")
        val externalId = jwt.subject
        val identity = customerIdentityRepository.findByIssuerAndExternalId(issuer, externalId)
        val email = jwt.getClaimAsString("email")

        val (principal, authorities) = if (identity != null) {
            CustomerPrincipal(
                customerId = identity.customerId,
                issuer = issuer,
                externalId = externalId,
                email = email,
            ) to listOf(SimpleGrantedAuthority("ROLE_USER"))
        } else {
            PendingPrincipal(
                issuer = issuer,
                externalId = externalId,
                email = email,
            ) to listOf(SimpleGrantedAuthority("ROLE_PENDING"))
        }

        val allAuthorities = (defaultAuthoritiesConverter.convert(jwt) ?: emptyList()) + authorities
        return CustomerAuthenticationToken(jwt, allAuthorities, principal)
    }
}
