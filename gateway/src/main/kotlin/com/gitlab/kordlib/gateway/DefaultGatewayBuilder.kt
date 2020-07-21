package com.gitlab.kordlib.gateway

import com.gitlab.kordlib.common.ratelimit.BucketRateLimiter
import com.gitlab.kordlib.common.ratelimit.RateLimiter
import com.gitlab.kordlib.gateway.retry.LinearRetry
import com.gitlab.kordlib.gateway.retry.Retry
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.json.serializer.KotlinxSerializer
import io.ktor.client.features.websocket.WebSockets
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import kotlin.time.seconds

class DefaultGatewayBuilder {
    var url = "wss://gateway.discord.gg/?v=6&encoding=json&compress=zlib-stream"
    var connectionProvider: GatewayConnectionProvider? = null
    var reconnectRetry: Retry? = null
    var sendRateLimiter: RateLimiter? = null
    var identifyRateLimiter: RateLimiter? = null


    fun build(): DefaultGateway {
        val provider = connectionProvider ?: HttpGatewayConnectionProvider(HttpClient(CIO) {
            install(WebSockets)
            install(JsonFeature) {
                serializer = KotlinxSerializer(Json(JsonConfiguration.Default))
            }
        })
        val retry = reconnectRetry ?: LinearRetry(2.seconds, 20.seconds, 10)
        val sendRateLimiter = sendRateLimiter ?: BucketRateLimiter(120, 60.seconds)
        val identifyRateLimiter = identifyRateLimiter ?: BucketRateLimiter(1, 5.seconds)

        return DefaultGateway(DefaultGatewayData(url, provider, retry, sendRateLimiter, identifyRateLimiter))
    }

}