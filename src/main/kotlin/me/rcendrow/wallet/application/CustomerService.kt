package me.rcendrow.wallet.application

import com.fasterxml.uuid.Generators
import me.rcendrow.wallet.application.exception.NotFoundException
import me.rcendrow.wallet.domain.Customer
import me.rcendrow.wallet.domain.CustomerIdentity
import me.rcendrow.wallet.persistence.CustomerIdentityRepository
import me.rcendrow.wallet.persistence.CustomerRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*

@Service
class CustomerService(
    private val customerRepository: CustomerRepository,
    private val customerIdentityRepository: CustomerIdentityRepository,
) {

    @Transactional(readOnly = true)
    fun getCustomer(id: UUID): Customer {
        return customerRepository.findById(id) ?: throw NotFoundException("Customer", id)
    }

    @Transactional(readOnly = true)
    fun getCustomerByHandle(handle: String): Customer {
        return customerRepository.findByHandle(handle)
            ?: throw NotFoundException("Customer with handle", handle)
    }

    @Transactional
    fun createCustomer(
        handle: String,
        displayName: String,
        email: String?,
        issuer: String,
        externalId: String
    ): Customer {
        customerIdentityRepository.findByIssuerAndExternalId(issuer, externalId)?.let {
            return handleExistingIdentity(it, handle)
        }
        val customer = Customer(
            id = Generators.timeBasedEpochRandomGenerator().generate(),
            handle = handle,
            displayName = displayName,
            createdAt = LocalDateTime.now(),
        )
        return customerRepository.create(customer).also {
            customerIdentityRepository.create(
                CustomerIdentity(
                    customerId = it.id,
                    issuer = issuer,
                    externalId = externalId,
                    email = email,
                    createdAt = it.createdAt,
                )
            )
        }

    }

    private fun handleExistingIdentity(identity: CustomerIdentity, handle: String): Customer {
        val customer = customerRepository.findById(identity.customerId)
            ?: throw IllegalStateException("Customer identity exists but customer is missing")
        if (customer.handle != handle) {
            throw IllegalArgumentException("Customer already registered with handle ${customer.handle}")
        }
        return customer
    }
}
