package com.gitlab.kordlib.gateway

import com.gitlab.kordlib.common.ratelimit.RateLimiter
import com.gitlab.kordlib.gateway.GatewayCloseCode.*
import com.gitlab.kordlib.gateway.handler.*
import com.gitlab.kordlib.gateway.retry.Retry
import io.ktor.client.HttpClient
import io.ktor.client.features.websocket.webSocketSession
import io.ktor.client.request.url
import io.ktor.http.URLBuilder
import io.ktor.http.cio.websocket.CloseReason
import io.ktor.http.cio.websocket.DefaultWebSocketSession
import io.ktor.http.cio.websocket.Frame
import io.ktor.http.cio.websocket.close
import io.ktor.util.error
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.sendBlocking
import kotlinx.coroutines.flow.*
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import mu.KLogger
import mu.KotlinLogging
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.util.zip.Inflater
import java.util.zip.InflaterOutputStream
import kotlin.time.Duration

private val defaultGatewayLogger = KotlinLogging.logger { }


/**
 * @param url The url to connect to.
 * @param client The client from which a webSocket will be created, requires the WebSockets and JsonFeature to be
 * installed.
 * @param reconnectRetry A retry used for reconnection attempts.
 * @param sendRateLimiter A rate limiter than follows the Discord API specifications for sending messages.
 * @param identifyRateLimiter: A rate limiter that follows the Discord API specifications for identifying.
 */
data class DefaultGatewayData(
        val url: String,
        val connectionProvider: GatewayConnectionProvider,
        val reconnectRetry: Retry,
        val sendRateLimiter: RateLimiter,
        val identifyRateLimiter: RateLimiter
)

interface GatewayConnectionProvider {

    suspend fun provide(url: String): GatewayConnection

}

class HttpGatewayConnectionProvider(val client: HttpClient) : GatewayConnectionProvider {

    override suspend fun provide(url: String): GatewayConnection =
            HttpGatewayConnection(client.webSocketSession { url(url) })

}

class HttpGatewayConnection(
        private val session: DefaultWebSocketSession
) : GatewayConnection {

    override val closeReason: Deferred<CloseReason?>
        get() = session.closeReason

    override val incoming: Flow<ByteArray> = flow {
        try {
            for (value in session.incoming) {
                if (value is Frame.Text || value is Frame.Binary) emit(value.data)
                else if (value is Frame.Close) state = GatewayConnection.State.Closed
            }
        } catch (ignore: CancellationException) {
            //reading was stopped from somewhere else, ignore and stop consuming
        }
    }

    override var state: GatewayConnection.State = GatewayConnection.State.Running

    override suspend fun close(closeReason: CloseReason?) {
        state = GatewayConnection.State.Closed
        session.close(closeReason ?: CloseReason(1000, "Closing"))
    }

    override suspend fun send(serializer: SerializationStrategy<Command>, command: Command) {
        val json = Json.stringify(serializer, command)
        session.send(Frame.Text(json))
    }

}

interface GatewayConnection {

    val closeReason: Deferred<CloseReason?>

    val incoming: Flow<ByteArray>

    val state: State

    suspend fun send(serializer: SerializationStrategy<Command>, command: Command)

    suspend fun close(closeReason: CloseReason? = null)

    sealed class State {
        object Running : State()
        object Closed : State()
    }

}

suspend fun GatewayConnection.send(command: Command) = send(Command.Companion, command)

/**
 * The default Gateway implementation of Kord, using an [HttpClient] for the underlying webSocket
 */
class DefaultGateway(private val data: DefaultGatewayData) : Gateway {

    private val gatewayScope = CoroutineScope(Dispatchers.Default)

    private lateinit var connection: GatewayConnection

    private val actionQueue = Channel<Action>(Channel.UNLIMITED)

    private val compression: Boolean = URLBuilder(data.url).parameters.contains("compress", "zlib-stream")

    private val eventsChannel = BroadcastChannel<Any>(Channel.CONFLATED)

    private var _ping: MutableStateFlow<Duration> = MutableStateFlow(Duration.INFINITE)

    override var ping: StateFlow<Duration> = _ping

    override val events: Flow<Event> = eventsChannel.asFlow().drop(1).filterIsInstance()

    private val state: AtomicRef<State> = atomic(State.Stopped)

    private val handshakeHandler: HandshakeHandler

    private lateinit var inflater: Inflater

    private val jsonParser = Json(JsonConfiguration(
            isLenient = true,
            ignoreUnknownKeys = true,
            serializeSpecialFloatingPointValues = true,
            useArrayPolymorphism = true
    ))

    init {
        eventsChannel.sendBlocking(Unit)
        val sequence = Sequence()
        SequenceHandler(events, sequence)
        handshakeHandler = HandshakeHandler(events, ::send, sequence, data.identifyRateLimiter)
        HeartbeatHandler(events, ::send, { restart(Close.ZombieConnection) }, { _ping.value = it }, sequence)
        ReconnectHandler(events) { restart(Close.Reconnecting) }
        InvalidSessionHandler(events) { restart(it) }

        handleQueue()
    }

    //running on default dispatchers because ktor does *not* like running on an EmptyCoroutineContext from main
    override suspend fun start(configuration: GatewayConfiguration): Unit = withContext(Dispatchers.Default) {
        if(state.value is State.Detached) throw IllegalStateException(
                "The Gateway has been detached and cannot be started again, create a new one instead."
        )

        setState(State.Running(true))
        handshakeHandler.configuration = configuration
        data.reconnectRetry.reset()

        while (data.reconnectRetry.hasNext && state.value is State.Running) {
            try {
                connection = data.connectionProvider.provide(data.url)
                /**
                 * https://discordapp.com/developers/docs/topics/gateway#transport-compression
                 *
                 * > Every connection to the gateway should use its own unique zlib context.
                 */
                inflater = Inflater()
            } catch (exception: Exception) {
                defaultGatewayLogger.error(exception)
                if (exception is java.nio.channels.UnresolvedAddressException) {
                    eventsChannel.send(Close.Timeout)
                }

                data.reconnectRetry.retry()
                continue //can't handle a close code if you've got no socket
            }

            try {
                connection.incoming.collect { read(it) }
                data.reconnectRetry.reset() //connected and read without problems, resetting retry counter
            } catch (exception: Exception) {
                defaultGatewayLogger.error(exception)
            }

            defaultGatewayLogger.trace { "gateway connection closing" }

            try {
                handleClose()
            } catch (exception: Exception) {
                defaultGatewayLogger.error(exception)
            }

            defaultGatewayLogger.trace { "handled gateway connection closed" }

            if (state.value.retry) data.reconnectRetry.retry()
            else if(state.value !is State.Detached) eventsChannel.send(Close.RetryLimitReached)
        }

        if (!data.reconnectRetry.hasNext) {
            defaultGatewayLogger.warn { "retry limit exceeded, gateway closing" }
        }
    }


    private fun handleQueue() = actionQueue.consumeAsFlow().onEach { action ->
        when (action) {
            is Action.SendCommand -> {
                val result = runCatching { sendCommand(action.command) }
                action.callback.completeWith(result)
            }
            is Action.ChangeState -> {
                val result = runCatching { changeState(action.state) }
                action.callback.completeWith(result)
            }
        }
    }.flowOn(Dispatchers.Default).launchIn(gatewayScope)

    private fun changeState(state: State) = with(this.state.value) {
        navigateTo(state)
    }

    private suspend fun sendCommand(command: Command) {
        check(state.value is State.Running) { "Gateway can only send commands while running, but state was ${state.value::class.simpleName}" }
        data.sendRateLimiter.consume()
        defaultGatewayLogger.traceSend(command)
        connection.send(command)
    }

    private fun KLogger.traceSend(command: Command) = trace {
        val safeCommand = if (command is Identify) command.copy(token = "token") else command
        val json = Json.stringify(Command.Companion, safeCommand)
        "Gateway >>> $json"
    }

    private suspend fun setState(state: State) {
        val callback = CompletableDeferred<Unit>()
        val action = Action.ChangeState(state, callback)
        actionQueue.send(action)
        callback.await()
    }

    private suspend fun read(data: ByteArray) {
        val json = when {
            compression -> data.deflateData()
            else -> data.toString(Charset.defaultCharset())
        }

        try {
            defaultGatewayLogger.trace { "Gateway <<< $json" }
            val event = jsonParser.parse(Event.Companion, json)?.let { eventsChannel.send(it) }
        } catch (exception: Exception) {
            defaultGatewayLogger.error(exception)
        }

    }

    private fun ByteArray.deflateData(): String {
        val outputStream = ByteArrayOutputStream()
        InflaterOutputStream(outputStream, inflater).use {
            it.write(this)
        }

        return outputStream.use {
            outputStream.toString(Charset.defaultCharset().name())
        }
    }

    private suspend fun handleClose() {
        val reason = withTimeoutOrNull(1500) { connection.closeReason.await() } ?: return

        defaultGatewayLogger.trace { "Gateway closed: ${reason.code} ${reason.message}" }
        val discordReason = values().firstOrNull { it.code == reason.code.toInt() } ?: return

        eventsChannel.send(Close.DiscordClose(discordReason, discordReason.retry))

        when {
            !discordReason.retry -> {
                setState(State.Stopped)
                return
//                throw  IllegalStateException("Gateway closed: ${reason.code} ${reason.message}")
            }
            discordReason.resetSession -> {
                state.update { State.Running(true) }
            }
        }
    }

    override suspend fun stop() {
        check(state.value !is State.Detached) { "The resources of this gateway are detached, create another one" }
        eventsChannel.send(Close.UserClose)
        setState(State.Stopped)
        if (connectionOpen) connection.close(CloseReason(1000, "leaving"))
    }

    internal suspend fun restart(code: Close) {
        check(state.value !is State.Detached) { "The resources of this gateway are detached, create another one" }
        setState(State.Running(false))
        if (connectionOpen) {
            eventsChannel.send(code)
            connection.close(CloseReason(4900, "reconnecting"))
        }
    }

    override suspend fun detach() {
        if (state.value is State.Detached) return
        setState(State.Detached)
        if (::connection.isInitialized) {
            connection.close()
        }
        eventsChannel.send(Close.Detach)
        gatewayScope.cancel()
        actionQueue.close()
        eventsChannel.close()
    }

    override suspend fun send(command: Command) {
        val callback = CompletableDeferred<Unit>()
        val action = Action.SendCommand(command, callback)

        actionQueue.send(action)

        callback.await()
    }

    private val connectionOpen get() = ::connection.isInitialized && connection.state != GatewayConnection.State.Closed

    companion object {

        inline operator fun invoke(builder: DefaultGatewayBuilder.() -> Unit = {}): DefaultGateway =
                DefaultGatewayBuilder().apply(builder).build()

    }

    private sealed class State(val retry: Boolean) {
        object Stopped : State(false) {
            override fun DefaultGateway.navigateTo(state: State): Unit = when (state) {
                is Running, Stopped, Detached -> this.state.update { state }
            }

            override fun toString(): String = "Stopped"
        }

        class Running(retry: Boolean) : State(retry) {
            override fun DefaultGateway.navigateTo(state: State): Unit = when (state) {
                is Running, Stopped, Detached -> this.state.update { state }
            }

            override fun toString(): String = "Running(retry=$retry)"
        }

        object Detached : State(false) {
            override fun DefaultGateway.navigateTo(state: State): Unit = when (state) {
                Stopped, Detached, is Running -> throw IllegalStateException(
                        "The Gateway has been detached, navigation to $state is not allowed."
                )
            }

            override fun toString(): String = "Detached(retry=$retry)"
        }

        abstract fun DefaultGateway.navigateTo(state: State)

        protected fun illegalNavigation(to: State): Nothing = throw IllegalStateException(
                "Gateway state $this -> $to is not allowed"
        )

    }

    private sealed class Action {

        data class SendCommand(val command: Command, val callback: CompletableDeferred<Unit>) : Action()

        data class ChangeState(val state: State, val callback: CompletableDeferred<Unit>) : Action()
    }

}

internal val GatewayConfiguration.identify get() = Identify(token, IdentifyProperties(os, name, name), false, 50, shard, presence, intents)

internal val os: String get() = System.getProperty("os.name")

internal val GatewayCloseCode.retry
    get() = when (this) { //this statement is intentionally structured to ensure we consider the retry for every new code
        Unknown -> true
        UnknownOpCode -> true
        DecodeError -> true
        NotAuthenticated -> true
        AuthenticationFailed -> false
        AlreadyAuthenticated -> true
        InvalidSeq -> true
        RateLimited -> true
        SessionTimeout -> true
        InvalidShard -> false
        ShardingRequired -> false
        InvalidApiVersion -> false
        InvalidIntents -> false
        DisallowedIntents -> false
    }

internal val GatewayCloseCode.resetSession
    get() = when (this) { //this statement is intentionally structured to ensure we consider the reset for every new code
        Unknown -> false
        UnknownOpCode -> false
        DecodeError -> false
        NotAuthenticated -> false
        AuthenticationFailed -> false
        AlreadyAuthenticated -> false
        InvalidSeq -> true
        RateLimited -> false
        SessionTimeout -> false
        InvalidShard -> false
        ShardingRequired -> false
        InvalidApiVersion -> false
        InvalidIntents -> false
        DisallowedIntents -> false
    }
