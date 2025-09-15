package com.dddheroes.cinema

import com.dddheroes.cinema.shared.restapi.ErrorResponse
import org.axonframework.common.transaction.TransactionManager
import org.axonframework.config.ConfigurationScopeAwareProvider
import org.axonframework.config.Configurer
import org.axonframework.config.ConfigurerModule
import org.axonframework.deadline.DeadlineManager
import org.axonframework.deadline.DeadlineManagerSpanFactory
import org.axonframework.deadline.SimpleDeadlineManager
import org.axonframework.eventhandling.GenericDomainEventMessage
import org.axonframework.messaging.correlation.CorrelationDataProvider
import org.axonframework.messaging.correlation.MessageOriginProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.Clock
import java.util.function.Consumer


@Configuration
class AxonFrameworkConfiguration {

    @Bean
    fun messageOriginProvider(): CorrelationDataProvider = MessageOriginProvider()

    @Bean
    fun processorDefaultConfigurerModule(): ConfigurerModule {
        return ConfigurerModule { configurer: Configurer -> configurer.eventProcessing(Consumer { it.usingPooledStreamingEventProcessors() }) }
    }

    @Bean
    fun clock(): Clock {
        val clock = Clock.systemUTC()
        GenericDomainEventMessage.clock = clock
        return clock
    }

    @Bean
    fun deadlineManager(
        configuration: org.axonframework.config.Configuration,
        transactionManager: TransactionManager,
        spanFactory: DeadlineManagerSpanFactory
    ): DeadlineManager = SimpleDeadlineManager.builder()
        .scopeAwareProvider(ConfigurationScopeAwareProvider(configuration))
        .transactionManager(transactionManager)
        .spanFactory(spanFactory)
        .build()

}

@RestControllerAdvice
class GlobalControllerExceptionHandler {

    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ResponseEntity<ErrorResponse> {
        return ResponseEntity.badRequest()
            .body(ErrorResponse(e.message ?: "Unknown error occurred"));
    }

}