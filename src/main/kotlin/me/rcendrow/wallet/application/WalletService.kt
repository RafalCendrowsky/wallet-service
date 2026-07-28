package me.rcendrow.wallet.application

import com.fasterxml.uuid.Generators
import me.rcendrow.wallet.application.exception.InsufficientFundsException
import me.rcendrow.wallet.application.exception.NotFoundException
import me.rcendrow.wallet.domain.wallet.*
import me.rcendrow.wallet.persistence.wallet.WalletBalanceRepository
import me.rcendrow.wallet.persistence.wallet.WalletRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

@Service
class WalletService(
    private val customerService: CustomerService,
    private val walletBalanceService: WalletBalanceService,
    private val walletRepository: WalletRepository,
    private val walletBalanceRepository: WalletBalanceRepository,
) {
    @Transactional(readOnly = true)
    fun findWalletsByCustomer(customerId: UUID): List<Wallet> {
        return walletRepository.findAllByCustomerId(customerId)
    }

    @Transactional(readOnly = true)
    fun getCustomerWallet(customerId: UUID, walletId: UUID): Wallet {
        return walletRepository.findByCustomerIdAndWalletId(customerId, walletId)
            ?: throw NotFoundException("Wallet", walletId)
    }

    @Transactional(readOnly = true)
    fun getCustomerWallet(customerId: UUID): Wallet {
        return walletRepository.findByCustomerId(customerId) ?: throw NotFoundException(
            "Wallet for customer",
            customerId
        )
    }

    @Transactional(readOnly = true)
    fun getCustomerWalletById(walletId: UUID): Wallet {
        return walletRepository.findById(walletId) ?: throw NotFoundException("Wallet", walletId)
    }

    @Transactional
    fun findOrCreateCustomerWallet(customerId: UUID): Wallet {
        return walletRepository.findByCustomerId(customerId) ?: createCustomerWallet(customerId)
    }

    @Transactional
    fun createCustomerWallet(customerId: UUID): Wallet {
        val customer = customerService.getCustomer(customerId)
        val wallet = Wallet(
            id = Generators.timeBasedEpochRandomGenerator().generate(),
            owner = WalletOwner.from(customer),
            status = WalletStatus.ACTIVE,
            createdAt = LocalDateTime.now(),
        )
        return walletRepository.create(wallet).also {
            walletBalanceRepository.create(it.id)
        }
    }

    @Transactional
    fun lockAndVerifyBalance(wallet: Wallet, amount: BigDecimal) {
        walletRepository.lockWallet(wallet.id)
        val balance = walletBalanceService.findBalance(wallet.id)
        if (balance.availableBalance < amount) {
            throw InsufficientFundsException(wallet.id, balance.availableBalance, amount)
        }
    }

    @Transactional(readOnly = true)
    fun getBalance(customerId: UUID, walletId: UUID): WalletBalance {
        getCustomerWallet(customerId, walletId)
        return walletBalanceService.findBalance(walletId)
    }

    @Transactional
    fun updateWalletStatus(customerId: UUID, walletId: UUID, status: WalletStatus): Wallet {
        val wallet = getCustomerWallet(customerId, walletId)
        wallet.verifyStatusNot(WalletStatus.CLOSED)
        return walletRepository.updateStatus(wallet, status)
    }

    @Transactional
    fun updateWalletStatus(walletId: UUID, status: WalletStatus): Wallet {
        val wallet = walletRepository.findById(walletId) ?: throw NotFoundException("Wallet", walletId)
        wallet.verifyStatusNot(WalletStatus.CLOSED)
        return walletRepository.updateStatus(wallet, status)
    }

    @Transactional(readOnly = true)
    fun getServiceWalletByRole(role: ServiceRole): Wallet = walletRepository.findByServiceId(role)
}
