package me.rcendrow.wallet.application

import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import me.rcendrow.wallet.application.exception.AccountStatusException
import me.rcendrow.wallet.application.exception.InsufficientFundsException
import me.rcendrow.wallet.application.exception.NotFoundException
import me.rcendrow.wallet.domain.account.AccountBalance
import me.rcendrow.wallet.domain.account.AccountStatus
import me.rcendrow.wallet.domain.account.CustomerAccount
import me.rcendrow.wallet.persistence.account.AccountBalanceRepository
import me.rcendrow.wallet.persistence.account.CustomerAccountRepository
import me.rcendrow.wallet.persistence.account.ServiceAccountRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

class AccountServiceTest {

    private val customerAccountRepository: CustomerAccountRepository = mockk()
    private val serviceAccountRepository: ServiceAccountRepository = mockk()
    private val customerService: CustomerService = mockk()
    private val accountBalanceService: AccountBalanceService = mockk()
    private val accountBalanceRepository: AccountBalanceRepository = mockk()
    private val service =
        AccountService(
            customerService,
            accountBalanceService,
            customerAccountRepository,
            serviceAccountRepository,
            accountBalanceRepository
        )

    @AfterEach
    fun tearDown() {
        clearMocks(
            customerAccountRepository,
            serviceAccountRepository,
            customerService,
            accountBalanceService,
            accountBalanceRepository
        )
    }

    @Test
    fun `should create account`() {
        val customerId = UUID.randomUUID()
        every { customerService.getCustomer(customerId) } returns mockk()
        every { customerAccountRepository.create(any()) } answers { firstArg() }
        every { accountBalanceRepository.create(any()) } returns Unit

        val result = service.createCustomerAccount(customerId)

        assertThat(result.customerId).isEqualTo(customerId)
        assertThat(result.status).isEqualTo(AccountStatus.ACTIVE)
        assertThat(result.id).isNotNull
        assertThat(result.createdAt).isNotNull
        verify { customerAccountRepository.create(result) }
        verify { accountBalanceRepository.create(result.id) }
    }

    @Test
    fun `should return account by id`() {
        val id = UUID.randomUUID()
        val account = accountWithStatus(AccountStatus.ACTIVE)
        every { customerAccountRepository.findById(id) } returns account

        val result = service.getCustomerAccount(id)

        assertThat(result).isEqualTo(account)
    }

    @Test
    fun `should throw NotFoundException for unknown account`() {
        val id = UUID.randomUUID()
        every { customerAccountRepository.findById(id) } returns null

        assertThatThrownBy { service.getCustomerAccount(id) }
            .isInstanceOf(NotFoundException::class.java)
    }

    @Test
    fun `should return balance with available balance`() {
        val account = accountWithStatus(AccountStatus.ACTIVE)
        every { customerAccountRepository.findById(account.id) } returns account
        every { accountBalanceService.findBalance(account.id) } returns AccountBalance(
            accountId = account.id,
            balance = BigDecimal("100.00"),
            activeHolds = BigDecimal("30.00")
        )

        val result = service.getBalance(account.id)

        assertThat(result.balance).isEqualByComparingTo(BigDecimal("100.00"))
        assertThat(result.availableBalance).isEqualByComparingTo(BigDecimal("70.00"))
    }

    @Test
    fun `should suspend active account`() {
        val account = accountWithStatus(AccountStatus.ACTIVE)
        every { customerAccountRepository.findById(account.id) } returns account
        every {
            customerAccountRepository.updateStatus(
                account,
                AccountStatus.SUSPENDED
            )
        } returns account.copy(status = AccountStatus.SUSPENDED)

        val result = service.updateAccountStatus(account.id, AccountStatus.SUSPENDED)

        assertThat(result.status).isEqualTo(AccountStatus.SUSPENDED)
    }

    @Test
    fun `should allow updating status of non-closed account`() {
        val account = accountWithStatus(AccountStatus.SUSPENDED)
        every { customerAccountRepository.findById(account.id) } returns account
        every {
            customerAccountRepository.updateStatus(
                account,
                AccountStatus.SUSPENDED
            )
        } returns account.copy(status = AccountStatus.SUSPENDED)

        val result = service.updateAccountStatus(account.id, AccountStatus.SUSPENDED)

        assertThat(result.status).isEqualTo(AccountStatus.SUSPENDED)
    }

    @Test
    fun `should close active account`() {
        val account = accountWithStatus(AccountStatus.ACTIVE)
        every { customerAccountRepository.findById(account.id) } returns account
        every {
            customerAccountRepository.updateStatus(
                account,
                AccountStatus.CLOSED
            )
        } returns account.copy(status = AccountStatus.CLOSED)

        val result = service.updateAccountStatus(account.id, AccountStatus.CLOSED)

        assertThat(result.status).isEqualTo(AccountStatus.CLOSED)
    }

    @Test
    fun `should close suspended account`() {
        val account = accountWithStatus(AccountStatus.SUSPENDED)
        every { customerAccountRepository.findById(account.id) } returns account
        every {
            customerAccountRepository.updateStatus(
                account,
                AccountStatus.CLOSED
            )
        } returns account.copy(status = AccountStatus.CLOSED)

        val result = service.updateAccountStatus(account.id, AccountStatus.CLOSED)

        assertThat(result.status).isEqualTo(AccountStatus.CLOSED)
    }

    @Test
    fun `should not close already closed account`() {
        val account = accountWithStatus(AccountStatus.CLOSED)
        every { customerAccountRepository.findById(account.id) } returns account

        assertThatThrownBy { service.updateAccountStatus(account.id, AccountStatus.CLOSED) }
            .isInstanceOf(AccountStatusException::class.java)
    }

    @Test
    fun `should activate suspended account`() {
        val account = accountWithStatus(AccountStatus.SUSPENDED)
        every { customerAccountRepository.findById(account.id) } returns account
        every {
            customerAccountRepository.updateStatus(
                account,
                AccountStatus.ACTIVE
            )
        } returns account.copy(status = AccountStatus.ACTIVE)

        val result = service.updateAccountStatus(account.id, AccountStatus.ACTIVE)

        assertThat(result.status).isEqualTo(AccountStatus.ACTIVE)
    }

    @Test
    fun `should allow reactivating active account`() {
        val account = accountWithStatus(AccountStatus.ACTIVE)
        every { customerAccountRepository.findById(account.id) } returns account
        every {
            customerAccountRepository.updateStatus(
                account,
                AccountStatus.ACTIVE
            )
        } returns account.copy(status = AccountStatus.ACTIVE)

        val result = service.updateAccountStatus(account.id, AccountStatus.ACTIVE)

        assertThat(result.status).isEqualTo(AccountStatus.ACTIVE)
    }

    @Test
    fun `should pass verification when balance is sufficient`() {
        val account = accountWithStatus(AccountStatus.ACTIVE)
        every { customerAccountRepository.findById(account.id) } returns account
        every { customerAccountRepository.lockAccount(account.id) } returns Unit
        every { accountBalanceService.findBalance(account.id) } returns AccountBalance(
            accountId = account.id,
            balance = BigDecimal("100.00"),
            activeHolds = BigDecimal.ZERO
        )

        service.lockAndVerifyBalance(account, BigDecimal("50.00"))

        verify { customerAccountRepository.lockAccount(account.id) }
        verify { accountBalanceService.findBalance(account.id) }
    }

    @Test
    fun `should throw InsufficientFundsException when balance is insufficient`() {
        val account = accountWithStatus(AccountStatus.ACTIVE)
        every { customerAccountRepository.findById(account.id) } returns account
        every { customerAccountRepository.lockAccount(account.id) } returns Unit
        every { accountBalanceService.findBalance(account.id) } returns AccountBalance(
            accountId = account.id,
            balance = BigDecimal("30.00"),
            activeHolds = BigDecimal.ZERO
        )

        assertThatThrownBy {
            service.lockAndVerifyBalance(account, BigDecimal("50.00"))
        }.isInstanceOf(InsufficientFundsException::class.java)
    }

    fun accountWithStatus(status: AccountStatus): CustomerAccount {
        return CustomerAccount(
            id = UUID.randomUUID(),
            customerId = UUID.randomUUID(),
            status = status,
            createdAt = LocalDateTime.now()
        )
    }
}
