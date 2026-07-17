package me.rcendrow.settlement.application

import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import me.rcendrow.settlement.application.exception.NotFoundException
import me.rcendrow.settlement.domain.Customer
import me.rcendrow.settlement.persistence.CustomerRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*

class CustomerServiceTest {

    private val customerRepository: CustomerRepository = mockk()
    private val customerService = CustomerService(customerRepository)

    @AfterEach
    fun tearDown() {
        clearMocks(customerRepository)
    }

    @Test
    fun `should create customer`() {
        val email = "alice@test.com"
        every { customerRepository.create(any()) } answers { firstArg() }

        val result = customerService.createCustomer(email)

        assertThat(result.email).isEqualTo(email)
        assertThat(result.id).isNotNull
        assertThat(result.createdAt).isNotNull
        verify { customerRepository.create(result) }
    }

    @Test
    fun `should return customer by id`() {
        val id = UUID.randomUUID()
        val customer = Customer(id = id, email = "bob@test.com", createdAt = LocalDateTime.now())
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
}
