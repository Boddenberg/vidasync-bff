package com.vidasync_bff.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InternalAdminUserCloneServiceTests {

    @Test
    fun `buildCloneUsername deve gerar valor alfanumerico com sufixo e limite de 30`() {
        val username = InternalAdminUserCloneService.buildCloneUsername(
            sourceUsername = "User.Com-Caracteres_!@#MuitoLongo1234567890",
            suffix = "ABC123"
        )

        assertTrue(username.length <= 30)
        assertTrue(username.all { it.isLetterOrDigit() })
        assertTrue(username.contains("clone"))
        assertTrue(username.endsWith("abc123"))
        assertEquals(username, username.lowercase())
    }
}
