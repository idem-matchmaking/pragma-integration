package idem.client

data class IdemConfig(
    val apiUsername: String,
    val apiPassword: String,
    val gameModes: List<String>,
    val authEndpoint: String = "https://cognito-idp.eu-central-1.amazonaws.com",
    val wsEndpoint: String = "wss://ws-int.idem.gg",
    val apiEndpoint: String = "https://api-int.idem.gg",
)
