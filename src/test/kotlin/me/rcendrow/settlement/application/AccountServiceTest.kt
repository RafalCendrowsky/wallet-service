package me.rcendrow.settlement.application

import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import me.rcendrow.settlement.application.exception.AccountNotFoundException
import me.rcendrow.settlement.domain.Account
import me.rcendrow.settlement.persistence.AccountRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*

class AccountServiceTest {

    private val accountRepository: AccountRepository = mockk()
    private val ledgerService: LedgerService = mockk()
    private val service = AccountService(accountRepository, ledgerService)

    @AfterEach
    fun tearDown() {
        clearMocks(accountRepository, ledgerService)
    }

    @Test
    fun `should create account`() {
        val owner = "Alice"
        every { accountRepository.create(any()) } answers { firstArg() }

        val result = service.createAccount(owner)

        assertThat(result.owner).isEqualTo(owner)
        assertThat(result.id).isNotNull
        assertThat(result.createdAt).isNotNull
        verify { accountRepository.create(result) }
    }

    @Test
    fun `should return account by id`() {
        val id = UUID.randomUUID()
        val account = Account(id = id, owner = "Bob", createdAt = LocalDateTime.now())
        every { accountRepository.findById(id) } returns account

        val result = service.getAccount(id)

        assertThat(result).isEqualTo(account)
    }

    @Test
    fun `should throw AccountNotFoundException for unknown account`() {
        val id = UUID.randomUUID()
        every { accountRepository.findById(id) } returns null

        assertThatThrownBy { service.getAccount(id) }
            .isInstanceOf(AccountNotFoundException::class.java)
    }

    @Test
    fun `should lock account`() {
        val id = UUID.randomUUID()
        val account = Account(id = id, owner = "Lock", createdAt = LocalDateTime.now())
        every { accountRepository.findById(id) } returns account
        every { accountRepository.lock(id) } returns Unit

        service.lockAccount(id)

        verify { accountRepository.lock(id) }
    }

    @Test
    fun `should throw AccountNotFoundException when locking non-existent account`() {
        val id = UUID.randomUUID()
        every { accountRepository.findById(id) } returns null

        assertThatThrownBy { service.lockAccount(id) }
            .isInstanceOf(AccountNotFoundException::class.java)
        verify(exactly = 0) { accountRepository.lock(any()) }
    }
}
