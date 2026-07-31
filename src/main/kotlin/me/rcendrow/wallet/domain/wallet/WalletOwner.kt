package me.rcendrow.wallet.domain.wallet

import me.rcendrow.wallet.domain.Customer
import me.rcendrow.wallet.domain.ServiceAccount
import java.util.*

data class WalletOwner(
    val id: UUID,
    val displayName: String,
    val label: String,
    val type: WalletOwnerType,
) {
    companion object {
        fun from(customer: Customer) = WalletOwner(
            id = customer.id,
            displayName = customer.displayName,
            label = customer.handle,
            type = WalletOwnerType.CUSTOMER
        )

        fun from(serviceAccount: ServiceAccount) = WalletOwner(
            id = serviceAccount.id,
            displayName = serviceAccount.displayName,
            label = serviceAccount.role.name,
            type = WalletOwnerType.SERVICE
        )

        fun from(id: UUID?, displayName: String?, label: String?, type: String?): WalletOwner? {
            if (id == null) return null
            return WalletOwner(
                id = id,
                displayName = requireNotNull(displayName),
                label = requireNotNull(label),
                type = requireNotNull(type).let { WalletOwnerType.valueOf(it) }
            )
        }
    }
}


