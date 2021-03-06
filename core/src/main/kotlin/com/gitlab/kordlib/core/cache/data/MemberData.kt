package com.gitlab.kordlib.core.cache.data

import com.gitlab.kordlib.cache.api.data.description
import com.gitlab.kordlib.common.entity.*
import com.gitlab.kordlib.common.entity.optional.Optional
import com.gitlab.kordlib.common.entity.optional.optional
import kotlinx.serialization.Serializable

private val MemberData.id get() = "$userId$guildId"

@Serializable
data class MemberData(
        val userId: Snowflake,
        val guildId: Snowflake,
        val nick: Optional<String?> = Optional.Missing(),
        val roles: List<Snowflake>,
        val joinedAt: String,
        val premiumSince: Optional<String?>,
) {

    companion object {
        val description = description(MemberData::id)

        fun from(userId: Snowflake, guildId: Snowflake, entity: DiscordGuildMember) = with(entity) {
            MemberData(userId = userId, guildId = guildId, nick, roles, joinedAt, premiumSince)
        }

        fun from(userId: Snowflake, entity: DiscordAddedGuildMember) = with(entity) {
            MemberData(userId = userId, guildId = guildId, nick, roles, joinedAt, premiumSince)
        }

        fun from(entity: DiscordUpdatedGuildMember) = with(entity){
            MemberData(userId = user.id, guildId = guildId, nick, roles, joinedAt, premiumSince)
        }

    }
}

fun DiscordGuildMember.toData(userId: Snowflake, guildId: Snowflake) = MemberData.from(userId, guildId, this)