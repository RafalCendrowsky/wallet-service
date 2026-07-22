package me.rcendrow.settlement

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@EnableScheduling
@SpringBootApplication
class SettlementServiceApplication

fun main(args: Array<String>) {
    runApplication<SettlementServiceApplication>(*args)
}
