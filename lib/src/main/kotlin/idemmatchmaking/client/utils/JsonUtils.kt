package idemmatchmaking.client.utils

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

fun ObjectMapper.configureJackson() {
    registerModule(JavaTimeModule())
    disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    enable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN)
    setSerializationInclusion(JsonInclude.Include.NON_NULL)
}

object JsonUtils {
    private val objectMapper = jacksonObjectMapper().apply {
        configureJackson()
    }

    fun <T> toJson(obj: T): String {
        return objectMapper.writeValueAsString(obj)
    }

    fun <T> fromJson(json: String, clazz: Class<T>): T {
        return objectMapper.readValue(json, clazz)
    }

    fun <T> fromJson(json: String, valueTypeRef: TypeReference<T>): T {
        return objectMapper.readValue(json, valueTypeRef)
    }

    inline fun <reified T> fromJson(json: String): T {
        return fromJson(json, T::class.java)
    }

    fun <T> fromAny(obj: Any, clazz: Class<T>): T {
        return objectMapper.readValue(objectMapper.writeValueAsString(obj), clazz)
    }

    inline fun <reified T> fromAny(obj: Any): T {
        return fromAny(obj, T::class.java)
    }

    fun <T> fromJsonNode(node: JsonNode, clazz: Class<T>): T {
        return objectMapper.treeToValue(node, clazz)
    }

    inline fun <reified T> fromJsonNode(node: JsonNode): T {
        return fromJsonNode(node, T::class.java)
    }
}