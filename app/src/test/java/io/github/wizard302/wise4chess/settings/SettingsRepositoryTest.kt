package io.github.wizard302.wise4chess.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SettingsRepositoryTest {
    @Test
    fun settingsDefaultToDraggingOnAndRememberAChange() = runBlocking {
        val repository = SettingsRepository(ApplicationProvider.getApplicationContext<Context>())
        assertEquals(AppSettings(dragPieces = true, showEvaluation = false), repository.settings.first())
        repository.update(AppSettings(dragPieces = false, showEvaluation = true))
        assertEquals(true, repository.settings.first().showEvaluation)
        assertEquals(false, repository.settings.first().dragPieces)
        // A second repository over the same storage sees the stored value.
        assertEquals(false, SettingsRepository(ApplicationProvider.getApplicationContext<Context>()).settings.first().dragPieces)
        repository.update(AppSettings())
        assertEquals(AppSettings(), repository.settings.first())
    }
}
