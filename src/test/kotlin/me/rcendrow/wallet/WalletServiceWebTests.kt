package me.rcendrow.wallet

import me.rcendrow.wallet.api.dto.CreateCustomerRequest
import me.rcendrow.wallet.api.dto.CreateTransferRequest
import me.rcendrow.wallet.api.dto.CustomerResponse
import me.rcendrow.wallet.api.dto.WalletResponse
import me.rcendrow.wallet.domain.CustomerPrincipal
import me.rcendrow.wallet.domain.PendingPrincipal
import me.rcendrow.wallet.infrastructure.CustomerAuthenticationToken
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.json.JsonMapper
import java.math.BigDecimal
import java.time.Instant
import java.util.*

@Import(TestcontainersConfiguration::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class WalletServiceWebTests {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jsonMapper: JsonMapper

    private val testIssuer = "test-issuer"
    private val testExternalId = "test-subject"
    private val otherExternalId = "other-subject"
    private val testHandle = "testuser"

    private fun pendingPrincipal(externalId: String = testExternalId) = authentication(
        CustomerAuthenticationToken(
            jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("sub", externalId)
                .claim("iss", testIssuer)
                .claim("email", "$externalId@test.com")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build(),
            authorities = listOf(SimpleGrantedAuthority("ROLE_PENDING")),
            principal = PendingPrincipal(testIssuer, externalId, "$externalId@test.com"),
        )
    )

    private fun customerPrincipal(customerId: UUID, externalId: String = testExternalId) = authentication(
        CustomerAuthenticationToken(
            jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("sub", externalId)
                .claim("iss", testIssuer)
                .claim("email", "$externalId@test.com")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build(),
            authorities = listOf(SimpleGrantedAuthority("ROLE_USER")),
            principal = CustomerPrincipal(customerId, testIssuer, externalId, "$externalId@test.com"),
        )
    )

    private fun createCustomer(handle: String, externalId: String = testExternalId): CustomerResponse {
        val result = mockMvc.perform(
            post("/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(CreateCustomerRequest(handle)))
                .with(pendingPrincipal(externalId))
        )
            .andExpect(status().isCreated)
            .andReturn()

        return jsonMapper.readValue(result.response.contentAsString, CustomerResponse::class.java)
    }

    private fun createWallet(customerId: UUID, externalId: String = testExternalId): WalletResponse {
        val result = mockMvc.perform(
            post("/wallets")
                .with(customerPrincipal(customerId, externalId))
        )
            .andExpect(status().isCreated)
            .andReturn()

        return jsonMapper.readValue(result.response.contentAsString, WalletResponse::class.java)
    }

    @Test
    fun `should return 201 when creating customer`() {
        mockMvc.perform(
            post("/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(CreateCustomerRequest("new-user")))
                .with(pendingPrincipal("new-user"))
        )
            .andExpect(status().isCreated)
    }

    @Test
    fun `should return 201 when creating wallet`() {
        val customer = createCustomer(testHandle)

        mockMvc.perform(post("/wallets").with(customerPrincipal(customer.id)))
            .andExpect(status().isCreated)
    }

    @Test
    fun `should return 200 for existing wallet`() {
        val customer = createCustomer(testHandle)
        val wallet = createWallet(customer.id)

        mockMvc.perform(get("/wallets/${wallet.id}").with(customerPrincipal(customer.id)))
            .andExpect(status().isOk)
    }

    @Test
    fun `should return 404 for unknown wallet`() {
        val customer = createCustomer(testHandle)

        mockMvc.perform(
            get("/wallets/${UUID.randomUUID()}")
                .with(customerPrincipal(customer.id))
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `should return 422 for insufficient funds`() {
        val poorCustomer = createCustomer(testHandle)
        val richCustomer = createCustomer("rich", otherExternalId)

        val poorWallet = createWallet(poorCustomer.id)
        createWallet(richCustomer.id, otherExternalId)

        mockMvc.perform(
            post("/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    jsonMapper.writeValueAsString(
                        CreateTransferRequest(
                            fromWallet = poorWallet.id,
                            toCustomerId = richCustomer.id,
                            amount = BigDecimal("1.00"),
                            idempotencyKey = UUID.randomUUID().toString(),
                        )
                    )
                )
                .with(customerPrincipal(poorCustomer.id))
        )
            .andExpect(status().`is`(HttpStatus.UNPROCESSABLE_CONTENT.value()))
    }

    @Test
    fun `should return 400 when transfer fields are null`() {
        val customer = createCustomer(testHandle)

        mockMvc.perform(
            post("/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"fromWallet": null, "toCustomerId": null, "amount": null, "idempotencyKey": null}""")
                .with(customerPrincipal(customer.id))
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `should return 200 for balance endpoint`() {
        val customer = createCustomer(testHandle)
        val wallet = createWallet(customer.id)

        mockMvc.perform(get("/wallets/${wallet.id}/balance").with(customerPrincipal(customer.id)))
            .andExpect(status().isOk)
    }

    @Test
    fun `should return 200 for empty transfer history`() {
        val customer = createCustomer(testHandle)
        val wallet = createWallet(customer.id)

        mockMvc.perform(
            get("/wallets/${wallet.id}/transfers?page=0&size=3")
                .with(customerPrincipal(customer.id))
        )
            .andExpect(status().isOk)
    }
}
