package me.rcendrow.wallet

import org.springframework.boot.fromApplication

fun main(args: Array<String>) {
    fromApplication<WalletServiceApplication>()
        .with(TestcontainersConfiguration::class.java)
        .run(*args)
}
