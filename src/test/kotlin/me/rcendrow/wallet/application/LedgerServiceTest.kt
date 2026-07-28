package me.rcendrow.wallet.application

import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import me.rcendrow.wallet.domain.Transfer
import me.rcendrow.wallet.persistence.LedgerEntryRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

class LedgerServiceTest {

    private val ledgerEntryRepository: LedgerEntryRepository = mockk()
    private val service = LedgerService(ledgerEntryRepository)

    @AfterEach
    fun tearDown() {
        clearMocks(ledgerEntryRepository)
    }

    @Test
    fun `should create DEBIT entry with fromWallet`() {
        val transfer = Transfer(
            id = UUID.randomUUID(),
            fromWallet = UUID.randomUUID(),
            fromOwner = null,
            toWallet = UUID.randomUUID(),
            toOwner = null,
            amount = BigDecimal("50.00"),
            idempotencyKey = UUID.randomUUID().toString(),
            createdAt = LocalDateTime.now(),
        )
        every { ledgerEntryRepository.create(any()) } answers { firstArg() }

        val results = service.createEntries(transfer)

        assertThat(results.size).isEqualTo(2)
        assertThat(results.sumOf { it.amount }).isEqualByComparingTo(BigDecimal.ZERO)

        val debit = results.filter { it.amount < BigDecimal.ZERO }[0]

        assertThat(debit.walletId).isEqualTo(transfer.fromWallet)
        assertThat(debit.transferId).isEqualTo(transfer.id)
        assertThat(debit.amount).isEqualByComparingTo(transfer.amount.negate())
        verify { ledgerEntryRepository.create(debit) }
    }

    @Test
    fun `should create CREDIT entry with toWallet`() {
        val transfer = Transfer(
            id = UUID.randomUUID(),
            fromWallet = UUID.randomUUID(),
            fromOwner = null,
            toWallet = UUID.randomUUID(),
            toOwner = null,
            amount = BigDecimal("75.00"),
            idempotencyKey = UUID.randomUUID().toString(),
            createdAt = LocalDateTime.now(),
        )
        every { ledgerEntryRepository.create(any()) } answers { firstArg() }

        val results = service.createEntries(transfer)

        assertThat(results.size).isEqualTo(2)
        assertThat(results.sumOf { it.amount }).isEqualByComparingTo(BigDecimal.ZERO)

        val credit = results.filter { it.amount > BigDecimal.ZERO }[0]

        assertThat(credit.walletId).isEqualTo(transfer.toWallet)
        assertThat(credit.transferId).isEqualTo(transfer.id)
        assertThat(credit.amount).isEqualByComparingTo(transfer.amount)
        verify { ledgerEntryRepository.create(credit) }
    }
}
