package me.rcendrow.wallet.infrastructure.kafka

import org.apache.kafka.clients.admin.NewTopic
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafka

@Configuration
@EnableKafka
class KafkaConfig {

    @Bean
    fun depositCompletedTopic() = NewTopic("wallet.deposit.completed", 1, 1)

    @Bean
    fun withdrawalInitiatedTopic() = NewTopic("wallet.withdrawal.initiated", 1, 1)

    @Bean
    fun withdrawalCompletedTopic() = NewTopic("wallet.withdrawal.completed", 1, 1)
}
