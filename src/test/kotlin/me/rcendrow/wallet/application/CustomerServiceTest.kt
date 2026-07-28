package me.rcendrow.wallet.application

import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import me.rcendrow.wallet.application.exception.NotFoundException
import me.rcendrow.wallet.domain.Customer
import me.rcendrow.wallet.domain.CustomerIdentity
import me.rcendrow.wallet.persistence.CustomerIdentityRepository
import me.rcendrow.wallet.persistence.CustomerRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*

class CustomerServiceTest {

    private val customerRepository: CustomerRepository = mockk()
    private val customerIdentityRepository: CustomerIdentityRepository = mockk()
    private val customerService = CustomerService(customerRepository, customerIdentityRepository)

    @AfterEach
    fun tearDown() {
        clearMocks(customerRepository, customerIdentityRepository)
    }

    @Test
    fun `should create customer`() {
        val handle = "alice"
        val email = "alice@test.com"
        val issuer = "https://issuer.example.com"
        val externalId = "ext-123"
        every { customerIdentityRepository.findByIssuerAndExternalId(issuer, externalId) } returns null
        every { customerRepository.findByHandle(handle) } returns null
        every { customerRepository.create(any()) } answers { firstArg() }
        every { customerIdentityRepository.create(any()) } returns mockk()

        val result = customerService.createCustomer(handle, handle, email, issuer, externalId)

        assertThat(result.id).isNotNull
        assertThat(result.handle).isEqualTo(handle)
        assertThat(result.displayName).isEqualTo(handle)
        assertThat(result.createdAt).isNotNull
        verify { customerRepository.create(result) }
        verify { customerIdentityRepository.create(any()) }
    }

    @Test
    fun `should return existing customer on duplicate identity`() {
        val handle = "alice"
        val email = "alice@test.com"
        val issuer = "https://issuer.example.com"
        val externalId = "ext-123"
        val customerId = UUID.randomUUID()
        val customer = Customer(id = customerId, handle = handle, displayName = handle, createdAt = LocalDateTime.now())
        every { customerIdentityRepository.findByIssuerAndExternalId(issuer, externalId) } returns CustomerIdentity(
            customerId = customerId,
            issuer = issuer,
            externalId = externalId,
            email = email,
            createdAt = LocalDateTime.now(),
        )
        every { customerRepository.findById(customerId) } returns customer

        val result = customerService.createCustomer(handle, handle, email, issuer, externalId)

        assertThat(result).isEqualTo(customer)
    }

    @Test
    fun `should return existing customer for same handle and same identity`() {
        val handle = "alice"
        val email = "alice@test.com"
        val issuer = "https://issuer.example.com"
        val externalId = "ext-123"
        val customerId = UUID.randomUUID()
        val customer = Customer(id = customerId, handle = handle, displayName = handle, createdAt = LocalDateTime.now())
        every { customerIdentityRepository.findByIssuerAndExternalId(issuer, externalId) } returns CustomerIdentity(
            customerId = customerId,
            issuer = issuer,
            externalId = externalId,
            email = email,
            createdAt = LocalDateTime.now()
        )
        every { customerRepository.findById(customerId) } returns customer

        val result = customerService.createCustomer(handle, handle, email, issuer, externalId)

        assertThat(result).isEqualTo(customer)
    }

    @Test
    fun `should reject different handle for same identity`() {
        val handle = "alice"
        val differentHandle = "bob"
        val email = "alice@test.com"
        val issuer = "https://issuer.example.com"
        val externalId = "ext-123"
        val customerId = UUID.randomUUID()
        val customer = Customer(id = customerId, handle = handle, displayName = handle, createdAt = LocalDateTime.now())
        every { customerIdentityRepository.findByIssuerAndExternalId(issuer, externalId) } returns CustomerIdentity(
            customerId = customerId,
            issuer = issuer,
            externalId = externalId,
            email = email,
            createdAt = LocalDateTime.now()
        )
        every { customerRepository.findById(customerId) } returns customer

        assertThatThrownBy {
            customerService.createCustomer(
                differentHandle,
                differentHandle,
                email,
                issuer,
                externalId
            )
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("already registered with handle")
    }

    @Test
    fun `should reject duplicate handle for different identity`() {
        val handle = "alice"
        val email = "alice@test.com"
        val issuer = "https://issuer.example.com"
        val externalId = "ext-123"
        every { customerIdentityRepository.findByIssuerAndExternalId(issuer, externalId) } returns null
        every { customerRepository.create(any()) } throws IllegalArgumentException("handle already taken")

        assertThatThrownBy { customerService.createCustomer(handle, handle, email, issuer, externalId) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("handle already taken")
    }

    @Test
    fun `should return customer by id`() {
        val id = UUID.randomUUID()
        val customer = Customer(id = id, handle = "bob", displayName = "bob", createdAt = LocalDateTime.now())
        every { customerRepository.findById(id) } returns customer

        val result = customerService.getCustomer(id)

        assertThat(result).isEqualTo(customer)
    }

    @Test
    fun `should throw NotFoundException for unknown customer`() {
        val id = UUID.randomUUID()
        every { customerRepository.findById(id) } returns null

        assertThatThrownBy { customerService.getCustomer(id) }
            .isInstanceOf(NotFoundException::class.java)
    }

    @Test
    fun `should return customer by handle`() {
        val handle = "charlie"
        val customer =
            Customer(id = UUID.randomUUID(), handle = handle, displayName = handle, createdAt = LocalDateTime.now())
        every { customerRepository.findByHandle(handle) } returns customer

        val result = customerService.getCustomerByHandle(handle)

        assertThat(result).isEqualTo(customer)
    }

    @Test
    fun `should throw NotFoundException for unknown handle`() {
        val handle = "unknown"
        every { customerRepository.findByHandle(handle) } returns null

        assertThatThrownBy { customerService.getCustomerByHandle(handle) }
            .isInstanceOf(NotFoundException::class.java)
    }
}
