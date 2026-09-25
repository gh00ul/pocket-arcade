package com.pocketarcade

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.pocketarcade.data.ArcadeRepository
import com.pocketarcade.engine.AudioSynth
import com.pocketarcade.engine.Haptics

class MainActivity : ComponentActivity() {
    private lateinit var services: ArcadeServices
    private val signals = AppSignals()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()
        val audio = AudioSynth().also { it.start() }
        services = ArcadeServices(ArcadeRepository(applicationContext), audio, Haptics.from(applicationContext))
        signals.launchGame = intent?.getStringExtra(EXTRA_PLAY)
        setContent { ArcadeApp(services, signals) }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_PLAY)?.let { signals.launchGame = it }
    }

    companion object {
        /** Intent extra naming a machine id to walk straight into. */
        const val EXTRA_PLAY = "play"
    }

    override fun onResume() {
        super.onResume()
        signals.paused = false
        services.audio.setActive(true)
        hideSystemBars()
    }

    override fun onPause() {
        signals.paused = true
        services.audio.setActive(false)
        super.onPause()
    }

    override fun onDestroy() {
        services.audio.release()
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    /** Immersive full screen: bars stay hidden and peek in only on a swipe from the edge. */
    private fun hideSystemBars() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }
}
