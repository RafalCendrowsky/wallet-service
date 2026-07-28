package me.rcendrow.wallet.api.dto

import me.rcendrow.wallet.domain.wallet.WalletOwner
import me.rcendrow.wallet.domain.wallet.WalletOwnerType
import java.util.*

data class WalletOwnerResponse(
    val id: UUID,
    val displayName: String,
    val label: String,
    val type: WalletOwnerType
) {
    companion object {
        fun from(owner: WalletOwner) = WalletOwnerResponse(
            id = owner.id,
            displayName = owner.displayName,
            label = owner.label,
            type = owner.type
        )
    }
}
