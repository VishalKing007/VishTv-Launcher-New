package com.vishal.vishietv

import android.os.Looper
import android.view.View
import com.vishal.vishietv.data.AppRepository
import com.vishal.vishietv.data.TvApp
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-xhdpi")
class FocusProbeTest {

    @Test
    fun probeFocus() {
        AppRepository.overrideApps = listOf(
            TvApp("com.google.android.youtube.tv", ".MainActivity", "YouTube", null),
            TvApp("com.netflix.ninja", ".MainActivity", "Netflix", null)
        )
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        val decor = activity.window.decorView
        val w = View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY)
        val h = View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)
        decor.measure(w, h)
        decor.layout(0, 0, 1920, 1080)

        Robolectric.getForegroundThreadScheduler().advanceBy(500, TimeUnit.MILLISECONDS)
        shadowOf(Looper.getMainLooper()).idle()

        val tile = decor.findViewWithTag<View>("com.google.android.youtube.tv")
        println("PROBE tile found: ${tile != null}")
        println("PROBE tile focusable: ${tile?.isFocusable}, attached: ${tile?.isAttachedToWindow}")
        val result = tile?.requestFocus()
        println("PROBE requestFocus result: $result")
        Robolectric.getForegroundThreadScheduler().advanceBy(500, TimeUnit.MILLISECONDS)
        shadowOf(Looper.getMainLooper()).idle()
        println("PROBE tile hasFocus: ${tile?.hasFocus()}")
        println("PROBE tile isFocused: ${tile?.isFocused}")
        println("PROBE currentFocus: ${activity.currentFocus}")
        println("PROBE tile scaleX: ${tile?.scaleX}")
        println("PROBE decor hasWindowFocus: ${decor.hasWindowFocus()}")
        controller.close()
        AppRepository.overrideApps = null
    }
}
