package com.vidasync_bff.controller

import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class HealthControllerTests {

    private val mockMvc = MockMvcBuilders.standaloneSetup(HealthController()).build()

    @Test
    fun `deve responder status up no endpoint health`() {
        mockMvc.get("/health")
            .andExpect {
                status { isOk() }
                content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
                jsonPath("$.status") { value("UP") }
            }
    }
}
