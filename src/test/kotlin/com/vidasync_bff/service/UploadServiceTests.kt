package com.vidasync_bff.service

import com.vidasync_bff.client.SupabaseStorageClient
import com.vidasync_bff.dto.request.UploadPresignRequest
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class UploadServiceTests {

    private val storageClient = mock(SupabaseStorageClient::class.java)
    private val service = UploadService(
        storageClient = storageClient,
        pipelineBucket = "pipeline-inputs",
        signedUploadTtlSeconds = 900
    )

    @Test
    fun `deve criar upload presign com file key sanitizado e fallback de kind`() {
        `when`(storageClient.createSignedUploadUrl(anyString(), anyString(), anyInt()))
            .thenAnswer { invocation ->
                SupabaseStorageClient.SignedUploadResult(
                    uploadUrl = "https://upload.example.com/signed",
                    fileKey = invocation.getArgument(0),
                    expiresIn = invocation.getArgument(2)
                )
            }

        val response = service.createPresignedUpload(
            userId = "user-123",
            request = UploadPresignRequest(
                fileName = "Meu relatorio final",
                mimeType = "application/pdf",
                sizeBytes = 1024,
                kind = "desconhecido"
            )
        )

        assertEquals("https://upload.example.com/signed", response.uploadUrl)
        assertEquals(900, response.expiresIn)
        assertTrue(response.fileKey.startsWith("file/user-123/${LocalDate.now()}/"))
        assertTrue(response.fileKey.endsWith("_Meu_relatorio_final.pdf"))

        verify(storageClient).createSignedUploadUrl(response.fileKey, "pipeline-inputs", 900)
    }

    @Test
    fun `deve preservar kind suportado e extensao valida do nome do arquivo`() {
        `when`(storageClient.createSignedUploadUrl(anyString(), anyString(), anyInt()))
            .thenAnswer { invocation ->
                SupabaseStorageClient.SignedUploadResult(
                    uploadUrl = "https://upload.example.com/image",
                    fileKey = invocation.getArgument(0),
                    expiresIn = invocation.getArgument(2)
                )
            }

        val response = service.createPresignedUpload(
            userId = "user-abc",
            request = UploadPresignRequest(
                fileName = "foto.webp",
                mimeType = "image/jpeg",
                sizeBytes = 2048,
                kind = " IMAGE "
            )
        )

        assertTrue(response.fileKey.startsWith("image/user-abc/${LocalDate.now()}/"))
        assertTrue(response.fileKey.endsWith("_foto.webp"))
    }

    @Test
    fun `deve rejeitar size bytes fora do intervalo permitido`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            service.createPresignedUpload(
                userId = "user-123",
                request = UploadPresignRequest(
                    fileName = "arquivo.pdf",
                    mimeType = "application/pdf",
                    sizeBytes = 0,
                    kind = "pdf"
                )
            )
        }

        assertEquals("sizeBytes must be between 1 and 52428800", exception.message)
    }
}
