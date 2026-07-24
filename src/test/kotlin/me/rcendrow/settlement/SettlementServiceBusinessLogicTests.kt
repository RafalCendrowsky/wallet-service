package me.rcendrow.settlement

import me.rcendrow.jooq.generated.tables.Account.Companion.ACCOUNT
import me.rcendrow.jooq.generated.tables.AccountBalance.Companion.ACCOUNT_BALANCE
import me.rcendrow.jooq.generated.tables.AccountBalanceQueue.Companion.ACCOUNT_BALANCE_QUEUE
import me.rcendrow.jooq.generated.tables.LedgerEntry.Companion.LEDGER_ENTRY
import me.rcendrow.jooq.generated.tables.ServiceAccount.Companion.SERVICE_ACCOUNT
import me.rcendrow.jooq.generated.tables.Transfer.Companion.TRANSFER
import me.rcendrow.jooq.generated.tables.references.CUSTOMER
import me.rcendrow.settlement.application.*
import me.rcendrow.settlement.application.exception.InsufficientFundsException
import me.rcendrow.settlement.application.exception.NotFoundException
import me.rcendrow.settlement.domain.account.AccountStatus
import me.rcendrow.settlement.domain.account.ServiceAccountRole
import me.rcendrow.settlement.persistence.account.AccountBalanceQueueRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageRequest
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.*

@Import(TestcontainersConfiguration::class)
@SpringBootTest(properties = ["scheduler.account-balance.enabled=false"])
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

    @Autowired
    private lateinit var accountBalanceQueueRepository: AccountBalanceQueueRepository

    @BeforeEach
    fun clearDatabase() {
        txTemplate.execute {
            db.truncate(ACCOUNT, CUSTOMER).cascade().execute()
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

    @Nested
    inner class CustomerTests {
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
    }

    @Nested
    inner class AccountTests {
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
        fun `should return zero balance for new account`() {
            val customer = customerService.createCustomer("zero-balance@test.com")
            val account = accountService.createCustomerAccount(customer.id)

            val balance = accountService.getBalance(account.id)
            assertThat(balance.balance).isEqualByComparingTo(BigDecimal.ZERO)
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

            accountBalanceService.refreshBalance(2)

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

            accountBalanceService.refreshBalance(2)

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

            accountBalanceService.refreshBalance(2)

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

            accountBalanceService.refreshBalance(4)

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
        fun `should not create duplicate queue entries when marking same account multiple times`() {
            val customer = customerService.createCustomer("queue-dup@test.com")
            val account = accountService.createCustomerAccount(customer.id)

            accountBalanceService.markAccountForRefresh(account.id)
            accountBalanceService.markAccountForRefresh(account.id)
            accountBalanceService.markAccountForRefresh(account.id)

            val count = db.fetchCount(ACCOUNT_BALANCE_QUEUE, ACCOUNT_BALANCE_QUEUE.ACCOUNT_ID.eq(account.id))
            assertThat(count).isOne()
        }

        @Test
        fun `should claim non-overlapping batches from concurrent threads`() {
            val customer = customerService.createCustomer("queue-concurrent@test.com")
            val accountIds = (1..20).map {
                val account = accountService.createCustomerAccount(customer.id)
                accountBalanceService.markAccountForRefresh(account.id)
                account.id
            }

            val numThreads = 4
            val batchSize = 5
            val barrier = CyclicBarrier(numThreads)
            val pool = Executors.newVirtualThreadPerTaskExecutor()
            val allClaimed = ConcurrentLinkedQueue<UUID>()

            val tasks = (1..numThreads).map {
                Callable {
                    barrier.await()
                    val claimed = accountBalanceQueueRepository.claimOldestBatch(batchSize)
                    allClaimed.addAll(claimed)
                    claimed.size
                }
            }

            val futures = pool.invokeAll(tasks, 30, TimeUnit.SECONDS)
            val batchSizes = futures.map { it.get() }

            assertThat(batchSizes.sum()).isEqualTo(20)
            assertThat(allClaimed).hasSize(20)
            assertThat(allClaimed).doesNotHaveDuplicates()
            assertThat(allClaimed).containsAll(accountIds)

            pool.shutdown()
        }

        @Test
        fun `should claim oldest entries first`() {
            val customer = customerService.createCustomer("queue-order@test.com")
            val accounts = (1..3).map {
                accountService.createCustomerAccount(customer.id)
            }

            accounts.forEach { accountBalanceService.markAccountForRefresh(it.id) }
            Thread.sleep(5)
            accounts.reversed().forEach { accountBalanceService.markAccountForRefresh(it.id) }

            val firstBatch = accountBalanceQueueRepository.claimOldestBatch(2)
            assertThat(firstBatch).containsExactly(accounts[0].id, accounts[1].id)

            val secondBatch = accountBalanceQueueRepository.claimOldestBatch(2)
            assertThat(secondBatch).containsExactly(accounts[2].id)
        }

        @Test
        fun `should not crash when balance queue is empty`() {
            accountBalanceService.refreshBalance(1)
        }
    }

    @Nested
    inner class TransferTests {
        @Test
        fun `should deposit money and update balance`() {
            val customer = customerService.createCustomer("deposit@test.com")
            val account = accountService.createCustomerAccount(customer.id)

            val transfer = transferService.createDeposit(account.id, BigDecimal("100.00"), UUID.randomUUID().toString())

            val balance = accountService.getBalance(account.id)
            assertThat(balance.balance).isEqualByComparingTo(BigDecimal("100.00"))

            val debitCount = db.fetchCount(
                LEDGER_ENTRY,
                LEDGER_ENTRY.TRANSFER_ID.eq(transfer.id).and(LEDGER_ENTRY.AMOUNT.lt(BigDecimal.ZERO))
            )
            val creditCount = db.fetchCount(
                LEDGER_ENTRY,
                LEDGER_ENTRY.TRANSFER_ID.eq(transfer.id).and(LEDGER_ENTRY.AMOUNT.gt(BigDecimal.ZERO))
            )
            val entriesSum = db.select(DSL.sum(LEDGER_ENTRY.AMOUNT)).from(LEDGER_ENTRY).where(
                LEDGER_ENTRY.TRANSFER_ID.eq(transfer.id)
            ).fetchOne(0, BigDecimal::class.java)

            assertThat(debitCount).isEqualTo(1)
            assertThat(creditCount).isEqualTo(1)
            assertThat(entriesSum).isEqualByComparingTo(BigDecimal.ZERO)
        }

        @Test
        fun `should withdraw money and update balance`() {
            val customer = customerService.createCustomer("withdrawal@test.com")
            val account = accountService.createCustomerAccount(customer.id)
            transferService.createDeposit(account.id, BigDecimal("100.00"), UUID.randomUUID().toString())

            val transfer =
                transferService.createWithdrawal(account.id, BigDecimal("40.00"), UUID.randomUUID().toString())

            val balance = accountService.getBalance(account.id)
            assertThat(balance.balance).isEqualByComparingTo(BigDecimal("60.00"))

            val debitCount = db.fetchCount(
                LEDGER_ENTRY,
                LEDGER_ENTRY.TRANSFER_ID.eq(transfer.id).and(LEDGER_ENTRY.AMOUNT.lt(BigDecimal.ZERO))
            )
            val creditCount = db.fetchCount(
                LEDGER_ENTRY,
                LEDGER_ENTRY.TRANSFER_ID.eq(transfer.id).and(LEDGER_ENTRY.AMOUNT.gt(BigDecimal.ZERO))
            )
            val entriesSum = db.select(DSL.sum(LEDGER_ENTRY.AMOUNT)).from(LEDGER_ENTRY).where(
                LEDGER_ENTRY.TRANSFER_ID.eq(transfer.id)
            ).fetchOne(0, BigDecimal::class.java)


            assertThat(debitCount).isEqualTo(1)
            assertThat(creditCount).isEqualTo(1)
            assertThat(entriesSum).isEqualByComparingTo(BigDecimal.ZERO)
        }

        @Test
        fun `should transfer money between accounts`() {
            val customer = customerService.createCustomer("transfer-test@test.com")
            val sender = accountService.createCustomerAccount(customer.id)
            val receiver = accountService.createCustomerAccount(customer.id)
            transferService.createDeposit(sender.id, BigDecimal("100.00"), UUID.randomUUID().toString())

            val transfer =
                transferService.createTransfer(
                    sender.id,
                    receiver.id,
                    BigDecimal("50.00"),
                    UUID.randomUUID().toString()
                )

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
            val entriesSum = db.select(DSL.sum(LEDGER_ENTRY.AMOUNT)).from(LEDGER_ENTRY).where(
                LEDGER_ENTRY.TRANSFER_ID.eq(transfer.id)
            ).fetchOne(0, BigDecimal::class.java)

            assertThat(debitCount).isEqualTo(1)
            assertThat(creditCount).isEqualTo(1)
            assertThat(entriesSum).isEqualByComparingTo(BigDecimal.ZERO)
        }

        @Test
        fun `should reject self-transfer`() {
            val customer = customerService.createCustomer("self-transfer@test.com")
            val account = accountService.createCustomerAccount(customer.id)
            transferService.createDeposit(account.id, BigDecimal("100.00"), UUID.randomUUID().toString())

            assertThatThrownBy {
                transferService.createTransfer(
                    account.id,
                    account.id,
                    BigDecimal("50.00"),
                    UUID.randomUUID().toString()
                )
            }.isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("Self-transfer")

            val balance = accountService.getBalance(account.id)
            assertThat(balance.balance).isEqualByComparingTo(BigDecimal("100.00"))
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
        fun `should return existing transfer on duplicate idempotency key`() {
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

            val barrier = CyclicBarrier(2)
            val pool = Executors.newFixedThreadPool(2)
            val tasks = listOf(receiver1, receiver2).map { receiver ->
                Callable {
                    try {
                        barrier.await()
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
        fun `should prevent double spending with many concurrent transfers using virtual threads`() {
            val customer = customerService.createCustomer("mass-concurrent@test.com")
            val sender = accountService.createCustomerAccount(customer.id)
            transferService.createDeposit(sender.id, BigDecimal("1000.00"), UUID.randomUUID().toString())

            val numThreads = 50
            val receivers = (1..numThreads).map {
                accountService.createCustomerAccount(customer.id)
            }

            val barrier = CyclicBarrier(numThreads)
            val pool = Executors.newVirtualThreadPerTaskExecutor()
            val tasks = receivers.map { receiver ->
                Callable {
                    try {
                        barrier.await()
                        transferService.createTransfer(
                            sender.id,
                            receiver.id,
                            BigDecimal("100.00"),
                            UUID.randomUUID().toString()
                        )
                        "SUCCESS"
                    } catch (_: InsufficientFundsException) {
                        "FAILED"
                    }
                }
            }

            val futures = pool.invokeAll(tasks, 30, TimeUnit.SECONDS)
            val results = futures.map { it.get() }

            assertThat(results.count { it == "SUCCESS" }).isEqualTo(10)
            assertThat(results.count { it == "FAILED" }).isEqualTo(numThreads - 10)

            val senderBalance = accountService.getBalance(sender.id)
            assertThat(senderBalance.balance).isEqualByComparingTo(BigDecimal.ZERO)

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
        fun `should handle amounts with extra decimal precision across multiple transfers`() {
            val customer = customerService.createCustomer("precision@test.com")
            val sender = accountService.createCustomerAccount(customer.id)
            val receiver = accountService.createCustomerAccount(customer.id)
            transferService.createDeposit(sender.id, BigDecimal("10000.00"), UUID.randomUUID().toString())

            transferService.createTransfer(sender.id, receiver.id, BigDecimal("10.999"), UUID.randomUUID().toString())
            transferService.createTransfer(sender.id, receiver.id, BigDecimal("20.444"), UUID.randomUUID().toString())
            transferService.createTransfer(sender.id, receiver.id, BigDecimal("30.555"), UUID.randomUUID().toString())

            val senderBalance = accountService.getBalance(sender.id)
            assertThat(senderBalance.balance).isEqualByComparingTo(BigDecimal("9938.00"))

            val receiverBalance = accountService.getBalance(receiver.id)
            assertThat(receiverBalance.balance).isEqualByComparingTo(BigDecimal("62.00"))
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
    }

    @Nested
    inner class HoldTests {
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
        fun `should not change balance after failed hold`() {
            val customer = customerService.createCustomer("hold-fail@test.com")
            val account = accountService.createCustomerAccount(customer.id)
            transferService.createDeposit(account.id, BigDecimal("100.00"), UUID.randomUUID().toString())

            assertThatThrownBy {
                holdService.placeHold(account.id, BigDecimal("200.00"), LocalDateTime.now().plusDays(1))
            }.isInstanceOf(InsufficientFundsException::class.java)

            val balance = accountService.getBalance(account.id)
            assertThat(balance.balance).isEqualByComparingTo(BigDecimal("100.00"))
            assertThat(balance.availableBalance).isEqualByComparingTo(BigDecimal("100.00"))
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
        fun `should handle concurrent holds and transfers competing for same account`() {
            val customer = customerService.createCustomer("compete@test.com")
            val account = accountService.createCustomerAccount(customer.id)
            transferService.createDeposit(account.id, BigDecimal("1000.00"), UUID.randomUUID().toString())

            val numThreads = 20
            val barrier = CyclicBarrier(numThreads)
            val pool = Executors.newVirtualThreadPerTaskExecutor()

            val transferReceivers = (1..numThreads / 2).map {
                accountService.createCustomerAccount(customer.id)
            }

            val tasks = (0 until numThreads).map { i ->
                val receiverId = if (i % 2 == 1) transferReceivers[i / 2].id else null
                Callable {
                    try {
                        barrier.await()
                        if (i % 2 == 0) {
                            holdService.placeHold(account.id, BigDecimal("100.00"), LocalDateTime.now().plusDays(1))
                            "HOLD_SUCCESS"
                        } else {
                            transferService.createTransfer(
                                account.id,
                                receiverId!!,
                                BigDecimal("100.00"),
                                UUID.randomUUID().toString()
                            )
                            "TRANSFER_SUCCESS"
                        }
                    } catch (_: InsufficientFundsException) {
                        if (i % 2 == 0) "HOLD_FAILED" else "TRANSFER_FAILED"
                    }
                }
            }

            val futures = pool.invokeAll(tasks, 30, TimeUnit.SECONDS)
            val results = futures.map { it.get() }

            val holdSuccesses = results.count { it == "HOLD_SUCCESS" }
            val transferSuccesses = results.count { it == "TRANSFER_SUCCESS" }
            val totalSuccesses = holdSuccesses + transferSuccesses
            val totalFailures = results.count { it.endsWith("FAILED") }

            assertThat(totalSuccesses).isEqualTo(10)
            assertThat(totalFailures).isEqualTo(10)

            val balance = accountService.getBalance(account.id)
            assertThat(balance.availableBalance).isEqualByComparingTo(BigDecimal.ZERO)
            assertThat(balance.balance).isEqualByComparingTo(
                BigDecimal("1000").subtract(BigDecimal("100").multiply(BigDecimal.valueOf(transferSuccesses.toLong())))
            )

            pool.shutdown()
        }
    }
}
