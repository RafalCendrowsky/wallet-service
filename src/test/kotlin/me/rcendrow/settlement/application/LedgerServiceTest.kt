package me.rcendrow.settlement.application

import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import me.rcendrow.settlement.domain.EntryType
import me.rcendrow.settlement.domain.Transfer
import me.rcendrow.settlement.persistence.LedgerRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

class LedgerServiceTest {

    private val ledgerRepository: LedgerRepository = mockk()
    private val service = LedgerService(ledgerRepository)

    @AfterEach
    fun tearDown() {
        clearMocks(ledgerRepository)
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
        every { ledgerRepository.create(any()) } answers { firstArg() }

        val result = service.createEntry(transfer, EntryType.DEBIT)

        assertThat(result.accountId).isEqualTo(transfer.fromAccount)
        assertThat(result.transferId).isEqualTo(transfer.id)
        assertThat(result.type).isEqualTo(EntryType.DEBIT)
        assertThat(result.amount).isEqualByComparingTo(transfer.amount)
        verify { ledgerRepository.create(result) }
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
        every { ledgerRepository.create(any()) } answers { firstArg() }

        val result = service.createEntry(transfer, EntryType.CREDIT)

        assertThat(result.accountId).isEqualTo(transfer.toAccount)
        assertThat(result.transferId).isEqualTo(transfer.id)
        assertThat(result.type).isEqualTo(EntryType.CREDIT)
        assertThat(result.amount).isEqualByComparingTo(transfer.amount)
        verify { ledgerRepository.create(result) }
    }
}
