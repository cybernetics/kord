package com.gitlab.kordlib.rest.json.request

import com.gitlab.kordlib.common.Color
import com.gitlab.kordlib.common.entity.DiscordMessageReference
import com.gitlab.kordlib.common.entity.Snowflake
import com.gitlab.kordlib.common.entity.UserFlags
import com.gitlab.kordlib.common.entity.optional.Optional
import com.gitlab.kordlib.common.entity.optional.OptionalBoolean
import com.gitlab.kordlib.common.entity.optional.OptionalInt
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable
data class MessageCreateRequest(
        val content: Optional<String> = Optional.Missing(),
        val nonce: Optional<String> = Optional.Missing(),
        val tts: OptionalBoolean = OptionalBoolean.Missing,
        val embed: Optional<EmbedRequest> = Optional.Missing(),
        @SerialName("allowed_mentions")
        val allowedMentions: Optional<AllowedMentions> = Optional.Missing(),
        @SerialName("message_reference")
        val messageReference: Optional<DiscordMessageReference> = Optional.Missing()
)

@Serializable
data class AllowedMentions(
        val parse: List<AllowedMentionType>,
        val users: List<String>,
        val roles: List<String>,
        @SerialName("replied_user")
        val repliedUser: OptionalBoolean = OptionalBoolean.Missing
)

@Serializable(with = AllowedMentionType.Serializer::class)
sealed class AllowedMentionType(val value: String) {
    class Unknown(value: String) : AllowedMentionType(value)
    object RoleMentions : AllowedMentionType("roles")
    object UserMentions : AllowedMentionType("users")
    object EveryoneMentions : AllowedMentionType("everyone")

    internal class Serializer : KSerializer<AllowedMentionType> {
            override val descriptor: SerialDescriptor
                    get() = PrimitiveSerialDescriptor("Kord.DiscordAllowedMentionType", PrimitiveKind.STRING)

            override fun deserialize(decoder: Decoder): AllowedMentionType = when(val value = decoder.decodeString()) {
                    "roles" -> RoleMentions
                    "users" -> UserMentions
                    "everyone" -> EveryoneMentions
                    else -> Unknown(value)
            }

            override fun serialize(encoder: Encoder, value: AllowedMentionType) {
                    encoder.encodeString(value.value)
            }
    }
}

data class MultipartMessageCreateRequest(
        val request: MessageCreateRequest,
        val files: List<Pair<String, java.io.InputStream>> = emptyList(),
)

@Serializable
data class EmbedRequest(
        val title: Optional<String> = Optional.Missing(),
        val type: Optional<String> = Optional.Missing(),
        val description: Optional<String> = Optional.Missing(),
        val url: Optional<String> = Optional.Missing(),
        val timestamp: Optional<String> = Optional.Missing(),
        val color: Optional<Color> = Optional.Missing(),
        val footer: Optional<EmbedFooterRequest> = Optional.Missing(),
        val image: Optional<EmbedImageRequest> = Optional.Missing(),
        val thumbnail: Optional<EmbedThumbnailRequest> = Optional.Missing(),
        val author: Optional<EmbedAuthorRequest> = Optional.Missing(),
        val fields: Optional<List<EmbedFieldRequest>> = Optional.Missing(),
)


@Serializable
data class EmbedFooterRequest(
        val text: String,
        @SerialName("icon_url")
        val iconUrl: String? = null,
)

@Serializable
data class EmbedImageRequest(val url: String)

@Serializable
data class EmbedThumbnailRequest(val url: String)

@Serializable
data class EmbedAuthorRequest(
        val name: Optional<String> = Optional.Missing(),
        val url: Optional<String> = Optional.Missing(),
        @SerialName("icon_url")
        val iconUrl: Optional<String> = Optional.Missing(),
)

@Serializable
data class EmbedFieldRequest(
        val name: String,
        val value: String,
        val inline: OptionalBoolean = OptionalBoolean.Missing,
)

@Serializable
data class MessageEditPatchRequest(
        val content: Optional<String?> = Optional.Missing(),
        val embed: Optional<EmbedRequest?> = Optional.Missing(),
        val flags: Optional<UserFlags?> = Optional.Missing(),
        @SerialName("allowed_mentions")
        val allowedMentions: Optional<AllowedMentions?> = Optional.Missing(),
)

@Serializable
data class BulkDeleteRequest(val messages: List<Snowflake>)
