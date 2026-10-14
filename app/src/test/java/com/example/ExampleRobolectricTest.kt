package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.storage.TokenStorage
import com.example.data.zip.ZipProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read app name string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("GitCompile", appName)
    }

    @Test
    fun `token storage stores and clears token correctly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = TokenStorage(context)
        storage.token = "ghp_test_token_123"
        assertEquals("ghp_test_token_123", storage.token)
        storage.clear()
        assertEquals("", storage.token)
    }

    @Test
    fun `compilation database stores and retrieves records`() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = com.example.data.db.AppDatabase.getInstance(context)
        val dao = db.compilationDao()

        val entity = com.example.data.db.CompilationEntity(
            fileName = "teste_app.zip",
            status = "SUCCESS",
            repoFullName = "user/repo",
            branch = "main",
            runId = 12345L,
            runNumber = 1
        )
        val id = dao.insertCompilation(entity)
        val loaded = dao.getCompilationById(id)
        org.junit.Assert.assertNotNull(loaded)
        assertEquals("teste_app.zip", loaded?.fileName)
        assertEquals("SUCCESS", loaded?.status)
    }

    @Test
    fun `notification helper initializes channels and updates progress without crashing`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val helper = com.example.ui.notification.CompilationNotificationHelper(context)
        helper.updateProgress("projeto.zip", 1, "Preparação")
        helper.updateProgress("projeto.zip", 2, "Envio")
        helper.updateProgress("projeto.zip", 3, "Compilação")
        helper.updateProgress("projeto.zip", 4, "Finalização")
        helper.notifySuccess("projeto.zip", "APK")
        helper.cancelAll()
    }

    @Test
    fun `deduplicateAndroidResources removes conflicting webp and png files`() {
        val files = listOf(
            com.example.data.zip.ExtractedFile("app/src/main/res/mipmap-mdpi/ic_launcher.png", byteArrayOf(1), false),
            com.example.data.zip.ExtractedFile("app/src/main/res/mipmap-mdpi/ic_launcher.webp", byteArrayOf(2), false),
            com.example.data.zip.ExtractedFile("app/src/main/res/mipmap-mdpi/ic_launcher_round.png", byteArrayOf(3), false),
            com.example.data.zip.ExtractedFile("app/src/main/res/mipmap-mdpi/ic_launcher_round.webp", byteArrayOf(4), false),
            com.example.data.zip.ExtractedFile("app/src/main/res/values/strings.xml", byteArrayOf(5), true)
        )
        val (deduped, removed) = ZipProcessor.deduplicateAndroidResources(files)
        assertEquals(3, deduped.size)
        assertEquals(2, removed.size)
        assertTrue(deduped.any { it.path.endsWith("ic_launcher.png") })
        assertTrue(deduped.none { it.path.endsWith("ic_launcher.webp") })
        assertTrue(deduped.any { it.path.endsWith("ic_launcher_round.png") })
        assertTrue(deduped.none { it.path.endsWith("ic_launcher_round.webp") })
        assertTrue(deduped.any { it.path.endsWith("strings.xml") })
    }
}

