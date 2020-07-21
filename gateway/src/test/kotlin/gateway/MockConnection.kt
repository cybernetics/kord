package gateway

import com.gitlab.kordlib.gateway.*
import io.ktor.http.cio.websocket.CloseReason
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.serialization.*
import kotlinx.serialization.json.Json

class MockConnectionSupplier : GatewayConnectionProvider {

    val connection = MockConnection()

    override suspend fun provide(url: String): GatewayConnection =
            connection.also { it.state = GatewayConnection.State.Running }

}


class MockConnection : GatewayConnection {

    override val closeReason: CompletableDeferred<CloseReason?> = CompletableDeferred()

    val incomingChannel = Channel<ByteArray>(Channel.CONFLATED)

    override val incoming: Flow<ByteArray> = incomingChannel.receiveAsFlow()

    override var state: GatewayConnection.State = GatewayConnection.State.Running

    override suspend fun close(closeReason: CloseReason?) {
        state = GatewayConnection.State.Closed
        incomingChannel.close()
        incomingChannel.cancel()
        outgoing.close()
        this.closeReason.complete(closeReason)
    }

    val outgoing = Channel<Command>(Channel.UNLIMITED)

    override suspend fun send(serializer: SerializationStrategy<Command>, command: Command) {
        outgoing.send(command)
    }

    suspend fun <T> sendEvent(opCode: OpCode, serializer: SerializationStrategy<T>, event: T) {
        val bytes = Json.stringify(DiscordEventSerializer(serializer, opCode), event).toUtf8Bytes()
        incomingChannel.send(bytes)
    }

    private class DiscordEventSerializer<T>(
        private val serializer: SerializationStrategy<T>,
        private val opCode: OpCode
    ) : SerializationStrategy<T> {

        override val descriptor: SerialDescriptor = SerialDescriptor("event") {
            element("op", OpCode.descriptor)
            element("d", Event.descriptor)
        }

        override fun serialize(encoder: Encoder, value: T) {
            encoder.beginStructure(descriptor).apply {
                encodeSerializableElement(descriptor, 0, OpCode.OpCodeSerializer, opCode)
                encodeSerializableElement(descriptor, 1, serializer, value)
            }.endStructure(descriptor)
        }

    }

}