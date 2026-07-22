package me.rcendrow.settlement.application

import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import me.rcendrow.settlement.application.exception.AccountStatusException
import me.rcendrow.settlement.application.exception.NotFoundException
import me.rcendrow.settlement.domain.account.AccountBalance
import me.rcendrow.settlement.domain.account.AccountStatus
import me.rcendrow.settlement.domain.account.CustomerAccount
import me.rcendrow.settlement.persistence.CustomerAccountRepository
import me.rcendrow.settlement.persistence.ServiceAccountRepository
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
    private val service =
        AccountService(customerService, accountBalanceService, customerAccountRepository, serviceAccountRepository)

    @AfterEach
    fun tearDown() {
        clearMocks(
            customerAccountRepository,
            customerService,
            accountBalanceService
        )
    }

    @Test
    fun `should create account`() {
        val customerId = UUID.randomUUID()
        every { customerService.getCustomer(customerId) } returns mockk()
        every { customerAccountRepository.create(any()) } answers { firstArg() }

        val result = service.createAccount(customerId)

        assertThat(result.customerId).isEqualTo(customerId)
        assertThat(result.status).isEqualTo(AccountStatus.ACTIVE)
        assertThat(result.id).isNotNull
        assertThat(result.createdAt).isNotNull
        verify { customerAccountRepository.create(result) }
    }

    @Test
    fun `should return account by id`() {
        val id = UUID.randomUUID()
        val account =
            CustomerAccount(
                id = id,
                customerId = UUID.randomUUID(),
                status = AccountStatus.ACTIVE,
                createdAt = LocalDateTime.now()
            )
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
        val id = UUID.randomUUID()
        val account =
            CustomerAccount(
                id = id,
                customerId = UUID.randomUUID(),
                status = AccountStatus.ACTIVE,
                createdAt = LocalDateTime.now()
            )
        every { customerAccountRepository.findById(id) } returns account
        every { accountBalanceService.findBalance(id) } returns AccountBalance(
            accountId = id,
            balance = BigDecimal("100.00"),
            activeHolds = BigDecimal("30.00")
        )

        val result = service.getBalance(id)

        assertThat(result.balance).isEqualByComparingTo(BigDecimal("100.00"))
        assertThat(result.availableBalance).isEqualByComparingTo(BigDecimal("70.00"))
    }

    @Test
    fun `should suspend active account`() {
        val id = UUID.randomUUID()
        val account =
            CustomerAccount(
                id = id,
                customerId = UUID.randomUUID(),
                status = AccountStatus.ACTIVE,
                createdAt = LocalDateTime.now()
            )
        every { customerAccountRepository.findById(id) } returns account
        every {
            customerAccountRepository.updateStatus(
                account,
                AccountStatus.SUSPENDED
            )
        } returns account.copy(status = AccountStatus.SUSPENDED)

        val result = service.updateAccountStatus(id, AccountStatus.SUSPENDED)

        assertThat(result.status).isEqualTo(AccountStatus.SUSPENDED)
    }

    @Test
    fun `should allow updating status of non-closed account`() {
        val id = UUID.randomUUID()
        val account = CustomerAccount(
            id = id,
            customerId = UUID.randomUUID(),
            status = AccountStatus.SUSPENDED,
            createdAt = LocalDateTime.now()
        )
        every { customerAccountRepository.findById(id) } returns account
        every {
            customerAccountRepository.updateStatus(
                account,
                AccountStatus.SUSPENDED
            )
        } returns account.copy(status = AccountStatus.SUSPENDED)

        val result = service.updateAccountStatus(id, AccountStatus.SUSPENDED)

        assertThat(result.status).isEqualTo(AccountStatus.SUSPENDED)
    }

    @Test
    fun `should close active account`() {
        val id = UUID.randomUUID()
        val account =
            CustomerAccount(
                id = id,
                customerId = UUID.randomUUID(),
                status = AccountStatus.ACTIVE,
                createdAt = LocalDateTime.now()
            )
        every { customerAccountRepository.findById(id) } returns account
        every {
            customerAccountRepository.updateStatus(
                account,
                AccountStatus.CLOSED
            )
        } returns account.copy(status = AccountStatus.CLOSED)

        val result = service.updateAccountStatus(id, AccountStatus.CLOSED)

        assertThat(result.status).isEqualTo(AccountStatus.CLOSED)
    }

    @Test
    fun `should close suspended account`() {
        val id = UUID.randomUUID()
        val account = CustomerAccount(
            id = id,
            customerId = UUID.randomUUID(),
            status = AccountStatus.SUSPENDED,
            createdAt = LocalDateTime.now()
        )
        every { customerAccountRepository.findById(id) } returns account
        every {
            customerAccountRepository.updateStatus(
                account,
                AccountStatus.CLOSED
            )
        } returns account.copy(status = AccountStatus.CLOSED)

        val result = service.updateAccountStatus(id, AccountStatus.CLOSED)

        assertThat(result.status).isEqualTo(AccountStatus.CLOSED)
    }

    @Test
    fun `should not close already closed account`() {
        val id = UUID.randomUUID()
        val account =
            CustomerAccount(
                id = id,
                customerId = UUID.randomUUID(),
                status = AccountStatus.CLOSED,
                createdAt = LocalDateTime.now()
            )
        every { customerAccountRepository.findById(id) } returns account

        assertThatThrownBy { service.updateAccountStatus(id, AccountStatus.CLOSED) }
            .isInstanceOf(AccountStatusException::class.java)
    }

    @Test
    fun `should activate suspended account`() {
        val id = UUID.randomUUID()
        val account = CustomerAccount(
            id = id,
            customerId = UUID.randomUUID(),
            status = AccountStatus.SUSPENDED,
            createdAt = LocalDateTime.now()
        )
        every { customerAccountRepository.findById(id) } returns account
        every {
            customerAccountRepository.updateStatus(
                account,
                AccountStatus.ACTIVE
            )
        } returns account.copy(status = AccountStatus.ACTIVE)

        val result = service.updateAccountStatus(id, AccountStatus.ACTIVE)

        assertThat(result.status).isEqualTo(AccountStatus.ACTIVE)
    }

    @Test
    fun `should allow reactivating active account`() {
        val id = UUID.randomUUID()
        val account =
            CustomerAccount(
                id = id,
                customerId = UUID.randomUUID(),
                status = AccountStatus.ACTIVE,
                createdAt = LocalDateTime.now()
            )
        every { customerAccountRepository.findById(id) } returns account
        every {
            customerAccountRepository.updateStatus(
                account,
                AccountStatus.ACTIVE
            )
        } returns account.copy(status = AccountStatus.ACTIVE)

        val result = service.updateAccountStatus(id, AccountStatus.ACTIVE)

        assertThat(result.status).isEqualTo(AccountStatus.ACTIVE)
    }
}
