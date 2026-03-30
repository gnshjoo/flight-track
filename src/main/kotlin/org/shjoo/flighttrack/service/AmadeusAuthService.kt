package org.shjoo.flighttrack.service

import org.shjoo.flighttrack.config.AmadeusConfig
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodyOrNull
import java.util.concurrent.atomic.AtomicReference

@Service
class AmadeusAuthService(
    private val amadeusConfig: AmadeusConfig,
    private val amadeusWebClient: WebClient
) {
    private val log = LoggerFactory.getLogger(AmadeusAuthService::class.java)

    private data class TokenInfo(
        val accessToken: String,
        val expiresAt: Long
    )

    private val tokenRef = AtomicReference<TokenInfo?>(null)

    suspend fun getAccessToken(): String {
        val current = tokenRef.get()
        if (current != null && System.currentTimeMillis() < current.expiresAt - 60_000) {
            return current.accessToken
        }
        return refreshToken()
    }

    private suspend fun refreshToken(): String {
        log.info("Refreshing Amadeus access token")
        val response = amadeusWebClient.post()
            .uri("/v1/security/oauth2/token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(
                BodyInserters.fromFormData("grant_type", "client_credentials")
                    .with("client_id", amadeusConfig.clientId)
                    .with("client_secret", amadeusConfig.clientSecret)
            )
            .retrieve()
            .awaitBodyOrNull<AmadeusTokenResponse>()
            ?: throw RuntimeException("Failed to obtain Amadeus token")

        val tokenInfo = TokenInfo(
            accessToken = response.accessToken,
            expiresAt = System.currentTimeMillis() + (response.expiresIn * 1000L)
        )
        tokenRef.set(tokenInfo)
        log.info("Amadeus token refreshed, expires in ${response.expiresIn}s")
        return tokenInfo.accessToken
    }
}

data class AmadeusTokenResponse(
    val access_token: String = "",
    val token_type: String = "",
    val expires_in: Int = 0
) {
    val accessToken: String get() = access_token
    val expiresIn: Int get() = expires_in
}
