package com.mariage.soustitres

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Service au premier plan :
 *  - écoute le micro en continu (reconnaissance vocale Android, en français),
 *  - traduit en anglais et en espagnol (ML Kit),
 *  - affiche les sous-titres dans une fenêtre flottante par-dessus tout le reste.
 */
class SubtitleService : Service() {

    companion object {
        const val ACTION_START = "com.mariage.soustitres.START"
        const val ACTION_STOP = "com.mariage.soustitres.STOP"
        const val ACTION_TOGGLE = "com.mariage.soustitres.TOGGLE"
        private const val CHANNEL = "soustitres"
        private const val NOTIF_ID = 42
        private const val MAX_CHARS = 170

        @Volatile
        var running = false
            private set
    }

    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private lateinit var wm: WindowManager
    private var container: FrameLayout? = null
    private var panel: LinearLayout? = null
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var tvFr: TextView
    private lateinit var tvEn: TextView
    private lateinit var tvEs: TextView
    private lateinit var tvStatus: TextView

    private var paused = false
    private var lastPartial = ""
    private var seq = 0
    private val appliedSeq = intArrayOf(-1, -1)
    private val inFlight = intArrayOf(0, 0)
    private val pending = arrayOfNulls<Pair<String, Int>>(2)
    private val mutedStreams = mutableListOf<Int>()
    private var restartRecreate = false

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> applyPrefs() }
    private val restartRunnable = Runnable { if (restartRecreate) createRecognizer() else listen() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE -> {
                if (running) togglePause()
                return START_NOT_STICKY
            }
        }
        if (!running) {
            running = true
            startInForeground()
            createOverlay()
            muteBeeps()
            Prefs.get(this).registerOnSharedPreferenceChangeListener(prefListener)
            Translators.download { ok, msg ->
                if (!ok) setStatus("Modèles de traduction non téléchargés : $msg")
            }
            createRecognizer()
        } else {
            applyPrefs()
        }
        return START_NOT_STICKY
    }

    // ---------------------------------------------------------------- Notification

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Sous-titres en direct", NotificationManager.IMPORTANCE_LOW)
        )
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val openApp = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), flags)
        val toggle = PendingIntent.getService(
            this, 1, Intent(this, SubtitleService::class.java).setAction(ACTION_TOGGLE), flags
        )
        val stop = PendingIntent.getService(
            this, 2, Intent(this, SubtitleService::class.java).setAction(ACTION_STOP), flags
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(if (paused) "Sous-titres en pause" else "Sous-titres actifs (FR → EN / ES)")
            .setContentText("Touchez pour ouvrir les réglages")
            .setContentIntent(openApp)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_media_pause),
                    if (paused) "Reprendre" else "Pause", toggle
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_delete), "Arrêter", stop
                ).build()
            )
            .build()
    }

    private fun startInForeground() {
        val n = buildNotification()
        if (Build.VERSION.SDK_INT >= 30) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun refreshNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification())
    }

    // ---------------------------------------------------------------- Overlay

    private fun makeText(color: Int, bold: Boolean): TextView = TextView(this).apply {
        setTextColor(color)
        setShadowLayer(8f, 2f, 2f, Color.BLACK)
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        gravity = Gravity.CENTER
        setLineSpacing(0f, 1.05f)
    }

    private fun createOverlay() {
        wm = getSystemService(WindowManager::class.java)

        tvFr = makeText(Color.rgb(220, 220, 220), false)
        tvEn = makeText(Color.WHITE, true)
        tvEs = makeText(Color.rgb(255, 221, 120), true)
        tvStatus = makeText(Color.rgb(255, 140, 140), false).apply { visibility = View.GONE }

        val p = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(10), dp(18), dp(10))
            addView(tvFr)
            addView(tvEn)
            addView(tvEs)
            addView(tvStatus)
        }
        panel = p
        val c = FrameLayout(this).apply {
            setPadding(dp(16), 0, dp(16), 0)
            addView(p, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT
            ))
        }
        container = c

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        )
        params.y = dp(28)

        // Le bandeau peut être déplacé verticalement avec le doigt.
        var startY = 0
        var touchY = 0f
        c.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    startY = params.y
                    touchY = e.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = (e.rawY - touchY).toInt()
                    params.y = (if (Prefs.positionTop(this)) startY + dy else startY - dy).coerceAtLeast(0)
                    try { wm.updateViewLayout(c, params) } catch (_: Exception) {}
                }
            }
            true
        }

        applyPrefs(addView = true)
        tvEn.text = "🇬🇧  …"
        tvEs.text = "🇪🇸  …"
        tvFr.text = "🇫🇷  En écoute…"
    }

    private fun applyPrefs(addView: Boolean = false) {
        val c = container ?: return
        val size = Prefs.textSize(this).toFloat()
        tvEn.setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        tvEs.setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        tvFr.setTextSize(TypedValue.COMPLEX_UNIT_SP, size * 0.75f)
        tvStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        tvFr.visibility = if (Prefs.showFrench(this)) View.VISIBLE else View.GONE

        val alpha = (Prefs.bgAlpha(this) * 255 / 100).coerceIn(0, 255)
        panel?.background = GradientDrawable().apply {
            setColor(Color.argb(alpha, 0, 0, 0))
            cornerRadius = dp(16).toFloat()
        }
        params.gravity = (if (Prefs.positionTop(this)) Gravity.TOP else Gravity.BOTTOM) or Gravity.CENTER_HORIZONTAL
        try {
            if (addView) wm.addView(c, params) else wm.updateViewLayout(c, params)
        } catch (_: Exception) {
        }
    }

    private fun setStatus(s: String) {
        if (!running) return
        tvStatus.text = s
        tvStatus.visibility = if (s.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun togglePause() {
        paused = !paused
        if (paused) {
            handler.removeCallbacks(restartRunnable)
            try { recognizer?.cancel() } catch (_: Exception) {}
            container?.visibility = View.GONE
        } else {
            container?.visibility = View.VISIBLE
            createRecognizer()
        }
        refreshNotification()
    }

    // ---------------------------------------------------------------- Bips du micro

    private fun muteBeeps() {
        if (!Prefs.muteBeeps(this)) return
        val am = getSystemService(AudioManager::class.java)
        for (s in listOf(AudioManager.STREAM_NOTIFICATION, AudioManager.STREAM_SYSTEM, AudioManager.STREAM_MUSIC)) {
            try {
                am.adjustStreamVolume(s, AudioManager.ADJUST_MUTE, 0)
                mutedStreams.add(s)
            } catch (_: Exception) {
            }
        }
    }

    private fun unmuteBeeps() {
        val am = getSystemService(AudioManager::class.java)
        for (s in mutedStreams) {
            try { am.adjustStreamVolume(s, AudioManager.ADJUST_UNMUTE, 0) } catch (_: Exception) {}
        }
        mutedStreams.clear()
    }

    // ---------------------------------------------------------------- Reconnaissance vocale

    private fun recognizerIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "fr-FR")
        putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, true)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 15000L)
    }

    private fun createRecognizer() {
        if (!running || paused) return
        try { recognizer?.destroy() } catch (_: Exception) {}
        recognizer = null
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            setStatus("Reconnaissance vocale indisponible : installez / mettez à jour l'appli « Google ».")
            scheduleRestart(5000, true)
            return
        }
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).also {
            it.setRecognitionListener(listener)
        }
        listen()
    }

    private fun listen() {
        if (!running || paused) return
        val r = recognizer
        if (r == null) {
            scheduleRestart(300, true)
            return
        }
        try {
            r.startListening(recognizerIntent())
        } catch (_: Exception) {
            scheduleRestart(500, true)
        }
    }

    private fun scheduleRestart(delayMs: Long, recreate: Boolean) {
        if (!running || paused) return
        handler.removeCallbacks(restartRunnable)
        restartRecreate = recreate
        handler.postDelayed(restartRunnable, delayMs)
    }

    private fun firstResult(b: Bundle?): String? =
        b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) { setStatus("") }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onPartialResults(partialResults: Bundle?) {
            firstResult(partialResults)?.let { onText(it, false) }
        }

        override fun onResults(results: Bundle?) {
            val t = firstResult(results) ?: lastPartial.takeIf { it.isNotEmpty() }
            if (t != null) onText(t, true)
            scheduleRestart(30, false)
        }

        override fun onError(error: Int) {
            // Si une phrase était en cours, on la garde.
            if (lastPartial.isNotEmpty()) onText(lastPartial, true)
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> scheduleRestart(30, false)
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                SpeechRecognizer.ERROR_SERVER -> {
                    setStatus("Connexion internet instable… nouvel essai")
                    scheduleRestart(1000, true)
                }
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                    setStatus("Permission micro refusée : ouvrez l'appli pour l'accorder.")
                else -> scheduleRestart(250, true) // occupé, client, déconnexion…
            }
        }
    }

    // ---------------------------------------------------------------- Traduction

    private fun tail(s: String): String {
        if (s.length <= MAX_CHARS) return s
        val cut = s.indexOf(' ', s.length - MAX_CHARS)
        return "… " + s.substring(if (cut < 0) s.length - MAX_CHARS else cut + 1)
    }

    private fun onText(text: String, isFinal: Boolean) {
        if (!running) return
        lastPartial = if (isFinal) "" else text
        setStatus("")
        tvFr.text = "🇫🇷  " + tail(text)
        val s = ++seq
        for (i in 0..1) {
            if (!isFinal && inFlight[i] > 0) {
                pending[i] = Pair(text, s)   // on traduira la version la plus récente juste après
            } else {
                translate(i, text, s)
            }
        }
    }

    private fun translate(i: Int, text: String, s: Int) {
        inFlight[i]++
        val tr = if (i == 0) Translators.en() else Translators.es()
        tr.translate(text)
            .addOnSuccessListener { res ->
                if (running && s > appliedSeq[i]) {
                    appliedSeq[i] = s
                    val flag = if (i == 0) "🇬🇧  " else "🇪🇸  "
                    val tv = if (i == 0) tvEn else tvEs
                    tv.text = flag + tail(res)
                }
            }
            .addOnFailureListener { e ->
                setStatus("Traduction impossible : ${e.message}")
            }
            .addOnCompleteListener {
                inFlight[i]--
                val p = pending[i]
                if (p != null && inFlight[i] == 0) {
                    pending[i] = null
                    if (p.second > appliedSeq[i]) translate(i, p.first, p.second)
                }
            }
    }

    // ---------------------------------------------------------------- Fin

    override fun onDestroy() {
        running = false
        handler.removeCallbacksAndMessages(null)
        Prefs.get(this).unregisterOnSharedPreferenceChangeListener(prefListener)
        try { recognizer?.destroy() } catch (_: Exception) {}
        recognizer = null
        container?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        container = null
        unmuteBeeps()
        super.onDestroy()
    }
}
