package me.rcendrow.settlement.application

import com.fasterxml.uuid.Generators
import me.rcendrow.settlement.application.exception.NotFoundException
import me.rcendrow.settlement.domain.account.*
import me.rcendrow.settlement.persistence.CustomerAccountRepository
import me.rcendrow.settlement.persistence.ServiceAccountRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*

@Service
class AccountService(
    private val customerService: CustomerService,
    private val accountBalanceService: AccountBalanceService,
    private val customerAccountRepository: CustomerAccountRepository,
    private val serviceAccountRepository: ServiceAccountRepository
) {
    @Transactional(readOnly = true)
    fun findAccountsByCustomer(customerId: UUID): List<CustomerAccount> {
        customerService.getCustomer(customerId)
        return customerAccountRepository.findAllByCustomerId(customerId)
    }

    @Transactional(readOnly = true)
    fun getCustomerAccount(id: UUID): CustomerAccount = findCustomerAccountById(id)

    fun lockCustomerAccount(account: CustomerAccount) {
        customerAccountRepository.lockAccount(account.id)
    }

    @Transactional
    fun createAccount(customerId: UUID): CustomerAccount {
        customerService.getCustomer(customerId)
        return CustomerAccount(
            id = Generators.timeBasedEpochRandomGenerator().generate(),
            customerId = customerId,
            status = AccountStatus.ACTIVE,
            createdAt = LocalDateTime.now(),
        ).let { customerAccountRepository.create(it) }
    }

    @Transactional(readOnly = true)
    fun getBalance(id: UUID): AccountBalance {
        findCustomerAccountById(id)
        return accountBalanceService.findBalance(id)
    }

    @Transactional
    fun updateAccountStatus(id: UUID, status: AccountStatus): CustomerAccount {
        val account = findCustomerAccountById(id)
        account.verifyStatusNot(AccountStatus.CLOSED)
        return customerAccountRepository.updateStatus(account, status)
    }

    fun getServiceAccountByRole(role: ServiceAccountRole): ServiceAccount =
        serviceAccountRepository.findByRole(role)

    private fun findCustomerAccountById(id: UUID): CustomerAccount =
        customerAccountRepository.findById(id) ?: throw NotFoundException("CustomerAccount", id)
}
