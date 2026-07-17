package me.rcendrow.settlement.application

import com.fasterxml.uuid.Generators
import me.rcendrow.settlement.application.exception.NotFoundException
import me.rcendrow.settlement.domain.Customer
import me.rcendrow.settlement.persistence.CustomerRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*

@Service
class CustomerService(private val customerRepository: CustomerRepository) {

    @Transactional(readOnly = true)
    fun getCustomer(id: UUID): Customer {
        return customerRepository.findById(id) ?: throw NotFoundException("Customer", id)
    }

    @Transactional
    fun createCustomer(email: String): Customer {
        return Customer(
            id = Generators.timeBasedEpochRandomGenerator().generate(),
            email = email,
            createdAt = LocalDateTime.now(),
        ).let { customerRepository.create(it) }
    }
}
