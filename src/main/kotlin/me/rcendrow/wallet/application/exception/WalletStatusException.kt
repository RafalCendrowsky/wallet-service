package me.rcendrow.wallet.application.exception

import me.rcendrow.wallet.domain.wallet.WalletStatus
import java.util.*

class WalletStatusException(walletId: UUID, status: WalletStatus) :
    RuntimeException("Operation not permitted for wallet $walletId with status ${status.name}")
