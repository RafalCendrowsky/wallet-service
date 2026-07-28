package me.rcendrow.wallet.application

import com.fasterxml.uuid.Generators
import me.rcendrow.wallet.application.exception.InsufficientFundsException
import me.rcendrow.wallet.application.exception.NotFoundException
import me.rcendrow.wallet.domain.wallet.*
import me.rcendrow.wallet.persistence.wallet.CustomerWalletRepository
import me.rcendrow.wallet.persistence.wallet.ServiceWalletRepository
import me.rcendrow.wallet.persistence.wallet.WalletBalanceRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

@Service
class WalletService(
    private val customerService: CustomerService,
    private val walletBalanceService: WalletBalanceService,
    private val customerWalletRepository: CustomerWalletRepository,
    private val serviceWalletRepository: ServiceWalletRepository,
    private val walletBalanceRepository: WalletBalanceRepository,
) {
    @Transactional(readOnly = true)
    fun findWalletsByCustomer(customerId: UUID): List<CustomerWallet> {
        customerService.getCustomer(customerId)
        return customerWalletRepository.findAllByCustomerId(customerId)
    }

    @Transactional(readOnly = true)
    fun getCustomerWallet(customerId: UUID, walletId: UUID): CustomerWallet {
        return customerWalletRepository.findByCustomerIdAndWalletId(customerId, walletId)
            ?: throw NotFoundException("CustomerWallet", walletId)
    }

    @Transactional(readOnly = true)
    fun getCustomerWallet(customerId: UUID): CustomerWallet {
        return customerWalletRepository.findByCustomerId(customerId)
            ?: throw NotFoundException("Wallet for customer", customerId)
    }

    @Transactional(readOnly = true)
    fun getCustomerWalletById(walletId: UUID): CustomerWallet {
        return customerWalletRepository.findById(walletId)
            ?: throw NotFoundException("CustomerWallet", walletId)
    }

    @Transactional
    fun findOrCreateCustomerWallet(customerId: UUID): CustomerWallet {
        customerService.getCustomer(customerId)
        return customerWalletRepository.findByCustomerId(customerId)
            ?: createCustomerWallet(customerId)
    }

    @Transactional
    fun createCustomerWallet(customerId: UUID): CustomerWallet {
        customerService.getCustomer(customerId)
        val wallet = CustomerWallet(
            id = Generators.timeBasedEpochRandomGenerator().generate(),
            customerId = customerId,
            status = WalletStatus.ACTIVE,
            createdAt = LocalDateTime.now(),
        )
        return customerWalletRepository.create(wallet).also {
            walletBalanceRepository.create(it.id)
        }
    }

    @Transactional
    fun lockAndVerifyBalance(wallet: CustomerWallet, amount: BigDecimal) {
        customerWalletRepository.lockWallet(wallet.id)
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
    fun updateWalletStatus(customerId: UUID, walletId: UUID, status: WalletStatus): CustomerWallet {
        val wallet = getCustomerWallet(customerId, walletId)
        wallet.verifyStatusNot(WalletStatus.CLOSED)
        return customerWalletRepository.updateStatus(wallet, status)
    }

    @Transactional
    fun updateWalletStatus(walletId: UUID, status: WalletStatus): CustomerWallet {
        val wallet = customerWalletRepository.findById(walletId)
            ?: throw NotFoundException("CustomerWallet", walletId)
        wallet.verifyStatusNot(WalletStatus.CLOSED)
        return customerWalletRepository.updateStatus(wallet, status)
    }

    fun getServiceWalletByRole(role: ServiceWalletRole): ServiceWallet =
        serviceWalletRepository.findByRole(role)
}
