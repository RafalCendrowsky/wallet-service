package me.rcendrow.settlement.application

import com.fasterxml.uuid.Generators
import me.rcendrow.settlement.application.exception.AccountNotFoundException
import me.rcendrow.settlement.domain.Account
import me.rcendrow.settlement.persistence.AccountRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

@Service
class AccountService(
    private val accountRepository: AccountRepository,
    private val ledgerService: LedgerService,
) {

    @Transactional
    fun createAccount(owner: String): Account {
        return Account(
            id = Generators.timeBasedEpochRandomGenerator().generate(),
            owner = owner,
            createdAt = LocalDateTime.now(),
        ).let { accountRepository.create(it) }
    }

    @Transactional(readOnly = true)
    fun getAccount(id: UUID): Account = findById(id)

    @Transactional(readOnly = true)
    fun getBalance(id: UUID): BigDecimal {
        findById(id)
        return ledgerService.findBalance(id)
    }

    fun lockAccount(accountId: UUID) {
        findById(accountId)
        accountRepository.lock(accountId)
    }

    private fun findById(id: UUID): Account = accountRepository.findById(id) ?: throw AccountNotFoundException(id)
}
