package com.dddheroes.cinema

import com.dddheroes.sdk.application.CommandResult
import io.restassured.module.mockmvc.RestAssuredMockMvc
import org.axonframework.commandhandling.gateway.CommandGateway
import org.axonframework.eventhandling.GenericDomainEventMessage
import org.axonframework.messaging.responsetypes.ResponseTypes
import org.axonframework.queryhandling.QueryGateway
import org.junit.jupiter.api.BeforeEach
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.argThat
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CompletableFuture

abstract class RestApiSpringBootTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var queryGateway: QueryGateway

    @MockitoBean
    lateinit var commandGateway: CommandGateway

    @MockitoBean
    lateinit var clock: Clock

    @BeforeEach
    fun setUp() {
        RestAssuredMockMvc.mockMvc(mockMvc)
        currentTimeIs(Instant.now())
    }

    protected inline fun <reified R, reified Q : Any> assumeQueryReturns(query: Q, result: R) {
        Mockito.doReturn(CompletableFuture.completedFuture<R>(result))
            .`when`(queryGateway).query(query, ResponseTypes.instanceOf(R::class.java))
    }

    protected fun <T : Any> assumeCommandSuccess(command: T) {
        Mockito.doReturn(CompletableFuture.completedFuture(CommandResult.Success))
            .`when`(commandGateway).send<CommandResult>(eq(command))
        Mockito.doReturn(CommandResult.Success)
            .`when`(commandGateway).sendAndWait<CommandResult>(eq(command))
        Mockito.doReturn(CommandResult.Success)
            .`when`(commandGateway).sendAndWait<CommandResult>(eq(command), any())
    }

    final inline fun <reified T : Any> assumeCommandSuccess() {
        Mockito.doReturn(CompletableFuture.completedFuture(CommandResult.Success))
            .`when`(commandGateway).send<CommandResult>(argThat { it is T })
        Mockito.doReturn(CommandResult.Success)
            .`when`(commandGateway).sendAndWait<CommandResult>(argThat { it is T })
    }

    protected fun <T : Any> assumeCommandFailure(command: T) {
        val result = CommandResult.Failure("Simulated failure")
        Mockito.doReturn(CompletableFuture.completedFuture(result))
            .`when`(commandGateway).send<CommandResult>(eq(command))
        Mockito.doReturn(result)
            .`when`(commandGateway).sendAndWait<CommandResult>(eq(command))
        Mockito.doReturn(result)
            .`when`(commandGateway).sendAndWait<CommandResult>(eq(command), any())
    }

    final inline fun <reified T : Any> assumeCommandFailure() {
        Mockito.doReturn(CompletableFuture.completedFuture(CommandResult.Failure("Simulated failure")))
            .`when`(commandGateway).send<CommandResult>(argThat { it is T })
        Mockito.doReturn(CommandResult.Failure("Simulated failure"))
            .`when`(commandGateway).sendAndWait<CommandResult>(argThat { it is T })
    }

    protected fun currentTimeIs(instant: Instant): Instant {
        Mockito.`when`(clock.instant()).thenReturn(instant)
        GenericDomainEventMessage.clock = Clock.fixed(instant, ZoneOffset.UTC)
        return instant
    }

    protected fun currentTime(): Instant = clock.instant()
}