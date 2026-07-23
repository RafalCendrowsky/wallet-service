package me.rcendrow.settlement.application

import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import me.rcendrow.settlement.domain.Transfer
import me.rcendrow.settlement.persistence.LedgerEntryRepository
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
    fun `should create DEBIT entry with fromAccount`() {
        val transfer = Transfer(
            id = UUID.randomUUID(),
            fromAccount = UUID.randomUUID(),
            toAccount = UUID.randomUUID(),
            amount = BigDecimal("50.00"),
            idempotencyKey = UUID.randomUUID().toString(),
            createdAt = LocalDateTime.now(),
        )
        every { ledgerEntryRepository.create(any()) } answers { firstArg() }

        val result = service.createDebitEntry(transfer)

        assertThat(result.accountId).isEqualTo(transfer.fromAccount)
        assertThat(result.transferId).isEqualTo(transfer.id)
        assertThat(result.amount).isEqualByComparingTo(transfer.amount.negate())
        verify { ledgerEntryRepository.create(result) }
    }

    @Test
    fun `should create CREDIT entry with toAccount`() {
        val transfer = Transfer(
            id = UUID.randomUUID(),
            fromAccount = UUID.randomUUID(),
            toAccount = UUID.randomUUID(),
            amount = BigDecimal("75.00"),
            idempotencyKey = UUID.randomUUID().toString(),
            createdAt = LocalDateTime.now(),
        )
        every { ledgerEntryRepository.create(any()) } answers { firstArg() }

        val result = service.createCreditEntry(transfer)

        assertThat(result.accountId).isEqualTo(transfer.toAccount)
        assertThat(result.transferId).isEqualTo(transfer.id)
        assertThat(result.amount).isEqualByComparingTo(transfer.amount)
        verify { ledgerEntryRepository.create(result) }
    }
}
