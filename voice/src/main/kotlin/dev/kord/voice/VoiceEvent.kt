package dev.kord.voice

import kotlinx.serialization.*
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.json.JsonElementSerializer
import kotlinx.serialization.json.JsonObject
import mu.KotlinLogging

val jsonLogger = KotlinLogging.logger { }

sealed class VoiceEvent {
    companion object : DeserializationStrategy<VoiceEvent?> {
        override val descriptor: SerialDescriptor = SerialDescriptor("VoiceEvent") {
            element("op", VoiceOpCode.descriptor)
            element("d", JsonObject.serializer().descriptor, isOptional = true)
        }

        override fun deserialize(decoder: Decoder): VoiceEvent? {
            var op: VoiceOpCode? = null
            var data: VoiceEvent? = null
            with(decoder.beginStructure(descriptor)) {
                loop@ while (true) {
                    when (val index = decodeElementIndex(descriptor)) {//we assume the all fields to be present *before* the data field
                        CompositeDecoder.READ_DONE -> break@loop
                        0 -> {
                            op = VoiceOpCode.deserialize(decoder)
                        }
                        1 -> data = when (op) {
                            VoiceOpCode.Hello -> decodeSerializableElement(descriptor, index, VoiceHello.serializer())
                            VoiceOpCode.Ready -> decodeSerializableElement(descriptor, index, VoiceReady.serializer())
                            VoiceOpCode.Identify -> decodeSerializableElement(descriptor, index, VoiceIdentify.serializer())
                            VoiceOpCode.Heartbeat -> decodeSerializableElement(descriptor, index, VoiceHeartbeat.serializer())
                            VoiceOpCode.HearbeatACK -> decodeSerializableElement(descriptor, index, VoiceHeartbeatACK.serializer())
                            VoiceOpCode.SelectProtocol -> decodeSerializableElement(descriptor, index, SelectProtocol.serializer())
                            VoiceOpCode.SessionDescription -> decodeSerializableElement(descriptor, index, SessionDescription.serializer())
                            VoiceOpCode.Speaking -> decodeSerializableElement(descriptor, index, Speak.serializer())
                            VoiceOpCode.Resumed -> {
                                decoder.decodeNull()
                                Resumed
                            }

                            //some events contain undocumented data fields, we'll only assume an unknown opcode with no data to be an error
                            else -> if (data == null) {
                                val element = decodeNullableSerializableElement(descriptor, index, JsonElementSerializer.nullable)
                                error("Unknown 'd' field for Op code ${op?.code}: $element")
                            } else {
                                val element = decodeNullableSerializableElement(descriptor, index, JsonElementSerializer.nullable)
                                jsonLogger.warn { "Ignored unexpected 'd' field for Op code ${op?.code}: $element" }
                                data
                            }
                        }
                    }
                }
                endStructure(descriptor)
                return data
            }
        }

        override fun patch(decoder: Decoder, old: VoiceEvent?): VoiceEvent? = error("")

    }
}

@Serializable
class VoiceReady(
        val ssrc: Int,
        val ip: String,
        val port: Int,
        val modes: List<String>,
        @SerialName("heartbeat_interval")
        val heartbeat: Int
) : VoiceEvent()

@Serializable
data class VoiceIdentify(
        @SerialName("server_id")
        val serverId: String,
        @SerialName("user_id")
        val userId: String,
        @SerialName("session_id")
        val sessionId: String,
        val token: String
) : VoiceEvent()

@Serializable
data class VoiceHello(@SerialName("heartbeat_interval") val heartbeatInterval: Long) : VoiceEvent()

@Serializable
data class VoiceHeartbeatACK(val data: Long) : VoiceEvent() {
    @Serializer(VoiceHeartbeatACK::class)
    companion object : DeserializationStrategy<VoiceHeartbeatACK> {
        override val descriptor: SerialDescriptor
            get() = PrimitiveDescriptor("VoiceHeartbeatACKEvent", PrimitiveKind.LONG)

        override fun deserialize(decoder: Decoder) = VoiceHeartbeatACK(decoder.decodeLong())
    }
}

@Serializable
data class VoiceHeartbeat(val data: Long) : VoiceEvent() {
    @Serializer(VoiceHeartbeat::class)
    companion object : DeserializationStrategy<VoiceHeartbeat> {
        override val descriptor: SerialDescriptor
            get() = PrimitiveDescriptor("VoiceHeartbeatEvent", PrimitiveKind.LONG)

        override fun deserialize(decoder: Decoder) = VoiceHeartbeat(decoder.decodeLong())
    }
}


@Serializable
data class SessionDescription(
        val mode: String,
        @SerialName("secret_key")
        val secretKey: List<Int>
) : VoiceEvent()

@Serializable
data class SelectProtocol(val protocol: String, val data: SelectProtocolData) : VoiceEvent()

@Serializable
data class SelectProtocolData(
        val address: String,
        val port: Int,
        val mode: String
)

@Serializable
data class Speak(
        val speaking: Int,
        val delay: Int,
        val ssrc: Int
) : VoiceEvent()


object Resumed : VoiceEvent()
