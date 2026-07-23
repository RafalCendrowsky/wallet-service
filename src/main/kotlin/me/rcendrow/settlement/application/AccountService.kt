package me.rcendrow.settlement.application

import com.fasterxml.uuid.Generators
import me.rcendrow.settlement.application.exception.InsufficientFundsException
import me.rcendrow.settlement.application.exception.NotFoundException
import me.rcendrow.settlement.domain.account.*
import me.rcendrow.settlement.persistence.account.AccountBalanceRepository
import me.rcendrow.settlement.persistence.account.CustomerAccountRepository
import me.rcendrow.settlement.persistence.account.ServiceAccountRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

@Service
class AccountService(
    private val customerService: CustomerService,
    private val accountBalanceService: AccountBalanceService,
    private val customerAccountRepository: CustomerAccountRepository,
    private val serviceAccountRepository: ServiceAccountRepository,
    private val accountBalanceRepository: AccountBalanceRepository,
) {
    @Transactional(readOnly = true)
    fun findAccountsByCustomer(customerId: UUID): List<CustomerAccount> {
        customerService.getCustomer(customerId)
        return customerAccountRepository.findAllByCustomerId(customerId)
    }

    @Transactional(readOnly = true)
    fun getCustomerAccount(id: UUID): CustomerAccount = findCustomerAccountById(id)

    @Transactional
    fun createCustomerAccount(customerId: UUID): CustomerAccount {
        customerService.getCustomer(customerId)
        return CustomerAccount(
            id = Generators.timeBasedEpochRandomGenerator().generate(),
            customerId = customerId,
            status = AccountStatus.ACTIVE,
            createdAt = LocalDateTime.now(),
        ).let {
            customerAccountRepository.create(it)
            accountBalanceRepository.create(it.id)
            it
        }
    }

    @Transactional
    fun lockAndVerifyBalance(account: CustomerAccount, amount: BigDecimal) {
        customerAccountRepository.lockAccount(account.id)
        val balance = accountBalanceService.findBalance(account.id)
        if (balance.availableBalance < amount) {
            throw InsufficientFundsException(account.id, balance.availableBalance, amount)
        }
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
