package me.rcendrow.wallet

import me.rcendrow.wallet.api.dto.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.client.RestTestClient
import org.springframework.test.web.servlet.client.expectBody
import org.springframework.web.context.WebApplicationContext
import java.math.BigDecimal
import java.util.*

@Import(TestcontainersConfiguration::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WalletServiceWebTests {

    private lateinit var rest: RestTestClient

    @BeforeEach
    fun setUp(context: WebApplicationContext) {
        rest = RestTestClient.bindToApplicationContext(context).build()
    }

    @Test
    fun `should return 201 when creating customer`() {
        rest.post()
            .uri("/customers")
            .body(CreateCustomerRequest(email = "alice@test.com"))
            .exchange()
            .expectStatus().isCreated
    }

    @Test
    fun `should return 201 when creating wallet`() {
        val customerId = createCustomer("wallet-owner@test.com").id

        rest.post()
            .uri("/wallets")
            .body(CreateWalletRequest(customerId = customerId))
            .exchange()
            .expectStatus().isCreated
    }

    @Test
    fun `should return 200 for existing wallet`() {
        val customerId = createCustomer("existing@test.com").id
        val id = createWallet(customerId).id

        rest.get()
            .uri("/wallets/$id")
            .exchange()
            .expectStatus().isOk
    }

    @Test
    fun `should return 404 for unknown wallet`() {
        rest.get()
            .uri("/wallets/${UUID.randomUUID()}")
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    fun `should return 422 for insufficient funds`() {
        val customer1Id = createCustomer("poor@test.com").id
        val customer2Id = createCustomer("rich@test.com").id
        val senderId = createWallet(customer1Id).id
        val receiverId = createWallet(customer2Id).id

        rest.post()
            .uri("/transfers")
            .body(CreateTransferRequest(senderId, receiverId, BigDecimal("1.00"), UUID.randomUUID().toString()))
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT)
    }

    @Test
    fun `should return 400 when customerId is null`() {
        rest.post()
            .uri("/wallets")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"customerId": null}""")
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `should return 400 when transfer fields are null`() {
        rest.post()
            .uri("/transfers")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"fromWallet": null, "toWallet": null, "amount": null, "idempotencyKey": null}""")
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `should return 200 for balance endpoint`() {
        val customerId = createCustomer("balance-test@test.com").id
        val id = createWallet(customerId).id

        rest.get()
            .uri("/wallets/$id/balance")
            .exchange()
            .expectStatus().isOk
    }

    @Test
    fun `should return 200 for empty transfer history`() {
        val customerId = createCustomer("history@test.com").id
        val id = createWallet(customerId).id

        rest.get()
            .uri("/wallets/$id/transfers?page=0&size=3")
            .exchange()
            .expectStatus().isOk
    }

    private fun createCustomer(email: String): CustomerResponse {
        return rest.post()
            .uri("/customers")
            .body(CreateCustomerRequest(email = email))
            .exchange()
            .expectStatus().isCreated
            .expectBody<CustomerResponse>()
            .returnResult()
            .responseBody!!
    }

    private fun createWallet(customerId: UUID): WalletResponse {
        return rest.post()
            .uri("/wallets")
            .body(CreateWalletRequest(customerId = customerId))
            .exchange()
            .expectStatus().isCreated
            .expectBody<WalletResponse>()
            .returnResult()
            .responseBody!!
    }
}
