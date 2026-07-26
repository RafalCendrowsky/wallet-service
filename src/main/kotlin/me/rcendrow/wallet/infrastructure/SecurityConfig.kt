package me.rcendrow.wallet.infrastructure

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableWebSecurity
@ConditionalOnMissingBean(SecurityFilterChain::class)
class SecurityConfig(val customerJwtAuthenticationConverter: CustomerJwtAuthenticationConverter) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .authorizeHttpRequests {
                it
                    .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/").permitAll()
                    .requestMatchers(HttpMethod.POST, "/customers").hasAnyRole("PENDING", "USER")
                    .anyRequest().hasRole("USER")
            }
            .oauth2ResourceServer {
                it.jwt { jwt ->
                    jwt.jwtAuthenticationConverter(customerJwtAuthenticationConverter)
                }
            }
        return http.build()
    }
}
