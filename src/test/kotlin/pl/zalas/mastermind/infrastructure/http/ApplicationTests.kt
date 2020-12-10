package pl.zalas.mastermind.infrastructure.http

import io.restassured.http.ContentType
import io.restassured.module.kotlin.extensions.Given
import io.restassured.module.kotlin.extensions.Then
import io.restassured.module.kotlin.extensions.When
import org.hamcrest.Matchers.greaterThanOrEqualTo
import org.hamcrest.Matchers.matchesPattern
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import pl.zalas.mastermind.infrastructure.http.restassured.MakeGuessRequest
import pl.zalas.mastermind.infrastructure.http.restassured.makeGuess
import pl.zalas.mastermind.infrastructure.http.restassured.startGame
import pl.zalas.mastermind.model.Code.CodePeg.GREEN
import pl.zalas.mastermind.model.Code.CodePeg.RED
import pl.zalas.mastermind.model.GameId
import java.util.concurrent.atomic.AtomicInteger

class ApplicationTests {
    private lateinit var app: Application

    companion object {
        val portNumber = AtomicInteger(9090)
    }

    @BeforeEach
    fun startApplication() {
        app = Application(portNumber.getAndIncrement(), "classpath:in-memory.properties")

        assertTrue(app.serverStartup().await<Boolean>(100))
    }

    @AfterEach
    fun stopApplication() {
        app.stop()
    }

    @Test
    fun `it starts the game`() {
        Given {
            port(app.port)
        } When {
            post("/games")
        } Then {
            statusCode(201)
            contentType(ContentType.JSON)
            header("Location", matchesPattern("^/games/[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$"))
            body("gameId", matchesUuid())
        }
    }

    @Test
    fun `it makes a guess`() {
        val gameId = app.startGame().gameId
        Given {
            port(app.port)
            contentType(ContentType.JSON)
        } When {
            body(MakeGuessRequest(listOf(RED.name, GREEN.name, RED.name, GREEN.name)))
            post("/games/$gameId")
        } Then {
            statusCode(200)
            contentType(ContentType.JSON)
            body("gameId", matchesUuid())
            body("feedback.outcome", matchesPattern("IN_PROGRESS|WON"))
            body("feedback.pegs.size()",  greaterThanOrEqualTo(0))
        }
    }

    @Test
    fun `it returns an error if the guess cannot be accepted`() {
        val gameId = app.startGame().gameId
        Given {
            port(app.port)
            contentType(ContentType.JSON)
        } When {
            body(MakeGuessRequest(listOf(RED.name, GREEN.name)))
            post("/games/$gameId")
        } Then {
            statusCode(400)
            contentType(ContentType.JSON)
            body("gameId", matchesUuid())
            body("error", matchesPattern("The code is 2 colours long but expected it to be 4."))
        }
    }

    @Test
    fun `it returns an error if the guess code is invalid`() {
        val gameId = app.startGame().gameId
        Given {
            port(app.port)
            contentType(ContentType.JSON)
        } When {
            body(MakeGuessRequest(listOf("REDDISH", "YELLOWISH", "BLUEISH", "GREENISH")))
            post("/games/$gameId")
        } Then {
            statusCode(400)
            contentType(ContentType.JSON)
            body("gameId", matchesUuid())
            body("error", matchesPattern("Invalid guess code: REDDISH, YELLOWISH, BLUEISH, GREENISH.*Allowed colours:.*"))
        }
    }

    @Test
    @Disabled("It always finds the game and returns 400.")
    fun `it returns a 404 if the game is not found`() {
        Given {
            port(app.port)
            contentType(ContentType.JSON)
        } When {
            body(MakeGuessRequest(listOf(RED.name, GREEN.name, RED.name, GREEN.name)))
            post("/games/${GameId.generate()}")
        } Then {
            statusCode(404)
            contentType(ContentType.JSON)
        }
    }

    @Test
    fun `it returns the decoding board`() {
        val gameId = app.startGame().gameId
        app.makeGuess(gameId, MakeGuessRequest(listOf(RED.name, GREEN.name, RED.name, GREEN.name)))
        Given {
            port(app.port)
        } When {
            get("/games/$gameId")
        } Then {
            statusCode(200)
            contentType(ContentType.JSON)
            body("gameId", matchesUuid())
        }
    }

    @Test
    @Disabled
    fun `it returns a 404 if the decoding board is not found`() {
        Given {
            port(app.port)
        } When {
            get("/games/${GameId.generate()}")
        } Then {
            statusCode(404)
            contentType(ContentType.JSON)
        }
    }

    private fun matchesUuid() = matchesPattern("^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$")
}