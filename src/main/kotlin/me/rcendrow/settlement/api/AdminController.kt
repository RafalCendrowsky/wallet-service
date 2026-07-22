package me.rcendrow.settlement.api

import me.rcendrow.settlement.application.AccountBalanceService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/admin")
class AdminController(
    private val accountBalanceService: AccountBalanceService,
) {

    @PostMapping("/rebuild-balances")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun rebuildBalances() {
        accountBalanceService.rebuild()
    }
}
