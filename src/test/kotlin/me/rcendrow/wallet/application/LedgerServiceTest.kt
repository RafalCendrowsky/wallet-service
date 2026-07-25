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
            toWallet = UUID.randomUUID(),
            amount = BigDecimal("50.00"),
            idempotencyKey = UUID.randomUUID().toString(),
            createdAt = LocalDateTime.now(),
        )
        every { ledgerEntryRepository.create(any()) } answers { firstArg() }

        val result = service.createDebitEntry(transfer)

        assertThat(result.walletId).isEqualTo(transfer.fromWallet)
        assertThat(result.transferId).isEqualTo(transfer.id)
        assertThat(result.amount).isEqualByComparingTo(transfer.amount.negate())
        verify { ledgerEntryRepository.create(result) }
    }

    @Test
    fun `should create CREDIT entry with toWallet`() {
        val transfer = Transfer(
            id = UUID.randomUUID(),
            fromWallet = UUID.randomUUID(),
            toWallet = UUID.randomUUID(),
            amount = BigDecimal("75.00"),
            idempotencyKey = UUID.randomUUID().toString(),
            createdAt = LocalDateTime.now(),
        )
        every { ledgerEntryRepository.create(any()) } answers { firstArg() }

        val result = service.createCreditEntry(transfer)

        assertThat(result.walletId).isEqualTo(transfer.toWallet)
        assertThat(result.transferId).isEqualTo(transfer.id)
        assertThat(result.amount).isEqualByComparingTo(transfer.amount)
        verify { ledgerEntryRepository.create(result) }
    }
}
