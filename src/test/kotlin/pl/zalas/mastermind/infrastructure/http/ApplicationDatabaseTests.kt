package pl.zalas.mastermind.infrastructure.http

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceAccessMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.junit.jupiter.api.parallel.Resources
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import pl.zalas.mastermind.infrastructure.http.restassured.MakeGuessRequest
import pl.zalas.mastermind.infrastructure.http.restassured.makeGuess
import pl.zalas.mastermind.infrastructure.http.restassured.startGame
import pl.zalas.mastermind.infrastructure.http.restassured.viewBoard
import pl.zalas.mastermind.model.Code.CodePeg.GREEN
import pl.zalas.mastermind.model.Code.CodePeg.RED
import java.util.concurrent.atomic.AtomicInteger

@Testcontainers
@ResourceLock(value = Resources.SYSTEM_PROPERTIES, mode = ResourceAccessMode.READ_WRITE)
class ApplicationDatabaseTests {
    private lateinit var app: Application

    internal class TestPostgreSQLContainer : PostgreSQLContainer<TestPostgreSQLContainer>("postgres:11-alpine")

    @Container
    private val postgres = TestPostgreSQLContainer()
        .withDatabaseName("mastermind")
        .withUsername("mastermind")
        .withPassword("mastermind")
        .withExposedPorts(5432)

    companion object {
        val portNumber = AtomicInteger(9190)
    }

    @BeforeEach
    fun startApplication() {
        System.setProperty("mastermind.journal.port", postgres.getMappedPort(5432).toString())
        System.setProperty("mastermind.stateStore.port", postgres.getMappedPort(5432).toString())

        app = Application(portNumber.getAndIncrement(), "classpath:postgresql.properties")

        System.clearProperty("mastermind.journal.port")
        System.clearProperty("mastermind.stateStore.port")

        assertTrue(app.serverStartup().await<Boolean>(100))
    }

    @AfterEach
    fun stopApplication() {
        app?.run {
            stop()
        }
    }

    @Test
    fun `it plays the game`() {
        val gameId = app.startGame().gameId
        app.makeGuess(gameId, MakeGuessRequest(listOf(RED.name, GREEN.name, RED.name, GREEN.name)))
        val board = app.viewBoard(gameId)

        assertEquals(gameId, board.gameId)
    }
}