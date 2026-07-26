package me.rcendrow.wallet.infrastructure

import io.mockk.every
import io.mockk.mockk
import me.rcendrow.wallet.application.exception.UnauthorizedAccessException
import me.rcendrow.wallet.domain.CustomerIdentity
import me.rcendrow.wallet.domain.CustomerPrincipal
import me.rcendrow.wallet.domain.PendingPrincipal
import me.rcendrow.wallet.persistence.CustomerIdentityRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import java.time.Instant
import java.time.LocalDateTime
import java.util.*

class CustomerJwtAuthenticationConverterTest {

    private val customerIdentityRepository = mockk<CustomerIdentityRepository>()
    private val converter = CustomerJwtAuthenticationConverter(customerIdentityRepository)

    private val issuer = "https://issuer.example.com"
    private val externalId = "user123"
    private val email = "user@example.com"

    private val defaultJwt = Jwt.withTokenValue("token")
        .header("alg", "RS256")
        .claim("sub", externalId)
        .claim("iss", issuer)
        .claim("email", email)
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(3600))
        .build()

    @Test
    fun `should return CustomerAuthenticationToken with CustomerPrincipal and ROLE_USER when identity exists`() {
        val customerId = UUID.randomUUID()
        val identity = CustomerIdentity(
            customerId = customerId,
            issuer = issuer,
            externalId = externalId,
            email = email,
            createdAt = LocalDateTime.now(),
        )
        every { customerIdentityRepository.findByIssuerAndExternalId(issuer, externalId) } returns identity

        val token = converter.convert(defaultJwt)

        assertThat(token).isInstanceOf(CustomerAuthenticationToken::class.java)
        assertThat(token.principal).isInstanceOf(CustomerPrincipal::class.java)
        val principal = token.principal as CustomerPrincipal
        assertThat(principal.customerId).isEqualTo(customerId)
        assertThat(principal.issuer).isEqualTo(issuer)
        assertThat(principal.externalId).isEqualTo(externalId)
        assertThat(principal.email).isEqualTo(email)
        assertThat(token.authorities).hasSize(1)
        assertThat(token.authorities).contains(SimpleGrantedAuthority("ROLE_USER"))
    }

    @Test
    fun `should return CustomerAuthenticationToken with PendingPrincipal and ROLE_PENDING when identity does not exist`() {
        every { customerIdentityRepository.findByIssuerAndExternalId(issuer, externalId) } returns null

        val token = converter.convert(defaultJwt)

        assertThat(token).isInstanceOf(CustomerAuthenticationToken::class.java)
        assertThat(token.principal).isInstanceOf(PendingPrincipal::class.java)
        val principal = token.principal as PendingPrincipal
        assertThat(principal.issuer).isEqualTo(issuer)
        assertThat(principal.externalId).isEqualTo(externalId)
        assertThat(principal.email).isEqualTo(email)
        assertThat(token.authorities).hasSize(1)
        assertThat(token.authorities).contains(SimpleGrantedAuthority("ROLE_PENDING"))
    }

    @Test
    fun `should throw UnauthorizedAccessException when issuer is missing`() {
        val jwt = Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .claim("sub", externalId)
            .claim("email", email)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build()

        assertThrows<UnauthorizedAccessException> {
            converter.convert(jwt)
        }
    }

    @Test
    fun `should return token with email null when email claim is missing`() {
        val identity = CustomerIdentity(
            customerId = UUID.randomUUID(),
            issuer = issuer,
            externalId = externalId,
            email = email,
            createdAt = LocalDateTime.now(),
        )
        every { customerIdentityRepository.findByIssuerAndExternalId(issuer, externalId) } returns identity

        val jwt = Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .claim("sub", externalId)
            .claim("iss", issuer)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build()

        val token = converter.convert(jwt)

        val principal = token.principal as CustomerPrincipal
        assertThat(principal.email).isNull()
    }
}
