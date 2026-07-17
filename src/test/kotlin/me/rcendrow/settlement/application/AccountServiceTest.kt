package me.rcendrow.settlement.application

import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import me.rcendrow.settlement.application.exception.InvalidAccountStatusTransitionException
import me.rcendrow.settlement.application.exception.NotFoundException
import me.rcendrow.settlement.domain.Account
import me.rcendrow.settlement.domain.AccountStatus
import me.rcendrow.settlement.persistence.AccountRepository
import me.rcendrow.settlement.persistence.HoldRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

class AccountServiceTest {

    private val accountRepository: AccountRepository = mockk()
    private val ledgerService: LedgerService = mockk()
    private val customerService: CustomerService = mockk()
    private val transferService: TransferService = mockk()
    private val holdRepository: HoldRepository = mockk()
    private val accountBalanceService: AccountBalanceService = mockk()
    private val service = AccountService(
        accountRepository, customerService, holdRepository, accountBalanceService,
    )

    @AfterEach
    fun tearDown() {
        clearMocks(
            accountRepository,
            ledgerService,
            customerService,
            transferService,
            holdRepository,
            accountBalanceService
        )
    }

    @Test
    fun `should create account`() {
        val customerId = UUID.randomUUID()
        every { customerService.getCustomer(customerId) } returns mockk()
        every { accountRepository.create(any()) } answers { firstArg() }

        val result = service.createAccount(customerId)

        assertThat(result.customerId).isEqualTo(customerId)
        assertThat(result.status).isEqualTo(AccountStatus.ACTIVE)
        assertThat(result.id).isNotNull
        assertThat(result.createdAt).isNotNull
        verify { accountRepository.create(result) }
    }

    @Test
    fun `should return account by id`() {
        val id = UUID.randomUUID()
        val account =
            Account(
                id = id,
                customerId = UUID.randomUUID(),
                status = AccountStatus.ACTIVE,
                createdAt = LocalDateTime.now()
            )
        every { accountRepository.findById(id) } returns account

        val result = service.getAccount(id)

        assertThat(result).isEqualTo(account)
    }

    @Test
    fun `should throw NotFoundException for unknown account`() {
        val id = UUID.randomUUID()
        every { accountRepository.findById(id) } returns null

        assertThatThrownBy { service.getAccount(id) }
            .isInstanceOf(NotFoundException::class.java)
    }

    @Test
    fun `should return balance with available balance`() {
        val id = UUID.randomUUID()
        val account =
            Account(
                id = id,
                customerId = UUID.randomUUID(),
                status = AccountStatus.ACTIVE,
                createdAt = LocalDateTime.now()
            )
        every { accountRepository.findById(id) } returns account
        every { accountBalanceService.findBalance(id) } returns BigDecimal("100.00")
        every { holdRepository.sumActiveAmount(id) } returns BigDecimal("30.00")

        val result = service.getBalance(id)

        assertThat(result.balance).isEqualByComparingTo(BigDecimal("100.00"))
        assertThat(result.availableBalance).isEqualByComparingTo(BigDecimal("70.00"))
    }

    @Test
    fun `should suspend active account`() {
        val id = UUID.randomUUID()
        val account =
            Account(
                id = id,
                customerId = UUID.randomUUID(),
                status = AccountStatus.ACTIVE,
                createdAt = LocalDateTime.now()
            )
        every { accountRepository.findById(id) } returns account
        every { accountRepository.updateStatus(id, AccountStatus.SUSPENDED) } returns account

        val result = service.updateAccountStatus(id, AccountStatus.SUSPENDED)

        assertThat(result.status).isEqualTo(AccountStatus.SUSPENDED)
    }

    @Test
    fun `should not suspend non-active account`() {
        val id = UUID.randomUUID()
        val account = Account(
            id = id,
            customerId = UUID.randomUUID(),
            status = AccountStatus.SUSPENDED,
            createdAt = LocalDateTime.now()
        )
        every { accountRepository.findById(id) } returns account

        assertThatThrownBy { service.updateAccountStatus(id, AccountStatus.SUSPENDED) }
            .isInstanceOf(InvalidAccountStatusTransitionException::class.java)
    }

    @Test
    fun `should close active account`() {
        val id = UUID.randomUUID()
        val account =
            Account(
                id = id,
                customerId = UUID.randomUUID(),
                status = AccountStatus.ACTIVE,
                createdAt = LocalDateTime.now()
            )
        every { accountRepository.findById(id) } returns account
        every { accountRepository.updateStatus(id, AccountStatus.CLOSED) } returns account

        val result = service.updateAccountStatus(id, AccountStatus.CLOSED)

        assertThat(result.status).isEqualTo(AccountStatus.CLOSED)
    }

    @Test
    fun `should close suspended account`() {
        val id = UUID.randomUUID()
        val account = Account(
            id = id,
            customerId = UUID.randomUUID(),
            status = AccountStatus.SUSPENDED,
            createdAt = LocalDateTime.now()
        )
        every { accountRepository.findById(id) } returns account
        every { accountRepository.updateStatus(id, AccountStatus.CLOSED) } returns account

        val result = service.updateAccountStatus(id, AccountStatus.CLOSED)

        assertThat(result.status).isEqualTo(AccountStatus.CLOSED)
    }

    @Test
    fun `should not close already closed account`() {
        val id = UUID.randomUUID()
        val account =
            Account(
                id = id,
                customerId = UUID.randomUUID(),
                status = AccountStatus.CLOSED,
                createdAt = LocalDateTime.now()
            )
        every { accountRepository.findById(id) } returns account

        assertThatThrownBy { service.updateAccountStatus(id, AccountStatus.CLOSED) }
            .isInstanceOf(InvalidAccountStatusTransitionException::class.java)
    }

    @Test
    fun `should activate suspended account`() {
        val id = UUID.randomUUID()
        val account = Account(
            id = id,
            customerId = UUID.randomUUID(),
            status = AccountStatus.SUSPENDED,
            createdAt = LocalDateTime.now()
        )
        every { accountRepository.findById(id) } returns account
        every { accountRepository.updateStatus(id, AccountStatus.ACTIVE) } returns account

        val result = service.updateAccountStatus(id, AccountStatus.ACTIVE)

        assertThat(result.status).isEqualTo(AccountStatus.ACTIVE)
    }

    @Test
    fun `should not activate non-suspended account`() {
        val id = UUID.randomUUID()
        val account =
            Account(
                id = id,
                customerId = UUID.randomUUID(),
                status = AccountStatus.ACTIVE,
                createdAt = LocalDateTime.now()
            )
        every { accountRepository.findById(id) } returns account

        assertThatThrownBy { service.updateAccountStatus(id, AccountStatus.ACTIVE) }
            .isInstanceOf(InvalidAccountStatusTransitionException::class.java)
    }
}
