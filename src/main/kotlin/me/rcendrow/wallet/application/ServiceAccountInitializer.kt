package me.rcendrow.wallet.application

import com.fasterxml.uuid.Generators
import me.rcendrow.wallet.domain.ServiceAccount
import me.rcendrow.wallet.domain.wallet.ServiceRole
import me.rcendrow.wallet.persistence.ServiceAccountRepository
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ServiceAccountInitializer(
    private val serviceAccountRepository: ServiceAccountRepository,
    private val walletService: WalletService,
) : ApplicationRunner {

    @Transactional
    override fun run(args: ApplicationArguments) {
        val existingRoles = serviceAccountRepository.findAll().map { it.role }.toSet()
        val missingRoles = ServiceRole.entries.filter { it !in existingRoles }

        for (role in missingRoles) {
            val serviceAccount = ServiceAccount(
                id = Generators.timeBasedEpochRandomGenerator().generate(),
                role = role,
                displayName = role.name,
            )
            serviceAccountRepository.create(serviceAccount)
            walletService.createServiceWallet(serviceAccount)
        }
    }
}
