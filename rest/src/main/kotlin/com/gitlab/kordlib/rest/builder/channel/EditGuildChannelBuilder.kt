package com.gitlab.kordlib.rest.builder.channel

import com.gitlab.kordlib.common.entity.Overwrite
import com.gitlab.kordlib.common.entity.Snowflake
import com.gitlab.kordlib.rest.builder.AuditRequestBuilder
import com.gitlab.kordlib.common.annotation.KordDsl
import com.gitlab.kordlib.common.entity.optional.Optional
import com.gitlab.kordlib.common.entity.optional.OptionalBoolean
import com.gitlab.kordlib.common.entity.optional.OptionalInt
import com.gitlab.kordlib.common.entity.optional.OptionalSnowflake
import com.gitlab.kordlib.common.entity.optional.delegate.delegate
import com.gitlab.kordlib.rest.json.request.ChannelModifyPatchRequest

@KordDsl
class TextChannelModifyBuilder : AuditRequestBuilder<ChannelModifyPatchRequest> {
    override var reason: String? = null

    private var _name: Optional<String> = Optional.Missing()
    var name: String? by ::_name.delegate()

    private var _position: OptionalInt = OptionalInt.Missing
    var position: Int? by ::_position.delegate()

    private var _topic: Optional<String> = Optional.Missing()
    var topic: String? by ::_topic.delegate()

    private var _nsfw: OptionalBoolean = OptionalBoolean.Missing
    var nsfw: Boolean? by ::_nsfw.delegate()

    private var _parentId: OptionalSnowflake = OptionalSnowflake.Missing
    var parentId: Snowflake? by ::_parentId.delegate()

    private var _rateLimitPerUser: OptionalInt = OptionalInt.Missing
    var rateLimitPerUser: Int? by ::_rateLimitPerUser.delegate()

    var permissionOverwrites: MutableSet<Overwrite> = mutableSetOf()

    override fun toRequest(): ChannelModifyPatchRequest = ChannelModifyPatchRequest(
            name = _name,
            position = _position,
            topic = _topic,
            nsfw = _nsfw,
            rateLimitPerUser = _rateLimitPerUser,
            permissionOverwrites = Optional.missingOnEmpty(permissionOverwrites),
            parentId = _parentId
    )

}

@KordDsl
class VoiceChannelModifyBuilder : AuditRequestBuilder<ChannelModifyPatchRequest> {
    override var reason: String? = null

    private var _name: Optional<String> = Optional.Missing()
    var name: String? by ::_name.delegate()

    private var _position: OptionalInt = OptionalInt.Missing
    var position: Int? by ::_position.delegate()

    private var _topic: Optional<String> = Optional.Missing()
    var topic: String? by ::_topic.delegate()

    private var _parentId: OptionalSnowflake = OptionalSnowflake.Missing
    var parentId: Snowflake? by ::_parentId.delegate()

    var permissionOverwrites: MutableSet<Overwrite> = mutableSetOf()

    private var _bitrate: OptionalInt = OptionalInt.Missing
    var bitrate: Int? by ::_bitrate.delegate()

    private var _userLimit: OptionalInt = OptionalInt.Missing
    var userLimit: Int? by ::_userLimit.delegate()

    override fun toRequest(): ChannelModifyPatchRequest = ChannelModifyPatchRequest(
            name = _name,
            position = _position,
            parentId = _parentId,
            bitrate = _bitrate,
            userLimit = _userLimit,
            topic = _topic,
            permissionOverwrites = Optional.missingOnEmpty(permissionOverwrites)
    )

}

@KordDsl
class NewsChannelModifyBuilder : AuditRequestBuilder<ChannelModifyPatchRequest> {
    override var reason: String? = null

    private var _name: Optional<String> = Optional.Missing()
    var name: String? by ::_name.delegate()

    private var _position: OptionalInt = OptionalInt.Missing
    var position: Int? by ::_position.delegate()

    private var _topic: Optional<String> = Optional.Missing()
    var topic: String? by ::_topic.delegate()

    private var _nsfw: OptionalBoolean = OptionalBoolean.Missing
    var nsfw: Boolean? by ::_nsfw.delegate()

    private var _parentId: OptionalSnowflake = OptionalSnowflake.Missing
    var parentId: Snowflake? by ::_parentId.delegate()

    private var _rateLimitPerUser: OptionalInt = OptionalInt.Missing
    var rateLimitPerUser: Int? by ::_rateLimitPerUser.delegate()

    var permissionOverwrites: MutableSet<Overwrite> = mutableSetOf()

    override fun toRequest(): ChannelModifyPatchRequest = ChannelModifyPatchRequest(
            name = _name,
            position = _position,
            topic = _topic,
            nsfw = _nsfw,
            permissionOverwrites = Optional.missingOnEmpty(permissionOverwrites),
            parentId = _parentId
    )
}

@KordDsl
class StoreChannelModifyBuilder : AuditRequestBuilder<ChannelModifyPatchRequest> {
    override var reason: String? = null

    private var _name: Optional<String> = Optional.Missing()
    var name: String? by ::_name.delegate()

    private var _position: OptionalInt = OptionalInt.Missing
    var position: Int? by ::_position.delegate()

    var permissionOverwrites: MutableSet<Overwrite> = mutableSetOf()

    override fun toRequest(): ChannelModifyPatchRequest = ChannelModifyPatchRequest(
            name = _name,
            position = _position,
            permissionOverwrites = Optional.missingOnEmpty(permissionOverwrites)
    )

}