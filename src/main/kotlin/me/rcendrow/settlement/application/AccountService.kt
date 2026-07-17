package me.rcendrow.settlement.application

import com.fasterxml.uuid.Generators
import me.rcendrow.settlement.application.exception.InvalidAccountStatusTransitionException
import me.rcendrow.settlement.application.exception.NotFoundException
import me.rcendrow.settlement.domain.Account
import me.rcendrow.settlement.domain.AccountStatus
import me.rcendrow.settlement.domain.Balance
import me.rcendrow.settlement.persistence.AccountRepository
import me.rcendrow.settlement.persistence.HoldRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*

@Service
class AccountService(
    private val accountRepository: AccountRepository,
    private val customerService: CustomerService,
    private val holdRepository: HoldRepository,
    private val accountBalanceService: AccountBalanceService,
) {
    @Transactional(readOnly = true)
    fun findAccountsByCustomer(customerId: UUID): List<Account> {
        customerService.getCustomer(customerId)
        return accountRepository.findAllByCustomerId(customerId)
    }

    @Transactional(readOnly = true)
    fun getAccount(id: UUID): Account = findById(id)

    @Transactional
    fun createAccount(customerId: UUID): Account {
        customerService.getCustomer(customerId)
        return Account(
            id = Generators.timeBasedEpochRandomGenerator().generate(),
            customerId = customerId,
            status = AccountStatus.ACTIVE,
            createdAt = LocalDateTime.now(),
        ).let { accountRepository.create(it) }
    }

    @Transactional(readOnly = true)
    fun getBalance(id: UUID): Balance {
        findById(id)
        val balance = accountBalanceService.findBalance(id)
        val activeHolds = holdRepository.sumActiveAmount(id)
        return Balance(
            accountId = id,
            balance = balance,
            availableBalance = balance.subtract(activeHolds),
        )
    }

    @Transactional
    fun updateAccountStatus(id: UUID, status: AccountStatus): Account {
        val account = findById(id)
        if (account.status == AccountStatus.CLOSED) {
            throw InvalidAccountStatusTransitionException(id, account.status, status)
        }
        return accountRepository.updateStatus(id, status)
    }

    fun lockBalance(accountId: UUID) {
        accountRepository.lockBalance(accountId)
    }

    private fun findById(id: UUID): Account = accountRepository.findById(id) ?: throw NotFoundException("Account", id)
}
