package com.easyquran

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.Color
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*
import java.io.File

class MainActivity : Activity() {
    private lateinit var img: ImageView
    private lateinit var refTv: TextView
    private lateinit var srcTv: TextView
    private lateinit var extraTv: TextView
    private lateinit var markBtn: Button
    private lateinit var playBtn: Button
    private lateinit var targetBtn: Button
    private lateinit var remBtn: Button
    private val ui = Handler(Looper.getMainLooper())
    private var mp: MediaPlayer? = null

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Quran.load(this)
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)

        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(14), dp(14), dp(24)) }
        val sv = ScrollView(this).apply { setBackgroundColor(Color.parseColor("#0E1F1A")); addView(col) }
        setContentView(sv)

        fun tv(size: Float, alpha: Float = 1f) = TextView(this).apply { setTextColor(Color.WHITE); textSize = size; this.alpha = alpha }
        col.addView(tv(22f).apply { text = "🌙 Easy Quran"; setPadding(0, 0, 0, dp(8)) })
        img = ImageView(this).apply { adjustViewBounds = true; scaleType = ImageView.ScaleType.FIT_CENTER }
        col.addView(img, LinearLayout.LayoutParams(MATCH_PARENT, (resources.displayMetrics.heightPixels * 0.55f).toInt()))
        refTv = tv(15f).apply { gravity = Gravity.CENTER; setPadding(0, dp(8), 0, 0) }; col.addView(refTv)
        srcTv = tv(12f, .65f).apply { gravity = Gravity.CENTER }; col.addView(srcTv)
        extraTv = tv(14f, .9f).apply { gravity = Gravity.CENTER; setPadding(0, dp(6), 0, dp(6)) }; col.addView(extraTv)

        markBtn = btn("☆") { toggleMark() }
        playBtn = btn("🔊") { togglePlay() }
        col.addView(row(btn("◀") { move(-1) }, btn("▶") { move(1) }, markBtn, playBtn))
        col.addView(row(btn("Search") { searchDialog() }, btn("Go to") { gotoDialog() }, btn("Note") { noteDialog() }, btn("Saved") { savedDialog() }))
        col.addView(row(btn("Set wallpaper now") { setNow() }))
        col.addView(row(btn("🕌 Namaz times, tracker, Qibla & Tasbeeh") { startActivity(Intent(this, PrayerActivity::class.java)) }))

        val sw = Switch(this).apply {
            text = "Next ayah + new wallpaper on every unlock"; setTextColor(Color.WHITE); isChecked = Prefs.unlock(this@MainActivity)
            setPadding(0, dp(12), 0, dp(4))
            setOnCheckedChangeListener { _, on ->
                Prefs.setUnlock(this@MainActivity, on)
                val i = Intent(this@MainActivity, UnlockService::class.java)
                if (on) startForegroundService(i) else stopService(i)
            }
        }
        col.addView(sw)
        targetBtn = btn("") { Prefs.setTarget(this, (Prefs.target(this) + 1) % 3); labels() }
        remBtn = btn("") { reminderDialog() }
        col.addView(row(targetBtn, remBtn))
        col.addView(row(btn("Import translation / tafseer") { pick("*/*", 1, false) }, btn("Import photos") { pick("image/*", 2, true) }))
        col.addView(row(btn("Use built-in translation") { Quran.reset(this); toast("Built-in translation restored"); refresh() }))
        col.addView(tv(11f, .55f).apply {
            setPadding(0, dp(14), 0, 0)
            text = "Arabic text and the built-in Urdu placeholder come from Tanzil / the Noble Qur’an Encyclopedia (CC BY-SA 4.0). " +
                "Scenes are original illustrations; your own photos can be added too. Audio streams from everyayah.com (Alafasy)."
        })
        if (Prefs.unlock(this)) startForegroundService(Intent(this, UnlockService::class.java))
        Reminder.schedule(this); Prayer.schedule(this)
        labels(); refresh()
    }

    private fun btn(t: String, f: () -> Unit) = Button(this).apply { text = t; isAllCaps = false; setOnClickListener { f() } }
    private fun row(vararg b: Button) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        b.forEach { addView(it, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)) }
    }
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun labels() {
        targetBtn.text = "Apply to: " + listOf("Lock + Home", "Lock screen", "Home screen")[Prefs.target(this)]
        val m = Prefs.reminder(this)
        remBtn.text = if (m < 0) "Daily reminder: off" else "Reminder: %02d:%02d".format(m / 60, m % 60)
    }

    private fun refresh() {
        val i = Prefs.index(this).coerceIn(0, Quran.ayat.size - 1); val a = Quran.ayat[i]
        refTv.text = "${Quran.names[a.s - 1]}  ${a.s}:${a.a}   •   ${i + 1}/${Quran.ayat.size}"
        srcTv.text = "Urdu: " + if (a.own) Quran.urSource else Quran.baseSource
        val note = Prefs.note(this, i)
        extraTv.text = listOf(a.ur, a.tf, if (note.isNotEmpty()) "📝 $note" else "").filter { it.isNotEmpty() }.joinToString("\n\n")
        markBtn.text = if (Prefs.bookmarks(this).contains(i.toString())) "★" else "☆"
        val seed = Prefs.seed(this)
        Thread {
            val (bmp, _) = Scene.render(this, 540, 960, a, seed)
            ui.post { img.setImageBitmap(bmp) }
        }.start()
    }

    private fun jump(i: Int) { stopAudio(); Prefs.setIndex(this, i); Prefs.setSeed(this, Prefs.seed(this) + 1); refresh() }
    private fun move(d: Int) = jump((Prefs.index(this) + d + Quran.ayat.size) % Quran.ayat.size)

    private fun toggleMark() {
        val s = Prefs.bookmarks(this); val k = Prefs.index(this).toString()
        if (!s.remove(k)) s.add(k)
        Prefs.setBookmarks(this, s); refresh()
    }

    private fun setNow() {
        toast("Setting wallpaper…")
        Thread {
            try { Wall.apply(this); ui.post { toast("Wallpaper updated") } } catch (e: Exception) { ui.post { toast("Could not set wallpaper: ${e.message}") } }
        }.start()
    }

    private fun input(title: String, hint: String, initial: String = "", type: Int = InputType.TYPE_CLASS_TEXT, ok: (String) -> Unit) {
        val et = EditText(this).apply { this.hint = hint; setText(initial); inputType = type }
        AlertDialog.Builder(this).setTitle(title).setView(et).setPositiveButton("OK") { _, _ -> ok(et.text.toString()) }.setNegativeButton("Cancel", null).show()
    }

    private fun showList(title: String, idx: List<Int>) {
        if (idx.isEmpty()) { toast("Nothing found"); return }
        val items = idx.map { val a = Quran.ayat[it]; "${Quran.names[a.s - 1]} ${a.s}:${a.a} — ${a.ur.take(60)}" }.toTypedArray()
        AlertDialog.Builder(this).setTitle(title).setItems(items) { _, w -> jump(idx[w]) }.show()
    }

    private fun searchDialog() = input("Search", "Urdu/Arabic word, surah name, or 2:255") { showList("Results", Quran.search(it)) }
    private fun gotoDialog() = input("Go to ayah", "surah:ayah e.g. 36:1") { showList("Go to", Quran.search(it)) }
    private fun savedDialog() = showList("Saved ayat", Prefs.bookmarks(this).mapNotNull { it.toIntOrNull() }.sorted())
    private fun noteDialog() { val i = Prefs.index(this); input("My note on this ayah", "Write a note", Prefs.note(this, i)) { Prefs.setNote(this, i, it.trim()); refresh() } }

    private fun reminderDialog() {
        AlertDialog.Builder(this).setTitle("Daily ayah reminder").setItems(arrayOf("Set time", "Turn off")) { _, w ->
            if (w == 0) TimePickerDialog(this, { _, h, m -> Prefs.setReminder(this, h * 60 + m); Reminder.schedule(this); labels() }, 8, 0, true).show()
            else { Prefs.setReminder(this, -1); Reminder.schedule(this); labels() }
        }.show()
    }

    // ---- audio (Mishary Alafasy, streamed) ----
    private fun togglePlay() {
        if (mp != null) { stopAudio(); return }
        val a = Quran.ayat[Prefs.index(this)]
        val url = "https://everyayah.com/data/Alafasy_128kbps/%03d%03d.mp3".format(a.s, a.a)
        try {
            mp = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).setUsage(AudioAttributes.USAGE_MEDIA).build())
                setDataSource(url)
                setOnPreparedListener { start(); playBtn.text = "⏹" }
                setOnCompletionListener { stopAudio() }
                setOnErrorListener { _, _, _ -> toast("Audio unavailable (check internet)"); stopAudio(); true }
                prepareAsync()
            }
        } catch (e: Exception) { toast("Audio unavailable"); stopAudio() }
    }

    private fun stopAudio() { try { mp?.release() } catch (e: Exception) { }; mp = null; if (::playBtn.isInitialized) playBtn.text = "🔊" }

    // ---- imports ----
    private fun pick(type: String, code: Int, multi: Boolean) {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); this.type = type; putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multi) }
        startActivityForResult(i, code)
    }

    @Deprecated("simple picker result")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (res != RESULT_OK || data == null) return
        try {
            if (req == 1) {
                val text = contentResolver.openInputStream(data.data!!)!!.bufferedReader().use { it.readText() }
                val n = Quran.applyCustom(text)
                File(filesDir, "custom.json").writeText(text)
                toast("Loaded translation for $n ayat"); refresh()
            } else if (req == 2) {
                val dir = File(filesDir, "bg").apply { mkdirs() }
                val uris = ArrayList<android.net.Uri>()
                data.clipData?.let { for (k in 0 until it.itemCount) uris.add(it.getItemAt(k).uri) } ?: data.data?.let { uris.add(it) }
                uris.forEachIndexed { k, u ->
                    contentResolver.openInputStream(u)!!.use { inp -> File(dir, "p${System.currentTimeMillis()}_$k.img").outputStream().use { inp.copyTo(it) } }
                }
                toast("Added ${uris.size} photo(s)"); refresh()
            }
        } catch (e: Exception) { toast("Import failed: ${e.message}") }
    }

    override fun onDestroy() { stopAudio(); super.onDestroy() }
}
