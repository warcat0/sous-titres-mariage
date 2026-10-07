package com.mariage.soustitres

import android.app.Activity
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.VideoView

/** Lit la vidéo de fond en plein écran, en boucle. Les sous-titres s'affichent par-dessus. */
class PlayerActivity : Activity() {

    private lateinit var video: VideoView
    private var prepared = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        video = VideoView(this)
        root.addView(video, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER
        ))
        setContentView(root)

        val uri = Prefs.videoUri(this)
        if (uri == null) {
            root.addView(TextView(this).apply {
                text = "Aucune vidéo choisie (écran noir).\nRetour pour revenir aux réglages."
                setTextColor(Color.GRAY)
                gravity = Gravity.CENTER
            }, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP
            ))
            return
        }
        video.setOnPreparedListener { mp ->
            prepared = true
            mp.isLooping = true
            if (!Prefs.videoSound(this)) mp.setVolume(0f, 0f)
            video.start()
        }
        video.setOnErrorListener { _, _, _ ->
            root.addView(TextView(this).apply {
                text = "Impossible de lire cette vidéo. Choisissez-en une autre (MP4 conseillé)."
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
            })
            true
        }
        video.setVideoURI(Uri.parse(uri))
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        if (prepared) video.start()
    }

    override fun onPause() {
        super.onPause()
        if (prepared) video.pause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    @Suppress("DEPRECATION")
    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
        }
    }
}
