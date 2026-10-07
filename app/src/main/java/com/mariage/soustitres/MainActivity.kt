package com.mariage.soustitres

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.SpeechRecognizer
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val REQ_VIDEO = 10
    private lateinit var col: LinearLayout
    private lateinit var tvMic: TextView
    private lateinit var tvOverlay: TextView
    private lateinit var tvNotif: TextView
    private lateinit var tvModels: TextView
    private lateinit var tvRecog: TextView
    private lateinit var tvVideo: TextView
    private lateinit var tvRunning: TextView
    private var modelsReady = false

    // ------------------------------------------------------------ petites aides d'interface

    private fun section(s: String) = TextView(this).apply {
        text = s
        textSize = 20f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(Color.rgb(90, 40, 120))
        setPadding(0, dp(22), 0, dp(6))
        col.addView(this)
    }

    private fun label(s: String = "") = TextView(this).apply {
        text = s
        textSize = 15f
        setTextColor(Color.DKGRAY)
        setPadding(0, dp(4), 0, dp(4))
        col.addView(this)
    }

    private fun button(s: String, onClick: () -> Unit) = Button(this).apply {
        text = s
        isAllCaps = false
        setOnClickListener { onClick() }
        col.addView(this)
    }

    private fun checkbox(s: String, value: Boolean, onChange: (Boolean) -> Unit) = CheckBox(this).apply {
        text = s
        isChecked = value
        setOnCheckedChangeListener { _, b -> onChange(b) }
        col.addView(this)
    }

    private fun slider(name: String, lo: Int, hi: Int, value: Int, unit: String, onChange: (Int) -> Unit) {
        val l = label("$name : $value$unit")
        SeekBar(this).apply {
            this.max = hi - lo
            progress = value - lo
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    l.text = "$name : ${p + lo}$unit"
                    if (fromUser) onChange(p + lo)
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
            col.addView(this)
        }
    }

    // ------------------------------------------------------------ écran

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = Prefs.get(this)

        col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(40))
        }
        val scroll = ScrollView(this).apply {
            fitsSystemWindows = true
            setBackgroundColor(Color.rgb(253, 250, 255))
            addView(col)
        }
        setContentView(scroll)

        TextView(this).apply {
            text = "💍 Sous-titres Mariage"
            textSize = 26f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.rgb(90, 40, 120))
            col.addView(this)
        }
        label("Votre discours en français, sous-titré en direct en anglais 🇬🇧 et en espagnol 🇪🇸.")

        section("1. Préparation (une seule fois)")
        tvMic = label()
        button("Autoriser le micro") {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        }
        tvOverlay = label()
        button("Autoriser l'affichage par-dessus les autres applis") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        tvNotif = label()
        if (Build.VERSION.SDK_INT >= 33) {
            button("Autoriser les notifications (boutons Pause / Arrêter)") {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2)
            }
        }
        tvRecog = label()
        tvModels = label()
        button("Télécharger les traductions anglais + espagnol (~60 Mo)") {
            tvModels.text = "⏳ Téléchargement en cours… (gardez l'appli ouverte)"
            Translators.download { ok, msg ->
                runOnUiThread {
                    if (ok) Toast.makeText(this, "Traductions prêtes ✅", Toast.LENGTH_LONG).show()
                    else Toast.makeText(this, "Échec : $msg", Toast.LENGTH_LONG).show()
                    refresh()
                }
            }
        }

        section("2. Vidéo de fond")
        tvVideo = label()
        button("Choisir la vidéo de fond") {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "video/*"
            }
            startActivityForResult(i, REQ_VIDEO)
        }
        checkbox("Garder le son de la vidéo", Prefs.videoSound(this)) {
            prefs.edit().putBoolean(Prefs.VIDEO_SOUND, it).apply()
        }

        section("3. Apparence des sous-titres")
        slider("Taille du texte", 16, 64, Prefs.textSize(this), " sp") {
            prefs.edit().putInt(Prefs.TEXT_SIZE, it).apply()
        }
        slider("Opacité du fond noir", 0, 100, Prefs.bgAlpha(this), " %") {
            prefs.edit().putInt(Prefs.BG_ALPHA, it).apply()
        }
        checkbox("Afficher aussi le texte français", Prefs.showFrench(this)) {
            prefs.edit().putBoolean(Prefs.SHOW_FRENCH, it).apply()
        }
        checkbox("Sous-titres en haut de l'écran (sinon en bas)", Prefs.positionTop(this)) {
            prefs.edit().putBoolean(Prefs.POSITION_TOP, it).apply()
        }
        checkbox("Couper les « bips » du micro (coupe aussi le son des médias pendant le discours)", Prefs.muteBeeps(this)) {
            prefs.edit().putBoolean(Prefs.MUTE_BEEPS, it).apply()
        }
        label("Astuce : pendant le discours, le bandeau se déplace en le faisant glisser avec le doigt.")

        section("4. Tester la traduction")
        val input = EditText(this).apply {
            hint = "Écrivez une phrase en français"
            setText("Merci à tous d'être venus célébrer ce jour si important pour nous.")
            col.addView(this)
        }
        val out = label()
        button("Traduire") {
            val t = input.text.toString().trim()
            if (t.isEmpty()) return@button
            out.text = "…"
            Translators.en().translate(t).addOnSuccessListener { en ->
                Translators.es().translate(t).addOnSuccessListener { es ->
                    out.text = "🇬🇧 $en\n\n🇪🇸 $es"
                }.addOnFailureListener { out.text = "Espagnol : ${it.message}\n(téléchargez les traductions)" }
            }.addOnFailureListener { out.text = "Anglais : ${it.message}\n(téléchargez les traductions)" }
        }

        section("5. C'est parti !")
        tvRunning = label()
        button("▶  Démarrer : vidéo de fond + sous-titres") { start(withVideo = true) }
        button("▶  Démarrer les sous-titres seuls (vidéo lue par une autre appli)") { start(withVideo = false) }
        button("■  Arrêter les sous-titres") {
            startService(Intent(this, SubtitleService::class.java).setAction(SubtitleService.ACTION_STOP))
            col.postDelayed({ refresh() }, 400)
        }
        label("Pendant le discours : la notification permet de mettre en Pause ou d'Arrêter.\n" +
            "Le téléphone doit rester connecté à internet (reconnaissance vocale).")
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun ok(b: Boolean) = if (b) "✅" else "❌"

    private fun refresh() {
        val mic = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val overlay = Settings.canDrawOverlays(this)
        val notif = Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        tvMic.text = "${ok(mic)} Micro"
        tvOverlay.text = "${ok(overlay)} Affichage par-dessus les autres applis"
        tvNotif.text = "${ok(notif)} Notifications"
        val recog = SpeechRecognizer.isRecognitionAvailable(this)
        tvRecog.text = if (recog) "✅ Reconnaissance vocale disponible"
        else "❌ Reconnaissance vocale introuvable : installez ou mettez à jour l'appli « Google » depuis le Play Store."
        Translators.checkDownloaded { ready ->
            modelsReady = ready
            tvModels.text = "${ok(ready)} Traductions anglais + espagnol téléchargées"
        }
        val v = Prefs.videoUri(this)
        tvVideo.text = if (v == null) "Aucune vidéo choisie (fond noir)" else "🎬 Vidéo choisie ✅"
        tvRunning.text = if (SubtitleService.running) "🟢 Sous-titres en cours" else "⚪ Sous-titres arrêtés"
    }

    private fun start(withVideo: Boolean) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Autorisez d'abord le micro (étape 1)", Toast.LENGTH_LONG).show()
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Autorisez d'abord l'affichage par-dessus les autres applis (étape 1)", Toast.LENGTH_LONG).show()
            return
        }
        if (!modelsReady) {
            Toast.makeText(this, "Traductions pas encore téléchargées : téléchargement lancé…", Toast.LENGTH_LONG).show()
        }
        startForegroundService(Intent(this, SubtitleService::class.java).setAction(SubtitleService.ACTION_START))
        if (withVideo) {
            startActivity(Intent(this, PlayerActivity::class.java))
        } else {
            Toast.makeText(this, "Ouvrez maintenant votre vidéo dans l'appli de votre choix", Toast.LENGTH_LONG).show()
            moveTaskToBack(true)
        }
        col.postDelayed({ refresh() }, 400)
    }

    @Deprecated("API Activity classique")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_VIDEO && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {
            }
            Prefs.get(this).edit().putString(Prefs.VIDEO_URI, uri.toString()).apply()
            refresh()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refresh()
    }
}
