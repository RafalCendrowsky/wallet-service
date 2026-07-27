package me.rcendrow.wallet

import me.rcendrow.jooq.generated.tables.LedgerEntry.Companion.LEDGER_ENTRY
import me.rcendrow.jooq.generated.tables.ServiceWallet.Companion.SERVICE_WALLET
import me.rcendrow.jooq.generated.tables.Transfer.Companion.TRANSFER
import me.rcendrow.jooq.generated.tables.Wallet.Companion.WALLET
import me.rcendrow.jooq.generated.tables.WalletBalance.Companion.WALLET_BALANCE
import me.rcendrow.jooq.generated.tables.WalletBalanceQueue.Companion.WALLET_BALANCE_QUEUE
import me.rcendrow.jooq.generated.tables.references.CUSTOMER
import me.rcendrow.jooq.generated.tables.references.HOLD
import me.rcendrow.wallet.application.*
import me.rcendrow.wallet.application.exception.InsufficientFundsException
import me.rcendrow.wallet.application.exception.NotFoundException
import me.rcendrow.wallet.domain.Customer
import me.rcendrow.wallet.domain.Hold
import me.rcendrow.wallet.domain.HoldStatus
import me.rcendrow.wallet.domain.wallet.ServiceWalletRole
import me.rcendrow.wallet.domain.wallet.WalletStatus
import me.rcendrow.wallet.persistence.wallet.WalletBalanceQueueRepository
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
@SpringBootTest(properties = ["scheduler.wallet-balance.enabled=false"])
class WalletServiceBusinessLogicTests {

    @Autowired
    private lateinit var customerService: CustomerService

    @Autowired
    private lateinit var walletService: WalletService

    @Autowired
    private lateinit var transferService: TransferService

    @Autowired
    private lateinit var ledgerService: LedgerService

    @Autowired
    private lateinit var holdService: HoldService

    @Autowired
    private lateinit var walletBalanceService: WalletBalanceService

    @Autowired
    private lateinit var db: DSLContext

    @Autowired
    private lateinit var txTemplate: TransactionTemplate

    @Autowired
    private lateinit var walletBalanceQueueRepository: WalletBalanceQueueRepository

    private val testIssuer = "https://test-issuer.example.com"

    private fun createTestCustomer(handle: String, externalId: String = "ext-$handle"): Customer =
        customerService.createCustomer(handle, "email-$handle@test.com", testIssuer, externalId)

    @BeforeEach
    fun clearDatabase() {
        txTemplate.execute {
            db.truncate(WALLET, CUSTOMER).cascade().execute()
            val record = db.insertInto(WALLET)
                .set(WALLET.ID, UUID.randomUUID())
                .set(WALLET.STATUS, "ACTIVE")
                .set(WALLET.TYPE, "SERVICE")
                .set(WALLET.CREATED_AT, LocalDateTime.now())
                .returning()
                .fetchSingle()
            db.insertInto(SERVICE_WALLET)
                .set(SERVICE_WALLET.WALLET_ID, record[WALLET.ID])
                .set(SERVICE_WALLET.ROLE, ServiceWalletRole.EXTERNAL_SETTLEMENT.name)
                .execute()
            db.insertInto(WALLET_BALANCE)
                .set(WALLET_BALANCE.WALLET_ID, record[WALLET.ID])
                .set(WALLET_BALANCE.BALANCE, BigDecimal.ZERO)
                .execute()
        }
    }

    @Nested
    inner class CustomerTests {
        @Test
        fun `should create customer`() {
            val customer = createTestCustomer("create-customer")

            assertThat(customer.id).isNotNull
            assertThat(customer.handle).isEqualTo("create-customer")
            assertThat(customer.createdAt).isNotNull
        }

        @Test
        fun `should get customer by id`() {
            val created = createTestCustomer("get-customer")

            val customer = customerService.getCustomer(created.id)

            assertThat(customer.handle).isEqualTo("get-customer")
        }

        @Test
        fun `should throw NotFoundException for unknown customer`() {
            assertThatThrownBy { customerService.getCustomer(UUID.randomUUID()) }
                .isInstanceOf(NotFoundException::class.java)
        }

        @Test
        fun `should get customer by handle`() {
            val created = createTestCustomer("by-handle")

            val customer = customerService.getCustomerByHandle("by-handle")

            assertThat(customer.id).isEqualTo(created.id)
        }

        @Test
        fun `should reject duplicate handle`() {
            createTestCustomer("dup-handle")

            assertThatThrownBy { createTestCustomer("dup-handle", "other-id") }
                .isInstanceOf(IllegalArgumentException::class.java)
        }

        @Test
        fun `should return on multiple creates`() {
            val customer = createTestCustomer("same-handle", "external-id")
            val sameCustomer = createTestCustomer("same-handle", "external-id")

            assertThat(sameCustomer).isEqualTo(customer)
        }

        @Test
        fun `should throw on different handle for same identity`() {
            createTestCustomer("handle", "external-id")
            assertThatThrownBy { createTestCustomer("diff-handle", "external-id") }
                .isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    @Nested
    inner class WalletTests {
        @Test
        fun `should create wallet`() {
            val customer = createTestCustomer("wallet-create")

            val wallet = walletService.createCustomerWallet(customer.id)

            assertThat(wallet.id).isNotNull
            assertThat(wallet.customerId).isEqualTo(customer.id)
            assertThat(wallet.status).isEqualTo(WalletStatus.ACTIVE)
        }

        @Test
        fun `should get wallet by id`() {
            val customer = createTestCustomer("wallet-get")
            val created = walletService.createCustomerWallet(customer.id)

            val wallet = walletService.getCustomerWallet(customer.id, created.id)

            assertThat(wallet.customerId).isEqualTo(customer.id)
        }

        @Test
        fun `should throw NotFoundException for unknown wallet`() {
            val customer = createTestCustomer("wallet-unknown")

            assertThatThrownBy { walletService.getCustomerWallet(customer.id, UUID.randomUUID()) }
                .isInstanceOf(NotFoundException::class.java)
        }

        @Test
        fun `should return zero balance for new wallet`() {
            val customer = createTestCustomer("zero-balance")
            val wallet = walletService.createCustomerWallet(customer.id)

            val balance = walletService.getBalance(customer.id, wallet.id)
            assertThat(balance.balance).isEqualByComparingTo(BigDecimal.ZERO)
        }

        @Test
        fun `should refresh a single wallet balance from queue`() {
            val customer = createTestCustomer("refresh-single")
            val wallet = walletService.createCustomerWallet(customer.id)
            transferService.createDeposit(customer.id, wallet.id, BigDecimal("100.00"), UUID.randomUUID().toString())

            val lastEntryId = db.select(LEDGER_ENTRY.ID)
                .from(LEDGER_ENTRY)
                .where(LEDGER_ENTRY.WALLET_ID.eq(wallet.id))
                .orderBy(LEDGER_ENTRY.ID.desc())
                .limit(1)
                .fetchSingleInto(UUID::class.java)

            walletBalanceService.refreshBalance(2)

            val ab = db.selectFrom(WALLET_BALANCE)
                .where(WALLET_BALANCE.WALLET_ID.eq(wallet.id))
                .fetchSingle()
            assertThat(ab[WALLET_BALANCE.BALANCE]).isEqualByComparingTo(BigDecimal("100.00"))
            assertThat(ab[WALLET_BALANCE.LAST_ENTRY_ID]).isEqualTo(lastEntryId)

            val queueCount = db.fetchCount(WALLET_BALANCE_QUEUE, WALLET_BALANCE_QUEUE.WALLET_ID.eq(wallet.id))
            assertThat(queueCount).isZero()

            val balance = walletService.getBalance(customer.id, wallet.id)
            assertThat(balance.balance).isEqualByComparingTo(BigDecimal("100.00"))
        }

        @Test
        fun `should refresh balances incrementally`() {
            val customer = createTestCustomer("refresh-incr")
            val wallet = walletService.createCustomerWallet(customer.id)
            transferService.createDeposit(customer.id, wallet.id, BigDecimal("100.00"), UUID.randomUUID().toString())

            walletBalanceService.refreshBalance(2)

            var ab = db.selectFrom(WALLET_BALANCE)
                .where(WALLET_BALANCE.WALLET_ID.eq(wallet.id))
                .fetchSingle()
            assertThat(ab[WALLET_BALANCE.BALANCE]).isEqualByComparingTo(BigDecimal("100.00"))

            val firstLastEntryId = ab[WALLET_BALANCE.LAST_ENTRY_ID]

            transferService.createDeposit(customer.id, wallet.id, BigDecimal("50.00"), UUID.randomUUID().toString())

            val secondLastEntryId = db.select(LEDGER_ENTRY.ID)
                .from(LEDGER_ENTRY)
                .where(LEDGER_ENTRY.WALLET_ID.eq(wallet.id))
                .orderBy(LEDGER_ENTRY.ID.desc())
                .limit(1)
                .fetchSingleInto(UUID::class.java)

            walletBalanceService.refreshBalance(2)

            ab = db.selectFrom(WALLET_BALANCE)
                .where(WALLET_BALANCE.WALLET_ID.eq(wallet.id))
                .fetchSingle()
            assertThat(ab[WALLET_BALANCE.BALANCE]).isEqualByComparingTo(BigDecimal("150.00"))
            assertThat(ab[WALLET_BALANCE.LAST_ENTRY_ID]).isEqualTo(secondLastEntryId)
            assertThat(ab[WALLET_BALANCE.LAST_ENTRY_ID]).isNotEqualTo(firstLastEntryId)

            val balance = walletService.getBalance(customer.id, wallet.id)
            assertThat(balance.balance).isEqualByComparingTo(BigDecimal("150.00"))
        }

        @Test
        fun `should refresh multiple wallets in one batch`() {
            val customer1 = createTestCustomer("refresh-multi-1")
            val customer2 = createTestCustomer("refresh-multi-2")
            val wallet1 = walletService.createCustomerWallet(customer1.id)
            val wallet2 = walletService.createCustomerWallet(customer2.id)
            transferService.createDeposit(customer1.id, wallet1.id, BigDecimal("100.00"), UUID.randomUUID().toString())
            transferService.createDeposit(customer2.id, wallet2.id, BigDecimal("200.00"), UUID.randomUUID().toString())

            walletBalanceService.refreshBalance(4)

            val ab1 = db.selectFrom(WALLET_BALANCE)
                .where(WALLET_BALANCE.WALLET_ID.eq(wallet1.id))
                .fetchSingle()
            assertThat(ab1[WALLET_BALANCE.BALANCE]).isEqualByComparingTo(BigDecimal("100.00"))

            val ab2 = db.selectFrom(WALLET_BALANCE)
                .where(WALLET_BALANCE.WALLET_ID.eq(wallet2.id))
                .fetchSingle()
            assertThat(ab2[WALLET_BALANCE.BALANCE]).isEqualByComparingTo(BigDecimal("200.00"))

            val queueCount = db.fetchCount(WALLET_BALANCE_QUEUE)
            assertThat(queueCount).isZero()
        }

        @Test
        fun `should not create duplicate queue entries when marking same wallet multiple times`() {
            val customer = createTestCustomer("queue-dup")
            val wallet = walletService.createCustomerWallet(customer.id)

            walletBalanceService.markWalletForRefresh(wallet.id)
            walletBalanceService.markWalletForRefresh(wallet.id)
            walletBalanceService.markWalletForRefresh(wallet.id)

            val count = db.fetchCount(WALLET_BALANCE_QUEUE, WALLET_BALANCE_QUEUE.WALLET_ID.eq(wallet.id))
            assertThat(count).isOne()
        }

        @Test
        fun `should claim non-overlapping batches from concurrent threads`() {
            val walletIds = (1..20).map { i ->
                val customer = createTestCustomer("queue-concurrent-$i")
                val wallet = walletService.createCustomerWallet(customer.id)
                walletBalanceService.markWalletForRefresh(wallet.id)
                wallet.id
            }

            val numThreads = 4
            val batchSize = 5
            val barrier = CyclicBarrier(numThreads)
            val pool = Executors.newVirtualThreadPerTaskExecutor()
            val allClaimed = ConcurrentLinkedQueue<UUID>()

            val tasks = (1..numThreads).map {
                Callable {
                    barrier.await()
                    val claimed = walletBalanceQueueRepository.claimOldestBatch(batchSize)
                    allClaimed.addAll(claimed)
                    claimed.size
                }
            }

            val futures = pool.invokeAll(tasks, 30, TimeUnit.SECONDS)
            val batchSizes = futures.map { it.get() }

            assertThat(batchSizes.sum()).isEqualTo(20)
            assertThat(allClaimed).hasSize(20)
            assertThat(allClaimed).doesNotHaveDuplicates()
            assertThat(allClaimed).containsAll(walletIds)

            pool.shutdown()
        }

        @Test
        fun `should claim oldest entries first`() {
            val wallets = (1..3).map { i ->
                val customer = createTestCustomer("queue-order-$i")
                walletService.createCustomerWallet(customer.id)
            }

            wallets.forEach { walletBalanceService.markWalletForRefresh(it.id) }
            Thread.sleep(5)
            wallets.reversed().forEach { walletBalanceService.markWalletForRefresh(it.id) }

            val firstBatch = walletBalanceQueueRepository.claimOldestBatch(2)
            assertThat(firstBatch).containsExactly(wallets[0].id, wallets[1].id)

            val secondBatch = walletBalanceQueueRepository.claimOldestBatch(2)
            assertThat(secondBatch).containsExactly(wallets[2].id)
        }

        @Test
        fun `should not crash when balance queue is empty`() {
            walletBalanceService.refreshBalance(1)
        }
    }

    @Nested
    inner class TransferTests {
        @Test
        fun `should deposit money and update balance`() {
            val customer = createTestCustomer("deposit-test")
            val wallet = walletService.createCustomerWallet(customer.id)

            val transfer = transferService.createDeposit(
                customer.id,
                wallet.id,
                BigDecimal("100.00"),
                UUID.randomUUID().toString()
            )

            val balance = walletService.getBalance(customer.id, wallet.id)
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
            val customer = createTestCustomer("withdraw-test")
            val wallet = walletService.createCustomerWallet(customer.id)
            transferService.createDeposit(customer.id, wallet.id, BigDecimal("100.00"), UUID.randomUUID().toString())

            val transfer =
                transferService.createWithdrawal(
                    customer.id,
                    wallet.id,
                    BigDecimal("40.00"),
                    UUID.randomUUID().toString()
                )

            val balance = walletService.getBalance(customer.id, wallet.id)
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
        fun `should transfer money between customers`() {
            val senderCustomer = createTestCustomer("transfer-sender")
            val receiverCustomer = createTestCustomer("transfer-receiver")
            val sender = walletService.createCustomerWallet(senderCustomer.id)
            val receiver = walletService.createCustomerWallet(receiverCustomer.id)
            transferService.createDeposit(
                senderCustomer.id,
                sender.id,
                BigDecimal("100.00"),
                UUID.randomUUID().toString()
            )

            val transfer =
                transferService.createTransfer(
                    fromCustomerId = senderCustomer.id,
                    fromWallet = sender.id,
                    toCustomerHandle = receiverCustomer.handle,
                    amount = BigDecimal("50.00"),
                    idempotencyKey = UUID.randomUUID().toString()
                )

            val senderBalance = walletService.getBalance(senderCustomer.id, sender.id)
            assertThat(senderBalance.balance).isEqualByComparingTo(BigDecimal("50.00"))

            val receiverBalance = walletService.getBalance(receiverCustomer.id, receiver.id)
            assertThat(receiverBalance.balance).isEqualByComparingTo(BigDecimal("50.00"))

            val debitCount = db.fetchCount(
                LEDGER_ENTRY,
                LEDGER_ENTRY.WALLET_ID.eq(sender.id).and(LEDGER_ENTRY.AMOUNT.lt(BigDecimal.ZERO))
            )
            val creditCount = db.fetchCount(
                LEDGER_ENTRY,
                LEDGER_ENTRY.WALLET_ID.eq(receiver.id).and(LEDGER_ENTRY.AMOUNT.gt(BigDecimal.ZERO))
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
            val senderCustomer = createTestCustomer("self-transfer")
            val wallet = walletService.createCustomerWallet(senderCustomer.id)
            transferService.createDeposit(
                senderCustomer.id,
                wallet.id,
                BigDecimal("100.00"),
                UUID.randomUUID().toString()
            )

            assertThatThrownBy {
                transferService.createTransfer(
                    fromCustomerId = senderCustomer.id,
                    fromWallet = wallet.id,
                    toCustomerHandle = senderCustomer.handle,
                    amount = BigDecimal("50.00"),
                    idempotencyKey = UUID.randomUUID().toString()
                )
            }.isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("Self-transfer")

            val balance = walletService.getBalance(senderCustomer.id, wallet.id)
            assertThat(balance.balance).isEqualByComparingTo(BigDecimal("100.00"))
        }

        @Test
        fun `should reject transfer with insufficient funds`() {
            val senderCustomer = createTestCustomer("insufficient-sender")
            val receiverCustomer = createTestCustomer("insufficient-receiver")
            val sender = walletService.createCustomerWallet(senderCustomer.id)
            val receiver = walletService.createCustomerWallet(receiverCustomer.id)

            assertThatThrownBy {
                transferService.createTransfer(
                    fromCustomerId = senderCustomer.id,
                    fromWallet = sender.id,
                    toCustomerHandle = receiverCustomer.handle,
                    amount = BigDecimal("1.00"),
                    idempotencyKey = UUID.randomUUID().toString()
                )
            }.isInstanceOf(InsufficientFundsException::class.java)
        }

        @Test
        fun `should return existing transfer on duplicate idempotency key`() {
            val senderCustomer = createTestCustomer("dup-key-sender")
            val receiverCustomer = createTestCustomer("dup-key-receiver")
            val sender = walletService.createCustomerWallet(senderCustomer.id)
            val receiver = walletService.createCustomerWallet(receiverCustomer.id)
            transferService.createDeposit(
                senderCustomer.id,
                sender.id,
                BigDecimal("100.00"),
                UUID.randomUUID().toString()
            )
            val key = UUID.randomUUID().toString()

            val first = transferService.createTransfer(
                senderCustomer.id,
                sender.id,
                receiverCustomer.handle,
                BigDecimal("10.00"),
                key
            )
            val second = transferService.createTransfer(
                senderCustomer.id,
                sender.id,
                receiverCustomer.handle,
                BigDecimal("10.00"),
                key
            )

            assertThat(second.id).isEqualTo(first.id)

            val count = db.fetchCount(TRANSFER, TRANSFER.IDEMPOTENCY_KEY.eq(key))
            assertThat(count).isOne()
        }

        @Test
        fun `should rollback entire transfer on failure`() {
            val senderCustomer = createTestCustomer("rollback-sender")
            val receiverCustomer = createTestCustomer("rollback-receiver")
            val sender = walletService.createCustomerWallet(senderCustomer.id)
            val receiver = walletService.createCustomerWallet(receiverCustomer.id)
            val key = UUID.randomUUID().toString()

            assertThatThrownBy {
                transferService.createTransfer(
                    senderCustomer.id,
                    sender.id,
                    receiverCustomer.handle,
                    BigDecimal("99999.00"),
                    key
                )
            }

            val transferExists = db.fetchExists(TRANSFER, TRANSFER.IDEMPOTENCY_KEY.eq(key))
            val ledgerEntryExists = db.fetchExists(LEDGER_ENTRY, LEDGER_ENTRY.WALLET_ID.eq(sender.id))
            assertThat(transferExists).isFalse()
            assertThat(ledgerEntryExists).isFalse()
        }

        @Test
        fun `should prevent double spending from concurrent requests`() {
            val senderCustomer = createTestCustomer("concurrent-sender")
            val receiver1Customer = createTestCustomer("concurrent-recv-1")
            val receiver2Customer = createTestCustomer("concurrent-recv-2")
            val sender = walletService.createCustomerWallet(senderCustomer.id)
            val receiver1 = walletService.createCustomerWallet(receiver1Customer.id)
            val receiver2 = walletService.createCustomerWallet(receiver2Customer.id)
            transferService.createDeposit(
                senderCustomer.id,
                sender.id,
                BigDecimal("100.00"),
                UUID.randomUUID().toString()
            )

            val barrier = CyclicBarrier(2)
            val pool = Executors.newFixedThreadPool(2)
            val tasks = listOf(
                receiver1Customer.handle to receiver1.id,
                receiver2Customer.handle to receiver2.id,
            ).map { (handle, _) ->
                Callable {
                    try {
                        barrier.await()
                        transferService.createTransfer(
                            fromCustomerId = senderCustomer.id,
                            fromWallet = sender.id,
                            toCustomerHandle = handle,
                            amount = BigDecimal("80.00"),
                            idempotencyKey = UUID.randomUUID().toString()
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

            val senderBalance = walletService.getBalance(senderCustomer.id, sender.id)
            assertThat(senderBalance.balance).isEqualByComparingTo(BigDecimal("20.00"))

            pool.shutdown()
        }

        @Test
        fun `should prevent double spending with many concurrent transfers using virtual threads`() {
            val senderCustomer = createTestCustomer("mass-concurrent-sender")
            val sender = walletService.createCustomerWallet(senderCustomer.id)
            transferService.createDeposit(
                senderCustomer.id,
                sender.id,
                BigDecimal("1000.00"),
                UUID.randomUUID().toString()
            )

            val numThreads = 50
            val receivers = (1..numThreads).map {
                val c = createTestCustomer("mass-receiver-$it")
                walletService.createCustomerWallet(c.id)
                c
            }

            val barrier = CyclicBarrier(numThreads)
            val pool = Executors.newVirtualThreadPerTaskExecutor()
            val tasks = receivers.map { receiver ->
                Callable {
                    try {
                        barrier.await()
                        transferService.createTransfer(
                            fromCustomerId = senderCustomer.id,
                            fromWallet = sender.id,
                            toCustomerHandle = receiver.handle,
                            amount = BigDecimal("100.00"),
                            idempotencyKey = UUID.randomUUID().toString()
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

            val senderBalance = walletService.getBalance(senderCustomer.id, sender.id)
            assertThat(senderBalance.balance).isEqualByComparingTo(BigDecimal.ZERO)

            pool.shutdown()
        }

        @Test
        fun `should return correct balance after multiple transfers`() {
            val senderCustomer = createTestCustomer("balance-multi-sender")
            val receiverCustomer = createTestCustomer("balance-multi-receiver")
            val sender = walletService.createCustomerWallet(senderCustomer.id)
            val receiver = walletService.createCustomerWallet(receiverCustomer.id)
            transferService.createDeposit(
                senderCustomer.id,
                sender.id,
                BigDecimal("200.00"),
                UUID.randomUUID().toString()
            )

            transferService.createTransfer(
                senderCustomer.id,
                sender.id,
                receiverCustomer.handle,
                BigDecimal("70.00"),
                UUID.randomUUID().toString()
            )

            val senderBalance = walletService.getBalance(senderCustomer.id, sender.id)
            assertThat(senderBalance.balance).isEqualByComparingTo(BigDecimal("130.00"))
        }

        @Test
        fun `should handle amounts with extra decimal precision across multiple transfers`() {
            val senderCustomer = createTestCustomer("precision-sender")
            val receiverCustomer = createTestCustomer("precision-receiver")
            val sender = walletService.createCustomerWallet(senderCustomer.id)
            val receiver = walletService.createCustomerWallet(receiverCustomer.id)
            transferService.createDeposit(
                senderCustomer.id,
                sender.id,
                BigDecimal("10000.00"),
                UUID.randomUUID().toString()
            )

            transferService.createTransfer(
                senderCustomer.id,
                sender.id,
                receiverCustomer.handle,
                BigDecimal("10.999"),
                UUID.randomUUID().toString()
            )
            transferService.createTransfer(
                senderCustomer.id,
                sender.id,
                receiverCustomer.handle,
                BigDecimal("20.444"),
                UUID.randomUUID().toString()
            )
            transferService.createTransfer(
                senderCustomer.id,
                sender.id,
                receiverCustomer.handle,
                BigDecimal("30.555"),
                UUID.randomUUID().toString()
            )

            val senderBalance = walletService.getBalance(senderCustomer.id, sender.id)
            assertThat(senderBalance.balance).isEqualByComparingTo(BigDecimal("9938.00"))

            val receiverBalance = walletService.getBalance(receiverCustomer.id, receiver.id)
            assertThat(receiverBalance.balance).isEqualByComparingTo(BigDecimal("62.00"))
        }

        @Test
        fun `should return paginated transaction history`() {
            val senderCustomer = createTestCustomer("pagination-sender")
            val receiverCustomer = createTestCustomer("pagination-receiver")
            val sender = walletService.createCustomerWallet(senderCustomer.id)
            val receiver = walletService.createCustomerWallet(receiverCustomer.id)
            transferService.createDeposit(
                senderCustomer.id,
                sender.id,
                BigDecimal("1000.00"),
                UUID.randomUUID().toString()
            )

            val keys = (1..5).map { UUID.randomUUID().toString() }
            for (key in keys) {
                transferService.createTransfer(
                    senderCustomer.id,
                    sender.id,
                    receiverCustomer.handle,
                    BigDecimal("10.00"),
                    key
                )
            }

            val page = transferService.getWalletTransfers(receiverCustomer.id, receiver.id, PageRequest.of(0, 3))
            assertThat(page.number).isZero()
            assertThat(page.size).isEqualTo(3)
            assertThat(page.totalElements).isEqualTo(5L)
        }
    }

    @Nested
    inner class HoldTests {
        @Test
        fun `should place and release hold`() {
            val customer = createTestCustomer("hold-test")
            val wallet = walletService.createCustomerWallet(customer.id)
            transferService.createDeposit(customer.id, wallet.id, BigDecimal("100.00"), UUID.randomUUID().toString())

            val hold =
                holdService.placeHold(customer.id, wallet.id, BigDecimal("30.00"), LocalDateTime.now().plusDays(1))

            assertThat(hold.walletId).isEqualTo(wallet.id)
            assertThat(hold.amount).isEqualByComparingTo(BigDecimal("30.00"))
            assertThat(hold.status).isEqualTo(HoldStatus.ACTIVE)

            val balance = walletService.getBalance(customer.id, wallet.id)
            assertThat(balance.balance).isEqualByComparingTo(BigDecimal("100.00"))
            assertThat(balance.availableBalance).isEqualByComparingTo(BigDecimal("70.00"))

            holdService.releaseHold(hold.id)

            val releasedBalance = walletService.getBalance(customer.id, wallet.id)
            assertThat(releasedBalance.availableBalance).isEqualByComparingTo(BigDecimal("100.00"))
        }

        @Test
        fun `should reject hold when insufficient available balance`() {
            val customer = createTestCustomer("hold-insufficient")
            val wallet = walletService.createCustomerWallet(customer.id)

            assertThatThrownBy {
                holdService.placeHold(customer.id, wallet.id, BigDecimal("30.00"), LocalDateTime.now().plusDays(1))
            }.isInstanceOf(InsufficientFundsException::class.java)
        }

        @Test
        fun `should not change balance after failed hold`() {
            val customer = createTestCustomer("hold-fail")
            val wallet = walletService.createCustomerWallet(customer.id)
            transferService.createDeposit(customer.id, wallet.id, BigDecimal("100.00"), UUID.randomUUID().toString())

            assertThatThrownBy {
                holdService.placeHold(customer.id, wallet.id, BigDecimal("200.00"), LocalDateTime.now().plusDays(1))
            }.isInstanceOf(InsufficientFundsException::class.java)

            val balance = walletService.getBalance(customer.id, wallet.id)
            assertThat(balance.balance).isEqualByComparingTo(BigDecimal("100.00"))
            assertThat(balance.availableBalance).isEqualByComparingTo(BigDecimal("100.00"))
        }

        @Test
        fun `should capture hold and create transfer`() {
            val senderCustomer = createTestCustomer("hold-capture-sender")
            val receiverCustomer = createTestCustomer("hold-capture-receiver")
            val sender = walletService.createCustomerWallet(senderCustomer.id)
            val receiver = walletService.createCustomerWallet(receiverCustomer.id)
            transferService.createDeposit(
                senderCustomer.id,
                sender.id,
                BigDecimal("100.00"),
                UUID.randomUUID().toString()
            )

            val hold = holdService.placeHold(
                senderCustomer.id,
                sender.id,
                BigDecimal("50.00"),
                LocalDateTime.now().plusDays(1)
            )

            val transfer = holdService.captureHold(senderCustomer.id, hold.id, receiverCustomer.handle)
            assertThat(transfer.amount).isEqualByComparingTo(BigDecimal("50.00"))
            assertThat(transfer.fromWallet).isEqualTo(sender.id)
            assertThat(transfer.toWallet).isEqualTo(receiver.id)
        }

        @Test
        fun `should release expired holds`() {
            val senderCustomer = createTestCustomer("hold-capture-sender")
            val sender = walletService.createCustomerWallet(senderCustomer.id)
            transferService.createDeposit(
                senderCustomer.id,
                sender.id,
                BigDecimal("1000.00"),
                UUID.randomUUID().toString()
            )

            repeat(5) {
                holdService.placeHold(
                    senderCustomer.id,
                    sender.id,
                    BigDecimal("50.00"),

                    LocalDateTime.now().minusDays(1)
                )
            }

            repeat(5) {
                holdService.placeHold(
                    senderCustomer.id,
                    sender.id,
                    BigDecimal("50.00"),

                    LocalDateTime.now().plusDays(1)
                )
            }

            holdService.releaseExpiredHolds()

            val holds = db.selectFrom(HOLD).where(HOLD.WALLET_ID.eq(sender.id)).fetchInto(Hold::class.java)

            assertThat(holds.size).isEqualTo(10)
            assertThat(holds.count { it.status == HoldStatus.ACTIVE }).isEqualTo(5)
            assertThat(holds.count { it.status == HoldStatus.RELEASED }).isEqualTo(5)
            assertThat(holds.none { it.status != HoldStatus.RELEASED && it.expiresAt.isBefore(LocalDateTime.now()) })
        }

        @Test
        fun `should handle concurrent holds and transfers competing for same wallet`() {
            val holderCustomer = createTestCustomer("compete-holder")
            val wallet = walletService.createCustomerWallet(holderCustomer.id)
            transferService.createDeposit(
                holderCustomer.id,
                wallet.id,
                BigDecimal("1000.00"),
                UUID.randomUUID().toString()
            )

            val numThreads = 20
            val barrier = CyclicBarrier(numThreads)
            val pool = Executors.newVirtualThreadPerTaskExecutor()

            val transferReceivers = (1..numThreads / 2).map {
                val c = createTestCustomer("compete-receiver-$it")
                walletService.createCustomerWallet(c.id)
                c
            }

            val tasks = (0 until numThreads).map { i ->
                val receiverHandle = if (i % 2 == 1) transferReceivers[i / 2].handle else null
                Callable {
                    try {
                        barrier.await()
                        if (i % 2 == 0) {
                            holdService.placeHold(
                                holderCustomer.id,
                                wallet.id,
                                BigDecimal("100.00"),
                                LocalDateTime.now().plusDays(1)
                            )
                            "HOLD_SUCCESS"
                        } else {
                            transferService.createTransfer(
                                fromCustomerId = holderCustomer.id,
                                fromWallet = wallet.id,
                                toCustomerHandle = receiverHandle!!,
                                amount = BigDecimal("100.00"),
                                idempotencyKey = UUID.randomUUID().toString()
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

            val balance = walletService.getBalance(holderCustomer.id, wallet.id)
            assertThat(balance.availableBalance).isEqualByComparingTo(BigDecimal.ZERO)
            assertThat(balance.balance).isEqualByComparingTo(
                BigDecimal("1000").subtract(BigDecimal("100").multiply(BigDecimal.valueOf(transferSuccesses.toLong())))
            )

            pool.shutdown()
        }
    }
}
