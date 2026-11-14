package app.safetytags

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream

/** Rapport PDF des tags (fonction Pro). Format A4 portrait, 595 × 842 points. */
object Report {
    private const val W = 595f
    private const val H = 842f
    private const val M = 36f
    private val RED = Color.parseColor("#C62828")
    private val INK = Color.parseColor("#222222")
    private val GREY = Color.parseColor("#6B6B6B")

    private fun wrap(p: Paint, text: String, maxW: Float, maxLines: Int): List<String> {
        val out = mutableListOf<String>()
        var line = ""
        for (w in text.ifBlank { "—" }.split(Regex("\\s+"))) {
            val t = if (line.isEmpty()) w else "$line $w"
            if (p.measureText(t) > maxW && line.isNotEmpty()) { out += line; line = w } else line = t
        }
        out += line
        if (out.size > maxLines) {
            val cut = out.take(maxLines).toMutableList()
            cut[maxLines - 1] = cut[maxLines - 1].trimEnd() + "…"
            return cut
        }
        return out
    }

    fun pdf(ctx: Context, tags: List<Tag>, cfg: Config, logo: Bitmap?, late: (Tag) -> Boolean): File {
        val doc = PdfDocument()
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val regular = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        val bold = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        val cols = floatArrayOf(M, M + 38f, M + 98f, M + 168f, M + 368f, M + 468f)   // N°, date, zone, description, responsable, cible
        val colW = floatArrayOf(36f, 58f, 68f, 196f, 96f, 55f)
        var pageNo = 0
        lateinit var page: PdfDocument.Page
        var y = 0f

        fun header() {
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W.toInt(), H.toInt(), pageNo).create())
            val c = page.canvas
            p.color = RED; c.drawRect(0f, 0f, W, 64f, p)
            var tx = M
            if (logo != null) {
                val lh = 40f; val lw = (logo.width.toFloat() / logo.height * lh).coerceAtMost(110f)
                p.color = Color.WHITE; c.drawRoundRect(RectF(M - 4f, 12f, M + lw + 4f, 52f), 6f, 6f, p)
                c.drawBitmap(logo, null, RectF(M, 14f, M + lw, 14f + lw * logo.height / logo.width), Paint(Paint.FILTER_BITMAP_FLAG))
                tx = M + lw + 16f
            }
            p.color = Color.WHITE; p.typeface = bold; p.textSize = 18f
            c.drawText(tr("Rapport Safety Tags"), tx, 32f, p)
            p.typeface = regular; p.textSize = 10f
            c.drawText(cfg.organisation.ifBlank { tr("Suivi des anomalies HSE") } + " · " + D.day(D.now()), tx, 48f, p)
            p.textAlign = Paint.Align.RIGHT; c.drawText(tr("Page {0}", pageNo), W - M, 48f, p); p.textAlign = Paint.Align.LEFT
            y = 84f
        }

        fun tableHead() {
            val c = page.canvas
            p.color = Color.parseColor("#F3F3F3"); c.drawRect(M - 4f, y - 12f, W - M + 4f, y + 6f, p)
            p.color = GREY; p.typeface = bold; p.textSize = 8.5f
            listOf("N°", "Date", "Zone", "Description", "Responsable", "Cible").forEachIndexed { i, h -> c.drawText(tr(h).uppercase(), cols[i], y, p) }
            y += 16f
        }

        header()
        // synthèse
        val c0 = page.canvas
        val lateCount = tags.count(late)
        val high = tags.count { it.grav == "Élevée" }
        p.color = INK; p.typeface = bold; p.textSize = 12f
        c0.drawText(tr("{0} tag(s) · {1} en retard · {2} de gravité élevée", tags.size, lateCount, high), M, y, p)
        y += 16f
        p.typeface = regular; p.textSize = 9.5f; p.color = GREY
        val byResp = tags.flatMap { Rules.splitNames(it.resp) }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(6)
        if (byResp.isNotEmpty()) {
            c0.drawText(tr("Par responsable : {0}", byResp.joinToString(" · ") { "${it.key} (${it.value})" }), M, y, p)
            y += 20f
        }
        tableHead()

        for (t in tags.sortedWith(compareByDescending<Tag> { late(it) }.thenBy { D.parse(it.cible) })) {
            p.typeface = regular; p.textSize = 9f
            val desc = wrap(p, t.desc, colW[3] - 6f, 3)
            val resp = wrap(p, t.resp, colW[4] - 6f, 2)
            val zone = wrap(p, t.zone, colW[2] - 6f, 2)
            val rowH = maxOf(desc.size, resp.size, zone.size) * 11f + 8f
            if (y + rowH > H - M) { doc.finishPage(page); header(); tableHead() }
            val c = page.canvas
            val isLate = late(t)
            p.color = when (t.grav) { "Élevée" -> RED; "Faible" -> Color.parseColor("#689F38"); else -> Color.parseColor("#F57C00") }
            c.drawRect(M - 6f, y - 9f, M - 3f, y - 9f + rowH - 4f, p)
            p.color = INK; p.typeface = bold; c.drawText(t.n, cols[0], y, p)
            p.typeface = regular
            c.drawText(D.day(t.date), cols[1], y, p)
            zone.forEachIndexed { i, l -> c.drawText(l, cols[2], y + i * 11f, p) }
            desc.forEachIndexed { i, l -> c.drawText(l, cols[3], y + i * 11f, p) }
            resp.forEachIndexed { i, l -> c.drawText(l, cols[4], y + i * 11f, p) }
            p.color = if (isLate) RED else INK; p.typeface = if (isLate) bold else regular
            c.drawText(D.day(t.cible), cols[5], y, p)
            if (isLate) { p.textSize = 7.5f; c.drawText(tr("EN RETARD"), cols[5], y + 11f, p) }
            p.color = Color.parseColor("#E6E6E6"); c.drawRect(M - 4f, y + rowH - 10f, W - M + 4f, y + rowH - 9.5f, p)
            y += rowH
        }
        doc.finishPage(page)

        val f = File(File(ctx.cacheDir, "exports").apply { mkdirs() }, "Rapport_Safety_Tags.pdf")
        FileOutputStream(f).use { doc.writeTo(it) }
        doc.close()
        return f
    }
}
