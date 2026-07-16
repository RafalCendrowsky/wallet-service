package me.rcendrow.settlement

import me.rcendrow.settlement.api.dto.AccountResponse
import me.rcendrow.settlement.api.dto.CreateAccountRequest
import me.rcendrow.settlement.api.dto.CreateTransferRequest
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
class SettlementServiceWebTests {

    private lateinit var rest: RestTestClient

    @BeforeEach
    fun setUp(context: WebApplicationContext) {
        rest = RestTestClient.bindToApplicationContext(context).build()
    }

    @Test
    fun `should return 201 when creating account`() {
        rest.post()
            .uri("/accounts")
            .body(CreateAccountRequest(owner = "Alice"))
            .exchange()
            .expectStatus().isCreated
    }

    @Test
    fun `should return 200 for existing account`() {
        val id = createAccount("Bob").id

        rest.get()
            .uri("/accounts/$id")
            .exchange()
            .expectStatus().isOk
    }

    @Test
    fun `should return 404 for unknown account`() {
        rest.get()
            .uri("/accounts/${UUID.randomUUID()}")
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    fun `should return 422 for insufficient funds`() {
        val senderId = createAccount("Poor").id
        val receiverId = createAccount("Rich").id

        rest.post()
            .uri("/transfers")
            .body(CreateTransferRequest(senderId, receiverId, BigDecimal("1.00"), UUID.randomUUID().toString()))
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT)
    }

    @Test
    fun `should return 400 when owner is blank`() {
        rest.post()
            .uri("/accounts")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"owner": ""}""")
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `should return 400 when transfer fields are null`() {
        rest.post()
            .uri("/transfers")
            .contentType(MediaType.APPLICATION_JSON)
            .body("""{"fromAccount": null, "toAccount": null, "amount": null, "idempotencyKey": null}""")
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `should return 200 for balance endpoint`() {
        val id = createAccount("Balance").id

        rest.get()
            .uri("/accounts/$id/balance")
            .exchange()
            .expectStatus().isOk
    }

    @Test
    fun `should return 200 for empty transfer history`() {
        val id = createAccount("History").id

        rest.get()
            .uri("/accounts/$id/transfers?page=0&size=3")
            .exchange()
            .expectStatus().isOk
    }

    private fun createAccount(owner: String): AccountResponse {
        return rest.post()
            .uri("/accounts")
            .body(CreateAccountRequest(owner))
            .exchange()
            .expectStatus().isCreated
            .expectBody<AccountResponse>()
            .returnResult()
            .responseBody!!
    }
}
