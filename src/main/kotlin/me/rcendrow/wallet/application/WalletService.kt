package me.rcendrow.wallet.application

import com.fasterxml.uuid.Generators
import me.rcendrow.wallet.application.exception.InsufficientFundsException
import me.rcendrow.wallet.application.exception.NotFoundException
import me.rcendrow.wallet.domain.wallet.*
import me.rcendrow.wallet.persistence.wallet.WalletBalanceRepository
import me.rcendrow.wallet.persistence.wallet.CustomerWalletRepository
import me.rcendrow.wallet.persistence.wallet.ServiceWalletRepository
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
    fun getCustomerWallet(id: UUID): CustomerWallet = findCustomerWalletById(id)

    @Transactional
    fun createCustomerWallet(customerId: UUID): CustomerWallet {
        customerService.getCustomer(customerId)
        return CustomerWallet(
            id = Generators.timeBasedEpochRandomGenerator().generate(),
            customerId = customerId,
            status = WalletStatus.ACTIVE,
            createdAt = LocalDateTime.now(),
        ).let {
            customerWalletRepository.create(it)
            walletBalanceRepository.create(it.id)
            it
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
    fun getBalance(id: UUID): WalletBalance {
        findCustomerWalletById(id)
        return walletBalanceService.findBalance(id)
    }

    @Transactional
    fun updateWalletStatus(id: UUID, status: WalletStatus): CustomerWallet {
        val wallet = findCustomerWalletById(id)
        wallet.verifyStatusNot(WalletStatus.CLOSED)
        return customerWalletRepository.updateStatus(wallet, status)
    }

    fun getServiceWalletByRole(role: ServiceWalletRole): ServiceWallet =
        serviceWalletRepository.findByRole(role)

    private fun findCustomerWalletById(id: UUID): CustomerWallet =
        customerWalletRepository.findById(id) ?: throw NotFoundException("CustomerWallet", id)
}
