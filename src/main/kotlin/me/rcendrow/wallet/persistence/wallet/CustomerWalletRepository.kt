package me.rcendrow.wallet.persistence.wallet

import me.rcendrow.jooq.generated.tables.Wallet.Companion.WALLET
import me.rcendrow.jooq.generated.tables.references.CUSTOMER_WALLET
import me.rcendrow.wallet.domain.wallet.WalletStatus
import me.rcendrow.wallet.domain.wallet.CustomerWallet
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Repository
class CustomerWalletRepository(private val db: DSLContext) {

    fun findAllByCustomerId(customerId: UUID): List<CustomerWallet> {
        return db.select(*(WALLET.fields() + CUSTOMER_WALLET.CUSTOMER_ID))
            .from(WALLET)
            .innerJoin(CUSTOMER_WALLET).on(CUSTOMER_WALLET.WALLET_ID.eq(WALLET.ID))
            .where(CUSTOMER_WALLET.CUSTOMER_ID.eq(customerId))
            .fetchInto(CustomerWallet::class.java)
    }

    fun findById(id: UUID): CustomerWallet? {
        return db.select(*(WALLET.fields() + CUSTOMER_WALLET.CUSTOMER_ID))
            .from(WALLET)
            .innerJoin(CUSTOMER_WALLET).on(CUSTOMER_WALLET.WALLET_ID.eq(WALLET.ID))
            .where(WALLET.ID.eq(id))
            .fetchOneInto(CustomerWallet::class.java)
    }

    fun lockWallet(id: UUID) {
        db.select(WALLET.ID)
            .from(WALLET)
            .where(WALLET.ID.eq(id))
            .forUpdate()
            .execute()
    }

    @Transactional
    fun create(wallet: CustomerWallet): CustomerWallet {
        db.insertInto(WALLET)
            .set(WALLET.ID, wallet.id)
            .set(WALLET.TYPE, wallet.type.name)
            .set(WALLET.STATUS, wallet.status.name)
            .set(WALLET.CREATED_AT, wallet.createdAt)
            .execute()
        db.insertInto(CUSTOMER_WALLET)
            .set(CUSTOMER_WALLET.WALLET_ID, wallet.id)
            .set(CUSTOMER_WALLET.CUSTOMER_ID, wallet.customerId)
            .execute()
        return wallet
    }

    fun updateStatus(wallet: CustomerWallet, status: WalletStatus): CustomerWallet {
        db.update(WALLET)
            .set(WALLET.STATUS, status.name)
            .where(WALLET.ID.eq(wallet.id))
            .and(WALLET.STATUS.ne(status.name)).execute()
        return wallet.copy(status = status)
    }
}
