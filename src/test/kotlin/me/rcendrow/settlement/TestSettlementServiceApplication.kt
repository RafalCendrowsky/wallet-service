package me.rcendrow.settlement

import org.springframework.boot.fromApplication

fun main(args: Array<String>) {
    fromApplication<SettlementServiceApplication>()
        .with(TestcontainersConfiguration::class.java)
        .run(*args)
}
