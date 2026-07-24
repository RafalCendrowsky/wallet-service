package me.rcendrow.wallet.application

import com.fasterxml.uuid.Generators
import me.rcendrow.wallet.application.exception.NotFoundException
import me.rcendrow.wallet.domain.Customer
import me.rcendrow.wallet.persistence.CustomerRepository
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
