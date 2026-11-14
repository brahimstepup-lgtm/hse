package app.safetytags

import android.content.Context
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class Tag(
    val row: Int = 0,
    val n: String = "",
    val date: String = "",      // affichage : dd/MM/yyyy HH:mm
    val cible: String = "",     // affichage : dd/MM/yyyy
    val type: String = "",
    val grav: String = "Moyenne",
    val empl: String = "",
    val zone: String = "",
    val desc: String = "",
    val risque: String = "",
    val action: String = "",
    val prop: String = "",
    val photo: String = "",
    val statut: String = "Ouvert",
    val resp: String = "",
    val par: String = "",
    val apres: String = ""
) {
    /** Format attendu par le script (dates ISO). */
    fun toApi(): JSONObject = JSONObject().apply {
        put("n", n); put("date", D.uiToIso(date)); put("cible", D.uiDayToIso(cible))
        put("type", type); put("grav", grav); put("empl", empl); put("zone", zone)
        put("desc", desc); put("risque", risque); put("action", action); put("prop", prop)
        put("statut", statut); put("resp", resp)
    }

    companion object {
        fun fromApi(o: JSONObject) = Tag(
            row = o.optInt("row"), n = o.optString("n"),
            date = D.isoToUi(o.optString("date")), cible = o.optString("cible"),
            type = o.optString("type"), grav = o.optString("grav").ifBlank { "Moyenne" },
            empl = o.optString("empl"), zone = o.optString("zone"), desc = o.optString("desc"),
            risque = o.optString("risque"), action = o.optString("action"), prop = o.optString("prop"),
            photo = o.optString("photo"), statut = o.optString("statut", "Ouvert"),
            resp = o.optString("resp"), apres = o.optString("apres")
        )
    }
}

data class Regle(val categorie: String, val etage: String, val responsable: String)
data class Contact(val nom: String, val email: String)

object Lists {
    val types = listOf(
        "Risque électrique", "Risque mécanique", "Risque de coupure / blessure", "Risque incendie / explosion",
        "Risque chimique", "Moyens de secours / évacuation", "Chute de hauteur", "Chute de plain-pied",
        "Chute d'objet / stockage", "Circulation / engins", "Manutention / ergonomie", "Énergie",
        "Dégradation infrastructure", "Nuisibles", "Nettoyage",
        "Ambiance de travail (éclairage / poussière / bruit)", "À qualifier"
    )
    val etages = listOf(
        "étage 1", "étage 0", "étage -1", "étage Brovind", "magasin mp", "bloc vestiaire",
        "bloc administratif", "voie exterieur", "les utilités"
    )
    val categories = listOf("Production", "Technique", "Bâtiment", "Logistique", "Incendie / Utilités", "HSE")
    val gravites = listOf("Élevée", "Moyenne", "Faible")
}

object Rules {
    private fun norm(s: String) = s.trim().lowercase()

    fun suggest(regles: List<Regle>, categorie: String, etage: String, sharp: Boolean): String {
        val main = regles.firstOrNull { norm(it.categorie) == norm(categorie) && norm(it.etage) == norm(etage) }
            ?: regles.firstOrNull { norm(it.categorie) == norm(categorie) && it.etage.trim() == "*" }
        val names = mutableListOf<String>()
        main?.responsable?.split("/")?.map { it.trim() }?.filter { it.isNotEmpty() }?.let { names += it }
        if (sharp) regles.filter { norm(it.categorie) == "partie tranchante" }
            .forEach { if (it.responsable.trim() !in names) names += it.responsable.trim() }
        return names.joinToString(" / ")
    }

    private val building = Regex(
        "fissur|\\bmur|ouverture mural|trou mural|escalier|marches?\\b|grillage|cl[ôo]ture|\\bsas\\b|toiture|chenau|" +
            "avaloir|joint de dilatation|moustiquaire|rideau|redeau|infiltration|canalisation|[ée]gout|siphon|" +
            "porte de secours|toilette|sanitaire|rev[êe]tement|carrelage|faux.plafond",
        RegexOption.IGNORE_CASE
    )

    fun isBuilding(t: Tag) = t.type.startsWith("Dégradation infrastructure") ||
        building.containsMatchIn(t.desc + " " + t.empl)

    /** « Rabah Seba / Hamza Belkacemi » -> emails connus ; renvoie (emails, noms sans email). */
    fun resolve(names: String, contacts: List<Contact>): Pair<List<String>, List<String>> {
        val emails = mutableListOf<String>()
        val missing = mutableListOf<String>()
        names.split("/", ";", ",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { n ->
            if ("@" in n) { emails += n; return@forEach }
            val c = contacts.firstOrNull { norm(it.nom) == norm(n) } ?: contacts.firstOrNull {
                val a = norm(it.nom).split(" ").toSet(); val b = norm(n).split(" ").toSet(); a == b
            }
            if (c != null && c.email.contains("@")) emails += c.email.trim() else missing += n
        }
        return emails.distinct() to missing
    }
}

object D {
    private val ui = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
    private val uiDay = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val iso = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private val isoDay = DateTimeFormatter.ISO_LOCAL_DATE

    fun now(): String = LocalDateTime.now().format(ui)

    fun parse(s: String): LocalDateTime? {
        val t = s.trim()
        return runCatching { LocalDateTime.parse(t, ui) }.getOrNull()
            ?: runCatching { LocalDateTime.parse(t, iso) }.getOrNull()
            ?: runCatching { LocalDate.parse(t, uiDay).atStartOfDay() }.getOrNull()
            ?: runCatching { LocalDate.parse(t.take(10), isoDay).atStartOfDay() }.getOrNull()
    }

    fun plusDays(fromUi: String, days: Long): String =
        ((parse(fromUi) ?: LocalDateTime.now()).toLocalDate()).plusDays(days).format(uiDay)

    fun uiToIso(s: String): String = parse(s)?.format(iso) ?: s
    fun uiDayToIso(s: String): String = parse(s)?.toLocalDate()?.format(isoDay) ?: s
    fun isoToUi(s: String): String = parse(s)?.format(ui) ?: s

    /** « 14/11/2026 à 09:44 » */
    fun label(s: String): String = parse(s)?.let { it.format(uiDay) + " à " + it.format(DateTimeFormatter.ofPattern("HH:mm")) } ?: s
    fun day(s: String): String = parse(s)?.format(uiDay) ?: s
}

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("safetytags", Context.MODE_PRIVATE)
    private fun get(k: String, d: String = "") = sp.getString(k, d) ?: d
    private fun put(k: String, v: String) = sp.edit().putString(k, v).apply()

    var url: String
        get() = get("url")
        set(v) = put("url", v.trim())
    var token: String
        get() = get("token")
        set(v) = put("token", v.trim())
    var aiKey: String
        get() = get("aiKey")
        set(v) = put("aiKey", v.trim())
    var aiModel: String
        get() = get("aiModel", "claude-sonnet-5-5")
        set(v) = put("aiModel", v.trim())
    var cache: String
        get() = get("cache")
        set(v) = put("cache", v)
}
