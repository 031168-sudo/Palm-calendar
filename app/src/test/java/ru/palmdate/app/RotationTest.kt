package ru.palmdate.app

import android.content.ComponentName
import android.content.pm.ActivityInfo
import org.robolectric.RuntimeEnvironment
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * При повороте и раскладывании экран не должен пересоздаваться — иначе пропадут открытые окна
 * и набранный текст. Проверяем, что приложение само обрабатывает эти изменения.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RotationTest {
    @Test fun survivesRotationAndFolding() {
        val ctx = RuntimeEnvironment.getApplication()
        val info = ctx.packageManager.getActivityInfo(ComponentName(ctx, MainActivity::class.java), 0)
        val needed = mapOf(
            "поворот" to ActivityInfo.CONFIG_ORIENTATION,
            "размер окна" to ActivityInfo.CONFIG_SCREEN_SIZE,
            "раскладывание" to ActivityInfo.CONFIG_SMALLEST_SCREEN_SIZE,
            "вид экрана" to ActivityInfo.CONFIG_SCREEN_LAYOUT,
            "плотность" to ActivityInfo.CONFIG_DENSITY,
        )
        needed.forEach { (what, flag) -> assertTrue("Не обрабатывается: $what", info.configChanges and flag != 0) }
    }
}
