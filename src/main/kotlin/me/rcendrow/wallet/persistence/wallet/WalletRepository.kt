package me.rcendrow.wallet.persistence.wallet

import me.rcendrow.jooq.generated.tables.Wallet.Companion.WALLET
import me.rcendrow.jooq.generated.tables.references.WALLET_OWNER
import me.rcendrow.jooq.generated.tables.references.WALLET_OWNER_VIEW
import me.rcendrow.wallet.domain.wallet.Wallet
import me.rcendrow.wallet.domain.wallet.WalletOwnerType
import me.rcendrow.wallet.domain.wallet.WalletStatus
import me.rcendrow.wallet.persistence.ownerField
import org.jooq.DSLContext
import org.jooq.Records
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Repository
class WalletRepository(private val db: DSLContext) {

    fun findAllServiceWallets(): List<Wallet> {
        return selectWithOwner()
            .where(WALLET_OWNER_VIEW.OWNER_TYPE.eq(WalletOwnerType.SERVICE.name))
            .fetch(Records.mapping(Wallet::from))
    }

    fun findByServiceId(serviceId: UUID): Wallet {
        return selectWithOwner()
            .where(WALLET_OWNER_VIEW.SERVICE_ID.eq(serviceId))
            .fetchSingle(Records.mapping(Wallet::from))
    }

    fun findAllByCustomerId(customerId: UUID): List<Wallet> {
        return selectWithOwner()
            .where(WALLET_OWNER_VIEW.CUSTOMER_ID.eq(customerId))
            .fetch(Records.mapping(Wallet::from))
    }

    fun findByCustomerId(customerId: UUID): Wallet? {
        return selectWithOwner()
            .where(WALLET_OWNER_VIEW.CUSTOMER_ID.eq(customerId))
            .fetchOne(Records.mapping(Wallet::from))
    }

    fun findByCustomerIdAndWalletId(customerId: UUID, walletId: UUID): Wallet? {
        return selectWithOwner()
            .where(WALLET.ID.eq(walletId))
            .and(WALLET_OWNER_VIEW.CUSTOMER_ID.eq(customerId))
            .fetchOne(Records.mapping(Wallet::from))
    }

    fun findById(walletId: UUID): Wallet? {
        return selectWithOwner()
            .where(WALLET.ID.eq(walletId))
            .fetchOne(Records.mapping(Wallet::from))
    }

    fun lockWallet(id: UUID) {
        db.select(WALLET.ID)
            .from(WALLET)
            .where(WALLET.ID.eq(id))
            .forUpdate()
            .execute()
    }

    @Transactional
    fun create(wallet: Wallet): Wallet {
        db.insertInto(WALLET)
            .set(WALLET.ID, wallet.id)
            .set(WALLET.STATUS, wallet.status.name)
            .set(WALLET.CREATED_AT, wallet.createdAt)
            .execute()
        db.insertInto(WALLET_OWNER)
            .set(WALLET_OWNER.WALLET_ID, wallet.id)
            .set(WALLET_OWNER.CUSTOMER_ID, wallet.owner.takeIf { it.type == WalletOwnerType.CUSTOMER }?.id)
            .set(WALLET_OWNER.SERVICE_ID, wallet.owner.takeIf { it.type == WalletOwnerType.SERVICE }?.id)
            .execute()
        return wallet
    }

    fun updateStatus(wallet: Wallet, status: WalletStatus): Wallet {
        db.update(WALLET)
            .set(WALLET.STATUS, status.name)
            .where(WALLET.ID.eq(wallet.id))
            .and(WALLET.STATUS.ne(status.name)).execute()
        return wallet.copy(status = status)
    }

    private fun selectWithOwner() = db
        .select(
            WALLET.ID,
            WALLET_OWNER_VIEW.ownerField(),
            WALLET.STATUS,
            WALLET.CREATED_AT
        ).from(WALLET)
        .join(WALLET_OWNER_VIEW).on(WALLET_OWNER_VIEW.WALLET_ID.eq(WALLET.ID))
}
