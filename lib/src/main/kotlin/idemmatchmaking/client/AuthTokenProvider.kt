package idemmatchmaking.client

import com.fasterxml.jackson.databind.JsonNode
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.headers

class AuthTokenProvider(
    private val config: IdemConfig,
    private val client: HttpClient
) {
    suspend fun getAuthToken(): String {
        val payload = client.post(config.authEndpoint) {
            headers {
                append("X-Amz-Target", "AWSCognitoIdentityProviderService.InitiateAuth")
                append("Content-Type", "application/x-amz-json-1.1")
            }
            setBody(mapOf(
                "AuthParameters" to mapOf(
                    "USERNAME" to config.apiUsername,
                    "PASSWORD" to config.apiPassword,
                ),
                "AuthFlow" to "USER_PASSWORD_AUTH",
                "ClientId" to "3b7bo4gjuqsjuer6eatjsgo58u"
            ))
        }.body<JsonNode>()
        return try {
            payload["AuthenticationResult"]["IdToken"].asText()
        } catch (e: Exception) {
            throw RuntimeException("Failed to authenticate with IDEM API: $payload")
        }
    }
}