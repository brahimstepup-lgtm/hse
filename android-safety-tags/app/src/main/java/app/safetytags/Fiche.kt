package app.safetytags

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface

/** Dessine la fiche « SAFETY TAG » (même mise en page que les fiches PNG habituelles). */
object Fiche {
    private const val W = 1100f
    private const val S = 1.5f
    private val RED = Color.parseColor("#C62828")
    private val GREEN = Color.parseColor("#2E7D32")
    private val INK = Color.parseColor("#222222")
    private val REGULAR = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    private val BOLD = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)


    private fun font(p: Paint, size: Float, bold: Boolean, spacing: Float = 0f) {
        p.textSize = size; p.typeface = if (bold) BOLD else REGULAR; p.letterSpacing = spacing
    }

    private fun wrap(p: Paint, text: String, maxW: Float): List<String> {
        val out = mutableListOf<String>()
        for (para in text.ifBlank { "—" }.split("\n")) {
            var line = ""
            for (w in para.split(Regex("\\s+")).filter { it.isNotEmpty() }) {
                val t = if (line.isEmpty()) w else "$line $w"
                if (p.measureText(t) > maxW && line.isNotEmpty()) { out += line; line = w } else line = t
            }
            out += line
        }
        return out
    }

    private class Ctx(val c: Canvas?, val p: Paint)

    private fun col(x: Ctx, lab: String, v: String, bold: Boolean, left: Float, w: Float, y: Float): Float {
        font(x.p, 20f, bold); val lines = wrap(x.p, v, w)
        x.c?.let { c ->
            x.p.color = RED; font(x.p, 14f, true, 0.07f); c.drawText(lab.uppercase(), left, y + 14f, x.p)
            x.p.color = INK; font(x.p, 20f, bold)
            lines.forEachIndexed { i, l -> c.drawText(l, left, y + 42f + i * 27f, x.p) }
        }
        return 22f + lines.size * 27f
    }

    private fun field(x: Ctx, lab: String, v: String, left: Float, w: Float, y: Float, box: Boolean = false, green: Boolean = false): Float {
        val inner = if (box) w - 34f else w
        font(x.p, 20f, false); val lines = wrap(x.p, v, inner)
        val h = 22f + lines.size * 27f + if (box) 20f else 0f
        x.c?.let { c ->
            if (box) {
                x.p.color = Color.parseColor(if (green) "#F1F8E9" else "#FFF5F5")
                c.drawRoundRect(RectF(left, y, left + w, y + h), 4f, 4f, x.p)
                x.p.color = if (green) GREEN else RED
                c.drawRect(left, y, left + 5f, y + h, x.p)
            }
            val tx = if (box) left + 19f else left
            val ty = if (box) y + 10f else y
            x.p.color = if (green) GREEN else RED; font(x.p, 14f, true, 0.07f)
            c.drawText(lab.uppercase(), tx, ty + 14f, x.p)
            x.p.color = INK; font(x.p, 20f, false)
            lines.forEachIndexed { i, l -> c.drawText(l, tx, ty + 42f + i * 27f, x.p) }
        }
        return h
    }

    private fun info(x: Ctx, t: Tag, left: Float, w: Float, y0: Float): Float {
        var y = y0
        val cw = (w - 20f) / 2f
        var h1 = col(x, "Date de constat", D.label(t.date), false, left, cw, y)
        var h2 = col(x, "Date cible", D.day(t.cible), false, left + cw + 20f, cw, y)
        y += maxOf(h1, h2) + 16f
        h1 = col(x, "Zone", listOf(t.zone, t.service).filter { it.isNotBlank() }.joinToString(" · "), false, left, cw, y)
        h2 = col(x, "Responsable", t.resp, true, left + cw + 20f, cw, y)
        y += maxOf(h1, h2) + 16f
        y += field(x, "Emplacement", t.empl, left, w, y) + 16f
        y += field(x, "Description", t.desc, left, w, y) + 16f
        y += field(x, "Risque principal", t.risque, left, w, y, box = true) + 16f
        y += field(x, if (t.statut == "Clôturé") "Action réalisée" else "Action urgente", t.action, left, w, y, box = true) + 16f
        y += field(x, "Proposition", t.prop, left, w, y, box = true, green = true)
        return y
    }

    private fun badge(c: Canvas, p: Paint, x: Float, y: Float, text: String, bg: String, fg: String): Float {
        font(p, 18f, true); val w = p.measureText(text) + 32f
        p.color = Color.parseColor(bg); c.drawRoundRect(RectF(x, y, x + w, y + 38f), 19f, 19f, p)
        p.color = Color.parseColor(fg); c.drawText(text, x + 16f, y + 26f, p)
        return w
    }

    fun render(t: Tag, photo: Bitmap?, cfg: Config, logo: Bitmap? = null): Bitmap {
        val site = cfg.organisation.ifBlank { "Suivi des anomalies HSE" }
        val footer = cfg.pied
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val top = 110f; val badgesH = 74f; val mainY = top + badgesH + 26f
        val infoX = 30f + 400f + 26f; val infoW = W - infoX - 30f
        val infoEnd = info(Ctx(null, p), t, infoX, infoW, mainY)
        val footY = maxOf(infoEnd, mainY + 620f + 28f) + 26f
        val h = footY + 48f

        val bmp = Bitmap.createBitmap((W * S).toInt(), (h * S).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); c.scale(S, S); c.drawColor(Color.WHITE)

        // en-tête
        p.color = RED; c.drawRect(0f, 0f, W, top, p)
        var tx = 36f
        if (logo != null) {
            // logo de l'entreprise en haut à gauche, sur un cartouche blanc
            val boxH = 78f; val inner = boxH - 12f
            val lw = (logo.width.toFloat() / logo.height * inner).coerceAtMost(200f)
            val lh = lw * logo.height / logo.width
            val boxW = lw + 12f
            p.color = Color.WHITE; c.drawRoundRect(RectF(22f, 16f, 22f + boxW, 16f + boxH), 10f, 10f, p)
            val ly = 16f + (boxH - lh) / 2f
            c.drawBitmap(logo, null, RectF(28f, ly, 28f + lw, ly + lh), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
            tx = 22f + boxW + 22f
        }
        p.color = Color.WHITE; font(p, 40f, true, 0.05f); c.drawText("SAFETY TAG", tx, 58f, p)
        font(p, 18f, false); p.alpha = 230; c.drawText(site, tx, 88f, p); p.alpha = 255
        font(p, 44f, true); val nt = "N° ${t.n.ifBlank { "—" }}"; val nw = p.measureText(nt) + 44f
        p.color = Color.WHITE; c.drawRoundRect(RectF(W - 36f - nw, 24f, W - 36f, 88f), 8f, 8f, p)
        p.color = RED; c.drawText(nt, W - 36f - nw + 22f, 72f, p)

        // badges
        p.color = Color.parseColor("#FAFAFA"); c.drawRect(0f, top, W, top + badgesH, p)
        p.color = Color.parseColor("#DDDDDD"); c.drawRect(0f, top + badgesH - 1f, W, top + badgesH, p)
        var bx = 36f; val by = top + 18f
        bx += badge(c, p, bx, by, t.type.ifBlank { "Type" }, "#E3F2FD", "#0D47A1") + 14f
        val g = when (t.grav) { "Élevée" -> "#FFCDD2" to "#B71C1C"; "Faible" -> "#DCEDC8" to "#33691E"; else -> "#FFE0B2" to "#E65100" }
        bx += badge(c, p, bx, by, "Gravité : ${t.grav}", g.first, g.second) + 14f
        val s = if (t.statut == "Clôturé") "#C8E6C9" to "#1B5E20" else "#FFCDD2" to "#B71C1C"
        badge(c, p, bx, by, "Statut : ${t.statut}", s.first, s.second)

        // photo
        val px = 36f; val py = mainY; val pw = 400f; val ph = 620f
        val clip = Path().apply { addRoundRect(RectF(px, py, px + pw, py + ph), 8f, 8f, Path.Direction.CW) }
        c.save(); c.clipPath(clip)
        if (photo != null) {
            val r = maxOf(pw / photo.width, ph / photo.height)
            val sw = pw / r; val sh = ph / r
            val sx = ((photo.width - sw) / 2f).toInt(); val sy = ((photo.height - sh) / 2f).toInt()
            c.drawBitmap(photo, Rect(sx, sy, (sx + sw).toInt(), (sy + sh).toInt()), RectF(px, py, px + pw, py + ph), Paint(Paint.FILTER_BITMAP_FLAG))
        } else {
            p.color = Color.parseColor("#ECEFF1"); c.drawRect(px, py, px + pw, py + ph, p)
            p.color = Color.parseColor("#90A4AE"); font(p, 20f, true); p.textAlign = Paint.Align.CENTER
            c.drawText("Photo de constat", px + pw / 2f, py + ph / 2f, p); p.textAlign = Paint.Align.LEFT
        }
        c.restore()
        p.style = Paint.Style.STROKE; p.strokeWidth = 1f; p.color = Color.parseColor("#CCCCCC")
        c.drawRoundRect(RectF(px + .5f, py + .5f, px + pw - .5f, py + ph - .5f), 8f, 8f, p); p.style = Paint.Style.FILL
        p.color = Color.parseColor("#666666"); font(p, 14f, false); p.textAlign = Paint.Align.CENTER
        c.drawText("Photo de constat — ${D.day(t.date)}", px + pw / 2f, py + ph + 20f, p); p.textAlign = Paint.Align.LEFT

        // informations
        info(Ctx(c, p), t, infoX, infoW, mainY)

        // pied de page
        p.color = Color.parseColor("#263238"); c.drawRect(0f, footY, W, footY + 48f, p)
        p.color = Color.WHITE; font(p, 16f, false)
        c.drawText("Constaté par : ${t.par.ifBlank { "—" }}", 36f, footY + 30f, p)
        p.textAlign = Paint.Align.RIGHT; c.drawText(footer, W - 36f, footY + 30f, p); p.textAlign = Paint.Align.LEFT

        // cadre
        p.style = Paint.Style.STROKE; p.strokeWidth = 6f; p.color = RED
        c.drawRect(3f, 3f, W - 3f, h - 3f, p); p.style = Paint.Style.FILL
        return bmp
    }
}
