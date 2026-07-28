package me.rcendrow.wallet.persistence

import me.rcendrow.jooq.generated.tables.WalletOwnerView
import me.rcendrow.wallet.domain.wallet.WalletOwner
import org.jooq.impl.DSL

fun WalletOwnerView.ownerField() =
    DSL.row(OWNER_ID, OWNER_DISPLAY_NAME, OWNER_LABEL, OWNER_TYPE).mapping(WalletOwner::from)
