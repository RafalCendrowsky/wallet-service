package me.rcendrow.wallet.persistence.wallet

import me.rcendrow.jooq.generated.tables.Wallet.Companion.WALLET
import me.rcendrow.jooq.generated.tables.ServiceWallet.Companion.SERVICE_WALLET
import me.rcendrow.wallet.domain.wallet.ServiceWallet
import me.rcendrow.wallet.domain.wallet.ServiceWalletRole
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class ServiceWalletRepository(private val db: DSLContext) {

    fun findAll(): List<ServiceWallet> {
        return db.select(*(WALLET.fields() + SERVICE_WALLET.ROLE))
            .from(WALLET)
            .innerJoin(SERVICE_WALLET).on(SERVICE_WALLET.WALLET_ID.eq(WALLET.ID))
            .fetchInto(ServiceWallet::class.java)
    }

    fun findByRole(role: ServiceWalletRole): ServiceWallet {
        return db.select(*(WALLET.fields() + SERVICE_WALLET.ROLE))
            .from(WALLET)
            .innerJoin(SERVICE_WALLET).on(SERVICE_WALLET.WALLET_ID.eq(WALLET.ID))
            .where(SERVICE_WALLET.ROLE.eq(role.name))
            .fetchSingleInto(ServiceWallet::class.java)
    }

    @Transactional
    fun create(wallet: ServiceWallet): ServiceWallet {
        db.insertInto(WALLET)
            .set(WALLET.ID, wallet.id)
            .set(WALLET.TYPE, wallet.type.name)
            .set(WALLET.STATUS, wallet.status.name)
            .set(WALLET.CREATED_AT, wallet.createdAt)
            .execute()
        db.insertInto(SERVICE_WALLET)
            .set(SERVICE_WALLET.WALLET_ID, wallet.id)
            .set(SERVICE_WALLET.ROLE, wallet.role.name)
            .execute()
        return wallet
    }
}
