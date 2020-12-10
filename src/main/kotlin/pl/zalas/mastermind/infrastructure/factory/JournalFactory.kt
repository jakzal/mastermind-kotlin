package pl.zalas.mastermind.infrastructure.factory

import io.vlingo.actors.Definition
import io.vlingo.actors.Protocols
import io.vlingo.actors.Stage
import io.vlingo.lattice.model.DomainEvent
import io.vlingo.lattice.model.projection.ProjectionDispatcher
import io.vlingo.lattice.model.projection.TextProjectionDispatcherActor
import io.vlingo.lattice.model.sourcing.Sourced
import io.vlingo.lattice.model.sourcing.SourcedTypeRegistry
import io.vlingo.symbio.Entry
import io.vlingo.symbio.State
import io.vlingo.symbio.store.DataFormat
import io.vlingo.symbio.store.common.jdbc.Configuration
import io.vlingo.symbio.store.common.jdbc.DatabaseType
import io.vlingo.symbio.store.common.jdbc.postgres.PostgresConfigurationProvider
import io.vlingo.symbio.store.dispatch.Dispatchable
import io.vlingo.symbio.store.dispatch.Dispatcher
import io.vlingo.symbio.store.dispatch.DispatcherControl
import io.vlingo.symbio.store.dispatch.control.DispatcherControlActor
import io.vlingo.symbio.store.journal.Journal
import io.vlingo.symbio.store.journal.inmemory.InMemoryJournalActor
import io.vlingo.symbio.store.journal.jdbc.JDBCDispatcherControlDelegate
import io.vlingo.symbio.store.journal.jdbc.JDBCJournalActor
import io.vlingo.symbio.store.journal.jdbc.JDBCJournalInstantWriter
import io.vlingo.symbio.store.state.StateStore
import pl.zalas.mastermind.infrastructure.factory.JournalFactory.JournalConfiguration.InMemoryConfiguration
import pl.zalas.mastermind.infrastructure.factory.JournalFactory.JournalConfiguration.PostgreSQLConfiguration
import pl.zalas.mastermind.model.GameEntity
import pl.zalas.mastermind.model.GameEvent
import pl.zalas.mastermind.view.DecodingBoardProjectionActor
import java.util.*

class JournalFactory(private val stage: Stage, private val configuration: JournalConfiguration) {
    sealed class JournalConfiguration {
        object InMemoryConfiguration : JournalConfiguration()
        data class PostgreSQLConfiguration(
            val username: String,
            val password: String,
            val database: String,
            val hostname: String = "[::1]",
            val port: Int = 5432,
            val useSsl: Boolean = false
        ) : JournalConfiguration()
    }

    fun createJournal(store: StateStore): Journal<DomainEvent> {
        val decodingBoardProjectionDescription = ProjectionDispatcher.ProjectToDescription.with(
            DecodingBoardProjectionActor::class.java,
            Optional.of<Any>(store),
            GameEvent.GameStarted::class.java,
            GameEvent.GuessMade::class.java
        )
        val descriptions = listOf(
            decodingBoardProjectionDescription
        )
        val dispatcherProtocols = stage.actorFor(
            arrayOf(Dispatcher::class.java, ProjectionDispatcher::class.java),
            Definition.has(TextProjectionDispatcherActor::class.java, listOf(descriptions))
        )
        val dispatchers =
            Protocols.two<Dispatcher<Dispatchable<Entry<DomainEvent>, State.TextState>>, ProjectionDispatcher>(
                dispatcherProtocols
            )

        return createJournal(dispatchers._1)
    }

    fun createJournal(dispatcher: Dispatcher<Dispatchable<Entry<DomainEvent>, State.TextState>>): Journal<DomainEvent> {
        val journal = when (configuration) {
            is InMemoryConfiguration -> Journal.using(stage, InMemoryJournalActor::class.java, dispatcher)
            is PostgreSQLConfiguration -> with(
                Configuration(
                    DatabaseType.Postgres,
                    PostgresConfigurationProvider.interest,
                    org.postgresql.Driver::class.java.name,
                    DataFormat.Text,
                    "jdbc:postgresql://${configuration.hostname}:${configuration.port}/",
                    configuration.database,
                    configuration.username,
                    configuration.password,
                    configuration.useSsl,
                    "",
                    true
                )
            ) {
                stage.actorFor(
                    Journal::class.java,
                    JDBCJournalActor::class.java,
                    this,
                    JDBCJournalInstantWriter(
                        this,
                        listOf(dispatcher as Dispatcher<Dispatchable<Entry<String>, State.TextState>>),
                        dispatcherControl(dispatcher, this)
                    )
                ) as Journal<DomainEvent>
            }
        }

        val registry = SourcedTypeRegistry(stage.world())
        @Suppress("UNCHECKED_CAST")
        registry.register(
            SourcedTypeRegistry.Info(
                journal,
                GameEntity::class.java as Class<Sourced<DomainEvent>>,
                GameEntity::class.java.simpleName
            )
        )
        return journal
    }

    private fun dispatcherControl(
        dispatcher: Dispatcher<Dispatchable<Entry<DomainEvent>, State.TextState>>,
        configuration: Configuration
    ) = stage.actorFor(
        DispatcherControl::class.java,
        DispatcherControlActor::class.java,
        listOf(dispatcher),
        JDBCDispatcherControlDelegate(Configuration.cloneOf(configuration), stage.world().defaultLogger()),
        StateStore.DefaultCheckConfirmationExpirationInterval,
        StateStore.DefaultConfirmationExpiration
    )
}