package app.safetytags

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class Tag(
    val row: Int = 0,           // ligne du Sheet (mode Sheet) ou identifiant local (mode local)
    val n: String = "",
    val date: String = "",      // dd/MM/yyyy HH:mm
    val cible: String = "",     // dd/MM/yyyy
    val type: String = "",
    val grav: String = "Moyenne",
    val empl: String = "",
    val zone: String = "",
    val service: String = "",
    val desc: String = "",
    val risque: String = "",
    val action: String = "",
    val prop: String = "",
    val photo: String = "",     // lien Drive ou chemin local
    val statut: String = "Ouvert",
    val resp: String = "",
    val par: String = "",
    val apres: String = ""
) {
    fun toApi(): JSONObject = JSONObject().apply {
        put("n", n); put("date", D.uiToIso(date)); put("cible", D.uiDayToIso(cible))
        put("type", type); put("grav", grav); put("empl", empl); put("zone", zone)
        put("desc", desc); put("risque", risque); put("action", action); put("prop", prop)
        put("statut", statut); put("resp", resp)
    }

    fun toLocal(): JSONObject = toApi().apply {
        put("row", row); put("date", date); put("cible", cible); put("service", service)
        put("photo", photo); put("par", par); put("apres", apres)
    }

    companion object {
        fun fromApi(o: JSONObject) = Tag(
            row = o.optInt("row"), n = o.optString("n"),
            date = D.isoToUi(o.optString("date")), cible = D.day(o.optString("cible")),
            type = o.optString("type"), grav = o.optString("grav").ifBlank { "Moyenne" },
            empl = o.optString("empl"), zone = o.optString("zone"), service = o.optString("service"),
            desc = o.optString("desc"), risque = o.optString("risque"), action = o.optString("action"),
            prop = o.optString("prop"), photo = o.optString("photo"), statut = o.optString("statut", "Ouvert"),
            resp = o.optString("resp"), par = o.optString("par"), apres = o.optString("apres")
        )
    }
}

data class Regle(val service: String, val zone: String, val responsable: String)
data class Contact(val nom: String, val email: String)

/** Tout ce que l'utilisateur personnalise (rien n'est figé dans l'application). */
data class Config(
    val organisation: String = "",          // sous-titre de la fiche
    val pied: String = "",                  // pied de la fiche
    val constatePar: String = "",
    val signature: String = "",
    val zones: List<String> = emptyList(),
    val types: List<String> = emptyList(),
    val services: List<String> = emptyList(),
    val personnes: List<Contact> = emptyList(),
    val regles: List<Regle> = emptyList(),
    val ccOuverture: String = "",
    val toCloture: String = "",
    val ccCloture: String = "",
    val delaiElevee: Int = 2,
    val delaiMoyenne: Int = 3,
    val delaiFaible: Int = 7
) {
    fun delai(grav: String) = when (grav) { "Élevée" -> delaiElevee; "Faible" -> delaiFaible; else -> delaiMoyenne }

    fun toJson(): JSONObject = JSONObject().apply {
        put("organisation", organisation); put("pied", pied); put("constatePar", constatePar); put("signature", signature)
        put("zones", JSONArray(zones)); put("types", JSONArray(types)); put("services", JSONArray(services))
        put("personnes", JSONArray(personnes.map { JSONObject().put("nom", it.nom).put("email", it.email) }))
        put("regles", JSONArray(regles.map { JSONObject().put("service", it.service).put("zone", it.zone).put("responsable", it.responsable) }))
        put("ccOuverture", ccOuverture); put("toCloture", toCloture); put("ccCloture", ccCloture)
        put("delaiElevee", delaiElevee); put("delaiMoyenne", delaiMoyenne); put("delaiFaible", delaiFaible)
    }

    companion object {
        private fun JSONArray?.strings() = if (this == null) emptyList() else (0 until length()).map { optString(it) }.filter { it.isNotBlank() }
        private fun JSONArray?.objs() = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

        fun fromJson(o: JSONObject) = Config(
            organisation = o.optString("organisation"), pied = o.optString("pied"),
            constatePar = o.optString("constatePar"), signature = o.optString("signature"),
            zones = o.optJSONArray("zones").strings(), types = o.optJSONArray("types").strings(),
            services = o.optJSONArray("services").strings(),
            personnes = o.optJSONArray("personnes").objs().map { Contact(it.optString("nom"), it.optString("email")) },
            regles = o.optJSONArray("regles").objs().map { Regle(it.optString("service"), it.optString("zone"), it.optString("responsable")) },
            ccOuverture = o.optString("ccOuverture"), toCloture = o.optString("toCloture"), ccCloture = o.optString("ccCloture"),
            delaiElevee = o.optInt("delaiElevee", 2), delaiMoyenne = o.optInt("delaiMoyenne", 3), delaiFaible = o.optInt("delaiFaible", 7)
        )

        /** Types de danger courants, proposés seulement si l'utilisateur le demande. */
        val exemplesTypes = listOf(
            "Risque électrique", "Risque mécanique", "Risque de coupure / blessure", "Risque incendie / explosion",
            "Risque chimique", "Moyens de secours / évacuation", "Chute de hauteur", "Chute de plain-pied",
            "Chute d'objet / stockage", "Circulation / engins", "Manutention / ergonomie", "Énergie",
            "Dégradation infrastructure", "Nuisibles", "Nettoyage", "Ambiance de travail", "Comportement / EPI"
        )
    }
}

object Gravites { val all = listOf("Élevée", "Moyenne", "Faible") }

/** Valeurs saisies qui ne sont pas encore dans les listes : proposées à l'enregistrement. */
data class Learn(
    val zones: List<String>, val types: List<String>, val services: List<String>,
    val personnes: List<String>, val regle: Regle?
) {
    val isEmpty get() = zones.isEmpty() && types.isEmpty() && services.isEmpty() && personnes.isEmpty() && regle == null
}

object Rules {
    private fun norm(s: String) = s.trim().lowercase()
    fun splitNames(s: String) = s.split("/", ";", ",").map { it.trim() }.filter { it.isNotEmpty() }

    fun suggest(c: Config, service: String, zone: String): String {
        if (service.isBlank() && zone.isBlank()) return ""
        val r = c.regles.firstOrNull { norm(it.service) == norm(service) && norm(it.zone) == norm(zone) }
            ?: c.regles.firstOrNull { norm(it.service) == norm(service) && it.zone.trim() == "*" }
            ?: c.regles.firstOrNull { it.service.trim() == "*" && norm(it.zone) == norm(zone) }
        return r?.responsable.orEmpty()
    }

    fun learn(c: Config, t: Tag): Learn {
        fun missing(v: String, list: List<String>) = v.isNotBlank() && list.none { norm(it) == norm(v) }
        val people = splitNames(t.resp).filter { n -> "@" !in n && c.personnes.none { norm(it.nom) == norm(n) } }
        val rule = if (t.service.isNotBlank() && t.zone.isNotBlank() && t.resp.isNotBlank() &&
            norm(suggest(c, t.service, t.zone)) != norm(t.resp)) Regle(t.service.trim(), t.zone.trim(), t.resp.trim()) else null
        return Learn(
            zones = listOfNotNull(t.zone.trim().takeIf { missing(it, c.zones) }),
            types = listOfNotNull(t.type.trim().takeIf { missing(it, c.types) }),
            services = listOfNotNull(t.service.trim().takeIf { missing(it, c.services) }),
            personnes = people, regle = rule
        )
    }

    fun apply(c: Config, l: Learn): Config {
        val regles = if (l.regle == null) c.regles else
            c.regles.filterNot { norm(it.service) == norm(l.regle.service) && norm(it.zone) == norm(l.regle.zone) } + l.regle
        return c.copy(
            zones = c.zones + l.zones, types = c.types + l.types, services = c.services + l.services,
            personnes = c.personnes + l.personnes.map { Contact(it, "") }, regles = regles
        )
    }

    private val building = Regex(
        "fissur|\\bmur|ouverture mural|trou mural|escalier|grillage|cl[ôo]ture|toiture|chenau|avaloir|" +
            "joint de dilatation|moustiquaire|infiltration|canalisation|[ée]gout|siphon|rev[êe]tement|carrelage|plafond",
        RegexOption.IGNORE_CASE
    )

    fun isBuilding(t: Tag) = t.type.contains("infrastructure", true) || t.type.contains("bâtiment", true) ||
        building.containsMatchIn(t.desc + " " + t.empl)

    /** Noms -> emails connus ; renvoie (emails, noms sans email). */
    fun resolve(names: String, contacts: List<Contact>): Pair<List<String>, List<String>> {
        val emails = mutableListOf<String>()
        val missing = mutableListOf<String>()
        splitNames(names).forEach { n ->
            if ("@" in n) { emails += n; return@forEach }
            val c = contacts.firstOrNull { norm(it.nom) == norm(n) }
                ?: contacts.firstOrNull { norm(it.nom).split(" ").toSet() == norm(n).split(" ").toSet() }
            if (c != null && "@" in c.email) emails += c.email.trim() else missing += n
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
        if (t.isEmpty()) return null
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
    var proCached: Boolean
        get() = get("pro") == "1"
        set(v) = put("pro", if (v) "1" else "")
    var config: Config
        get() = runCatching { Config.fromJson(JSONObject(get("config", "{}"))) }.getOrDefault(Config())
        set(v) = put("config", v.toJson().toString())
    val sheetMode get() = url.startsWith("https://")
    var userName: String
        get() = get("userName")
        set(v) = put("userName", v)
    var userEmail: String
        get() = get("userEmail")
        set(v) = put("userEmail", v)
    var onboarded: Boolean
        get() = get("onboarded") == "1"
        set(v) = put("onboarded", if (v) "1" else "")
}

/** Tags enregistrés sur le téléphone (utilisé quand aucun Google Sheet n'est connecté). */
class LocalStore(private val ctx: Context) {
    private val file get() = File(ctx.filesDir, "tags.json")
    val photoDir get() = File(ctx.filesDir, "photos").apply { mkdirs() }

    fun all(): List<Tag> = runCatching {
        val a = JSONArray(file.readText())
        (0 until a.length()).map { Tag.fromApi(a.getJSONObject(it)).copy(date = a.getJSONObject(it).optString("date"), cible = a.getJSONObject(it).optString("cible")) }
    }.getOrDefault(emptyList())

    private fun write(list: List<Tag>) = file.writeText(JSONArray(list.map { it.toLocal() }).toString())

    fun nextNumber(): Int = (all().mapNotNull { it.n.toIntOrNull() }.maxOrNull() ?: 0) + 1

    fun add(t: Tag): Tag {
        val list = all()
        val id = (list.maxOfOrNull { it.row } ?: 0) + 1
        val tag = t.copy(row = id, n = t.n.ifBlank { nextNumber().toString() })
        write(list + tag)
        return tag
    }

    fun update(t: Tag) = write(all().map { if (it.row == t.row) t else it })
    fun close(rows: Set<Int>, apres: String = "") =
        write(all().map { if (it.row in rows) it.copy(statut = "Clôturé", apres = if (rows.size == 1 && apres.isNotBlank()) apres else it.apres) else it })
}
