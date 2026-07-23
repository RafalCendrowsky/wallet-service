package me.rcendrow.settlement

import me.rcendrow.jooq.generated.tables.Account.Companion.ACCOUNT
import me.rcendrow.jooq.generated.tables.AccountBalance.Companion.ACCOUNT_BALANCE
import me.rcendrow.jooq.generated.tables.AccountBalanceQueue.Companion.ACCOUNT_BALANCE_QUEUE
import me.rcendrow.jooq.generated.tables.LedgerEntry.Companion.LEDGER_ENTRY
import me.rcendrow.jooq.generated.tables.ServiceAccount.Companion.SERVICE_ACCOUNT
import me.rcendrow.jooq.generated.tables.Transfer.Companion.TRANSFER
import me.rcendrow.settlement.application.*
import me.rcendrow.settlement.application.exception.InsufficientFundsException
import me.rcendrow.settlement.application.exception.NotFoundException
import me.rcendrow.settlement.domain.account.AccountStatus
import me.rcendrow.settlement.domain.account.ServiceAccountRole
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageRequest
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Import(TestcontainersConfiguration::class)
@SpringBootTest
class SettlementServiceBusinessLogicTests {

    @Autowired
    private lateinit var customerService: CustomerService

    @Autowired
    private lateinit var accountService: AccountService

    @Autowired
    private lateinit var transferService: TransferService

    @Autowired
    private lateinit var ledgerService: LedgerService

    @Autowired
    private lateinit var holdService: HoldService

    @Autowired
    private lateinit var accountBalanceService: AccountBalanceService

    @Autowired
    private lateinit var db: DSLContext

    @Autowired
    private lateinit var txTemplate: TransactionTemplate

    @BeforeEach
    fun ensureSystemAccountExists() {
        txTemplate.execute {
            val exists =
                db.fetchExists(SERVICE_ACCOUNT, SERVICE_ACCOUNT.ROLE.eq(ServiceAccountRole.EXTERNAL_SETTLEMENT.name))
            if (!exists) {
                val record = db.insertInto(ACCOUNT)
                    .set(ACCOUNT.ID, UUID.randomUUID())
                    .set(ACCOUNT.STATUS, "ACTIVE")
                    .set(ACCOUNT.TYPE, "SERVICE")
                    .set(ACCOUNT.CREATED_AT, LocalDateTime.now())
                    .returning()
                    .fetchSingle()
                db.insertInto(SERVICE_ACCOUNT)
                    .set(SERVICE_ACCOUNT.ACCOUNT_ID, record[ACCOUNT.ID])
                    .set(SERVICE_ACCOUNT.ROLE, ServiceAccountRole.EXTERNAL_SETTLEMENT.name)
                    .execute()
                db.insertInto(ACCOUNT_BALANCE)
                    .set(ACCOUNT_BALANCE.ACCOUNT_ID, record[ACCOUNT.ID])
                    .set(ACCOUNT_BALANCE.BALANCE, BigDecimal.ZERO)
                    .execute()
            }
        }
    }

    @Test
    fun `should create customer`() {
        val customer = customerService.createCustomer("create@test.com")

        assertThat(customer.id).isNotNull
        assertThat(customer.email).isEqualTo("create@test.com")
        assertThat(customer.createdAt).isNotNull
    }

    @Test
    fun `should throw exception on duplicate customer email`() {
        customerService.createCustomer("duplicate@test.com")
        assertThatThrownBy { customerService.createCustomer("duplicate@test.com") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("already exists")
    }

    @Test
    fun `should get customer by id`() {
        val created = customerService.createCustomer("bob@test.com")

        val customer = customerService.getCustomer(created.id)

        assertThat(customer.email).isEqualTo("bob@test.com")
    }

    @Test
    fun `should throw NotFoundException for unknown customer`() {
        assertThatThrownBy { customerService.getCustomer(UUID.randomUUID()) }
            .isInstanceOf(NotFoundException::class.java)
    }

    @Test
    fun `should create account`() {
        val customer = customerService.createCustomer("account-test@test.com")

        val account = accountService.createCustomerAccount(customer.id)

        assertThat(account.id).isNotNull
        assertThat(account.customerId).isEqualTo(customer.id)
        assertThat(account.status).isEqualTo(AccountStatus.ACTIVE)
    }

    @Test
    fun `should get account by id`() {
        val customer = customerService.createCustomer("get-account@test.com")
        val created = accountService.createCustomerAccount(customer.id)

        val account = accountService.getCustomerAccount(created.id)

        assertThat(account.customerId).isEqualTo(customer.id)
    }

    @Test
    fun `should throw NotFoundException for unknown account`() {
        assertThatThrownBy { accountService.getCustomerAccount(UUID.randomUUID()) }
            .isInstanceOf(NotFoundException::class.java)
    }

    @Test
    fun `should deposit money and update balance`() {
        val customer = customerService.createCustomer("deposit@test.com")
        val account = accountService.createCustomerAccount(customer.id)

        transferService.createDeposit(account.id, BigDecimal("100.00"), UUID.randomUUID().toString())

        val balance = accountService.getBalance(account.id)
        assertThat(balance.balance).isEqualByComparingTo(BigDecimal("100.00"))
    }

    @Test
    fun `should transfer money between accounts`() {
        val customer = customerService.createCustomer("transfer-test@test.com")
        val sender = accountService.createCustomerAccount(customer.id)
        val receiver = accountService.createCustomerAccount(customer.id)
        transferService.createDeposit(sender.id, BigDecimal("100.00"), UUID.randomUUID().toString())

        transferService.createTransfer(sender.id, receiver.id, BigDecimal("50.00"), UUID.randomUUID().toString())

        val senderBalance = accountService.getBalance(sender.id)
        assertThat(senderBalance.balance).isEqualByComparingTo(BigDecimal("50.00"))

        val receiverBalance = accountService.getBalance(receiver.id)
        assertThat(receiverBalance.balance).isEqualByComparingTo(BigDecimal("50.00"))

        val debitCount = db.fetchCount(
            LEDGER_ENTRY,
            LEDGER_ENTRY.ACCOUNT_ID.eq(sender.id).and(LEDGER_ENTRY.AMOUNT.lt(BigDecimal.ZERO))
        )
        val creditCount = db.fetchCount(
            LEDGER_ENTRY,
            LEDGER_ENTRY.ACCOUNT_ID.eq(receiver.id).and(LEDGER_ENTRY.AMOUNT.gt(BigDecimal.ZERO))
        )
        assertThat(debitCount).isEqualTo(1)
        assertThat(creditCount).isEqualTo(1)
    }

    @Test
    fun `should reject transfer with insufficient funds`() {
        val customer = customerService.createCustomer("insufficient@test.com")
        val sender = accountService.createCustomerAccount(customer.id)
        val receiver = accountService.createCustomerAccount(customer.id)

        assertThatThrownBy {
            transferService.createTransfer(sender.id, receiver.id, BigDecimal("1.00"), UUID.randomUUID().toString())
        }.isInstanceOf(InsufficientFundsException::class.java)
    }

    @Test
    fun `should reject duplicate idempotency key`() {
        val customer = customerService.createCustomer("dup-test@test.com")
        val sender = accountService.createCustomerAccount(customer.id)
        val receiver = accountService.createCustomerAccount(customer.id)
        transferService.createDeposit(sender.id, BigDecimal("100.00"), UUID.randomUUID().toString())
        val key = UUID.randomUUID().toString()

        val first = transferService.createTransfer(sender.id, receiver.id, BigDecimal("10.00"), key)
        val second = transferService.createTransfer(sender.id, receiver.id, BigDecimal("10.00"), key)

        assertThat(second.id).isEqualTo(first.id)

        val count = db.fetchCount(TRANSFER, TRANSFER.IDEMPOTENCY_KEY.eq(key))
        assertThat(count).isOne()
    }

    @Test
    fun `should rollback entire transfer on failure`() {
        val customer = customerService.createCustomer("rollback@test.com")
        val sender = accountService.createCustomerAccount(customer.id)
        val receiver = accountService.createCustomerAccount(customer.id)
        val key = UUID.randomUUID().toString()

        assertThatThrownBy {
            transferService.createTransfer(sender.id, receiver.id, BigDecimal("99999.00"), key)
        }

        val transferExists = db.fetchExists(TRANSFER, TRANSFER.IDEMPOTENCY_KEY.eq(key))
        val ledgerEntryExists = db.fetchExists(LEDGER_ENTRY, LEDGER_ENTRY.ACCOUNT_ID.eq(sender.id))
        assertThat(transferExists).isFalse()
        assertThat(ledgerEntryExists).isFalse()
    }

    @Test
    fun `should prevent double spending from concurrent requests`() {
        val customer = customerService.createCustomer("concurrent@test.com")
        val sender = accountService.createCustomerAccount(customer.id)
        val receiver1 = accountService.createCustomerAccount(customer.id)
        val receiver2 = accountService.createCustomerAccount(customer.id)
        transferService.createDeposit(sender.id, BigDecimal("100.00"), UUID.randomUUID().toString())

        val pool = Executors.newFixedThreadPool(2)
        val tasks = listOf(receiver1, receiver2).map { receiver ->
            Callable {
                try {
                    transferService.createTransfer(
                        sender.id,
                        receiver.id,
                        BigDecimal("80.00"),
                        UUID.randomUUID().toString()
                    )
                    "SUCCESS"
                } catch (_: InsufficientFundsException) {
                    "FAILED"
                }
            }
        }

        val futures = pool.invokeAll(tasks, 10, TimeUnit.SECONDS)
        val results = futures.map { it.get() }

        assertThat(results.count { it == "SUCCESS" }).isOne()
        assertThat(results.count { it == "FAILED" }).isOne()

        val senderBalance = accountService.getBalance(sender.id)
        assertThat(senderBalance.balance).isEqualByComparingTo(BigDecimal("20.00"))

        pool.shutdown()
    }

    @Test
    fun `should return correct balance after multiple transfers`() {
        val customer = customerService.createCustomer("balance-multi@test.com")
        val sender = accountService.createCustomerAccount(customer.id)
        val receiver = accountService.createCustomerAccount(customer.id)
        transferService.createDeposit(sender.id, BigDecimal("200.00"), UUID.randomUUID().toString())

        transferService.createTransfer(sender.id, receiver.id, BigDecimal("70.00"), UUID.randomUUID().toString())

        val senderBalance = accountService.getBalance(sender.id)
        assertThat(senderBalance.balance).isEqualByComparingTo(BigDecimal("130.00"))
    }

    @Test
    fun `should return paginated transaction history`() {
        val customer = customerService.createCustomer("pagination@test.com")
        val sender = accountService.createCustomerAccount(customer.id)
        val receiver = accountService.createCustomerAccount(customer.id)
        transferService.createDeposit(sender.id, BigDecimal("1000.00"), UUID.randomUUID().toString())

        val keys = (1..5).map { UUID.randomUUID().toString() }
        for (key in keys) {
            transferService.createTransfer(sender.id, receiver.id, BigDecimal("10.00"), key)
        }

        val page = transferService.getAccountTransfers(receiver.id, PageRequest.of(0, 3))
        assertThat(page.number).isZero()
        assertThat(page.size).isEqualTo(3)
        assertThat(page.totalElements).isEqualTo(5L)
    }

    @Test
    fun `should return zero balance for new account`() {
        val customer = customerService.createCustomer("zero-balance@test.com")
        val account = accountService.createCustomerAccount(customer.id)

        val balance = accountService.getBalance(account.id)
        assertThat(balance.balance).isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    fun `should place and release hold`() {
        val customer = customerService.createCustomer("hold-test@test.com")
        val account = accountService.createCustomerAccount(customer.id)
        transferService.createDeposit(account.id, BigDecimal("100.00"), UUID.randomUUID().toString())

        val hold = holdService.placeHold(account.id, BigDecimal("30.00"), LocalDateTime.now().plusDays(1))

        assertThat(hold.accountId).isEqualTo(account.id)
        assertThat(hold.amount).isEqualByComparingTo(BigDecimal("30.00"))
        assertThat(hold.status).isEqualTo(me.rcendrow.settlement.domain.HoldStatus.ACTIVE)

        val balance = accountService.getBalance(account.id)
        assertThat(balance.balance).isEqualByComparingTo(BigDecimal("100.00"))
        assertThat(balance.availableBalance).isEqualByComparingTo(BigDecimal("70.00"))

        holdService.releaseHold(hold.id)

        val releasedBalance = accountService.getBalance(account.id)
        assertThat(releasedBalance.availableBalance).isEqualByComparingTo(BigDecimal("100.00"))
    }

    @Test
    fun `should reject hold when insufficient available balance`() {
        val customer = customerService.createCustomer("hold-insufficient@test.com")
        val account = accountService.createCustomerAccount(customer.id)

        assertThatThrownBy {
            holdService.placeHold(account.id, BigDecimal("30.00"), LocalDateTime.now().plusDays(1))
        }.isInstanceOf(InsufficientFundsException::class.java)
    }

    @Test
    fun `should capture hold and create transfer`() {
        val customer = customerService.createCustomer("hold-capture@test.com")
        val sender = accountService.createCustomerAccount(customer.id)
        val receiver = accountService.createCustomerAccount(customer.id)
        transferService.createDeposit(sender.id, BigDecimal("100.00"), UUID.randomUUID().toString())

        val hold = holdService.placeHold(sender.id, BigDecimal("50.00"), LocalDateTime.now().plusDays(1))

        val transfer = holdService.captureHold(hold.id, receiver.id)
        assertThat(transfer.amount).isEqualByComparingTo(BigDecimal("50.00"))
        assertThat(transfer.fromAccount).isEqualTo(sender.id)
        assertThat(transfer.toAccount).isEqualTo(receiver.id)
    }

    @Test
    fun `should refresh a single account balance from queue`() {
        val customer = customerService.createCustomer("refresh-single@test.com")
        val account = accountService.createCustomerAccount(customer.id)
        transferService.createDeposit(account.id, BigDecimal("100.00"), UUID.randomUUID().toString())

        val lastEntryId = db.select(LEDGER_ENTRY.ID)
            .from(LEDGER_ENTRY)
            .where(LEDGER_ENTRY.ACCOUNT_ID.eq(account.id))
            .orderBy(LEDGER_ENTRY.ID.desc())
            .limit(1)
            .fetchSingleInto(UUID::class.java)

        accountBalanceService.refreshBalance()

        val ab = db.selectFrom(ACCOUNT_BALANCE)
            .where(ACCOUNT_BALANCE.ACCOUNT_ID.eq(account.id))
            .fetchSingle()
        assertThat(ab[ACCOUNT_BALANCE.BALANCE]).isEqualByComparingTo(BigDecimal("100.00"))
        assertThat(ab[ACCOUNT_BALANCE.LAST_ENTRY_ID]).isEqualTo(lastEntryId)

        val queueCount = db.fetchCount(ACCOUNT_BALANCE_QUEUE, ACCOUNT_BALANCE_QUEUE.ACCOUNT_ID.eq(account.id))
        assertThat(queueCount).isZero()

        val balance = accountService.getBalance(account.id)
        assertThat(balance.balance).isEqualByComparingTo(BigDecimal("100.00"))
    }

    @Test
    fun `should refresh balances incrementally`() {
        val customer = customerService.createCustomer("refresh-incremental@test.com")
        val account = accountService.createCustomerAccount(customer.id)
        transferService.createDeposit(account.id, BigDecimal("100.00"), UUID.randomUUID().toString())

        accountBalanceService.refreshBalance()

        var ab = db.selectFrom(ACCOUNT_BALANCE)
            .where(ACCOUNT_BALANCE.ACCOUNT_ID.eq(account.id))
            .fetchSingle()
        assertThat(ab[ACCOUNT_BALANCE.BALANCE]).isEqualByComparingTo(BigDecimal("100.00"))

        val firstLastEntryId = ab[ACCOUNT_BALANCE.LAST_ENTRY_ID]

        transferService.createDeposit(account.id, BigDecimal("50.00"), UUID.randomUUID().toString())

        val secondLastEntryId = db.select(LEDGER_ENTRY.ID)
            .from(LEDGER_ENTRY)
            .where(LEDGER_ENTRY.ACCOUNT_ID.eq(account.id))
            .orderBy(LEDGER_ENTRY.ID.desc())
            .limit(1)
            .fetchSingleInto(UUID::class.java)
        
        accountBalanceService.refreshBalance()

        ab = db.selectFrom(ACCOUNT_BALANCE)
            .where(ACCOUNT_BALANCE.ACCOUNT_ID.eq(account.id))
            .fetchSingle()
        assertThat(ab[ACCOUNT_BALANCE.BALANCE]).isEqualByComparingTo(BigDecimal("150.00"))
        assertThat(ab[ACCOUNT_BALANCE.LAST_ENTRY_ID]).isEqualTo(secondLastEntryId)
        assertThat(ab[ACCOUNT_BALANCE.LAST_ENTRY_ID]).isNotEqualTo(firstLastEntryId)

        val balance = accountService.getBalance(account.id)
        assertThat(balance.balance).isEqualByComparingTo(BigDecimal("150.00"))
    }

    @Test
    fun `should refresh multiple accounts in one batch`() {
        val customer = customerService.createCustomer("refresh-multi@test.com")
        val account1 = accountService.createCustomerAccount(customer.id)
        val account2 = accountService.createCustomerAccount(customer.id)
        transferService.createDeposit(account1.id, BigDecimal("100.00"), UUID.randomUUID().toString())
        transferService.createDeposit(account2.id, BigDecimal("200.00"), UUID.randomUUID().toString())

        accountBalanceService.refreshBalance()

        val ab1 = db.selectFrom(ACCOUNT_BALANCE)
            .where(ACCOUNT_BALANCE.ACCOUNT_ID.eq(account1.id))
            .fetchSingle()
        assertThat(ab1[ACCOUNT_BALANCE.BALANCE]).isEqualByComparingTo(BigDecimal("100.00"))

        val ab2 = db.selectFrom(ACCOUNT_BALANCE)
            .where(ACCOUNT_BALANCE.ACCOUNT_ID.eq(account2.id))
            .fetchSingle()
        assertThat(ab2[ACCOUNT_BALANCE.BALANCE]).isEqualByComparingTo(BigDecimal("200.00"))

        val queueCount = db.fetchCount(ACCOUNT_BALANCE_QUEUE)
        assertThat(queueCount).isZero()
    }

    @Test
    fun `should not crash when balance queue is empty`() {
        accountBalanceService.refreshBalance()
    }
}
