package com.vidasync_bff.client

import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class SupabaseClient(private val supabaseRestClient: RestClient) {

    fun <T> get(table: String, queryParams: Map<String, String> = emptyMap(), typeRef: ParameterizedTypeReference<T>): T? {
        return supabaseRestClient.get()
            .uri { uriBuilder ->
                uriBuilder.path("/$table")
                    .queryParam("select", "*")
                    .also { queryParams.forEach { (key, value) -> uriBuilder.queryParam(key, value) } }
                    .build()
            }
            .retrieve()
            .body(typeRef)
    }

    fun <T> post(table: String, body: Any, typeRef: ParameterizedTypeReference<T>): T? {
        return supabaseRestClient.post()
            .uri { it.path("/$table").build() }
            .body(body)
            .retrieve()
            .body(typeRef)
    }

    fun <T> patch(table: String, queryParams: Map<String, String>, body: Any, typeRef: ParameterizedTypeReference<T>): T? {
        return supabaseRestClient.patch()
            .uri { uriBuilder ->
                uriBuilder.path("/$table")
                    .also { queryParams.forEach { (key, value) -> uriBuilder.queryParam(key, value) } }
                    .build()
            }
            .body(body)
            .retrieve()
            .body(typeRef)
    }

    fun delete(table: String, queryParams: Map<String, String>) {
        supabaseRestClient.delete()
            .uri { uriBuilder ->
                uriBuilder.path("/$table")
                    .also { queryParams.forEach { (key, value) -> uriBuilder.queryParam(key, value) } }
                    .build()
            }
            .retrieve()
            .toBodilessEntity()
    }
}
