package app.safetytags

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class MainVm(app: Application) : AndroidViewModel(app) {
    val prefs = Prefs(app)
    private val api = Api(prefs)
    private val msgChannel = Channel<String>(Channel.BUFFERED)
    val messages = msgChannel.receiveAsFlow()

    // données du Sheet (onglets regles / contacts / parametres)
    var regles by mutableStateOf<List<Regle>>(emptyList()); private set
    var contacts by mutableStateOf<List<Contact>>(emptyList())
    var params by mutableStateOf<Map<String, String>>(emptyMap()); private set
    var connected by mutableStateOf(false); private set
    private var nextNumber = 0

    // formulaire « Nouveau tag »
    var form by mutableStateOf(blank())
    var categorie by mutableStateOf("Technique")
    var sharp by mutableStateOf(false)
    var note by mutableStateOf("")
    var photo by mutableStateOf<Bitmap?>(null); private set
    var fiche by mutableStateOf<Bitmap?>(null); private set
    var savedRow by mutableStateOf(0); private set
    var busy by mutableStateOf(""); private set

    // tags ouverts
    var open by mutableStateOf<List<Tag>>(emptyList()); private set
    var loadingList by mutableStateOf(false); private set

    init { restoreCache(); loadInfo() }

    private fun blank(): Tag {
        val now = D.now()
        return Tag(date = now, cible = D.plusDays(now, 3), statut = "Ouvert", grav = "Moyenne")
    }

    fun toast(s: String) { msgChannel.trySend(s) }

    private suspend fun <T> task(label: String, block: suspend () -> T): T? {
        busy = label
        return try { block() } catch (e: Exception) { toast(e.message ?: "Erreur"); null } finally { busy = "" }
    }

    /* ---------- configuration ---------- */
    private fun applyInfo(d: JSONObject) {
        regles = d.optJSONArray("regles").objs().map {
            Regle(it.optString("categorie"), it.optString("etage"), it.optString("responsable"))
        }
        contacts = d.optJSONArray("contacts").objs().map { Contact(it.optString("nom"), it.optString("email")) }
        val p = d.optJSONObject("params") ?: JSONObject()
        params = p.keys().asSequence().associateWith { p.optString(it) }
        nextNumber = d.optInt("nextNumber")
        if (savedRow == 0 && nextNumber > 0) form = form.copy(n = nextNumber.toString())
        if (form.par.isBlank()) form = form.copy(par = params["constate_par"].orEmpty())
    }

    private fun restoreCache() = runCatching { if (prefs.cache.isNotBlank()) applyInfo(JSONObject(prefs.cache)) }

    fun loadInfo(showOk: Boolean = false) = viewModelScope.launch {
        if (prefs.url.isBlank()) { toast("Bienvenue : configurez l’URL du script et le jeton dans Réglages."); return@launch }
        val d = task("Connexion au Sheet…") { api.call("info").getJSONObject("data") } ?: return@launch
        prefs.cache = d.toString()
        applyInfo(d)
        connected = true
        if (showOk) toast("Connecté · prochain tag N° $nextNumber")
    }

    fun saveContacts(list: List<Contact>) = viewModelScope.launch {
        val arr = JSONArray()
        list.filter { it.nom.isNotBlank() }.forEach { arr.put(JSONObject().put("nom", it.nom.trim()).put("email", it.email.trim())) }
        task("Enregistrement des contacts…") { api.call("saveContacts", JSONObject().put("contacts", arr)) } ?: return@launch
        contacts = list.filter { it.nom.isNotBlank() }
        toast("Contacts enregistrés dans le Sheet")
        loadInfo()
    }

    /* ---------- nouveau tag ---------- */
    fun edit(t: Tag) { form = t; fiche = null }

    fun setPhoto(ctx: Context, uri: Uri) = viewModelScope.launch {
        val b = withContext(Dispatchers.IO) { Img.load(ctx, uri) }
        if (b == null) toast("Image illisible") else { photo = b; fiche = null }
    }

    fun suggestResp() {
        val s = Rules.suggest(regles, categorie, form.zone, sharp)
        if (s.isNotBlank()) edit(form.copy(resp = s))
    }

    fun aiFill() = viewModelScope.launch {
        if (prefs.aiKey.isBlank()) { toast("Ajoutez une clé API Anthropic dans Réglages pour utiliser l’IA."); return@launch }
        if (photo == null && note.isBlank()) { toast("Ajoutez une photo ou une note."); return@launch }
        val r = task("L’IA analyse la photo…") { Ai.fill(prefs.aiKey, prefs.aiModel, note, photo, regles) } ?: return@launch
        r.optString("categorie").takeIf { it in Lists.categories }?.let { categorie = it }
        sharp = r.optBoolean("partie_tranchante", sharp)
        val etage = r.optString("etage").ifBlank { form.zone }
        val grav = r.optString("gravite").takeIf { it in Lists.gravites } ?: form.grav
        val jours = r.optInt("jours_cible", 0)
        val resp = r.optString("responsable").ifBlank { Rules.suggest(regles, categorie, etage, sharp) }.ifBlank { form.resp }
        edit(form.copy(
            type = r.optString("type").ifBlank { form.type }, grav = grav, zone = etage,
            empl = r.optString("emplacement").ifBlank { form.empl },
            desc = r.optString("description").ifBlank { form.desc },
            risque = r.optString("risque").ifBlank { form.risque },
            action = r.optString("action_urgente").ifBlank { form.action },
            prop = r.optString("proposition").ifBlank { form.prop },
            cible = if (jours > 0) D.plusDays(form.date, jours.toLong()) else form.cible,
            resp = resp
        ))
        toast("Champs remplis : relisez avant d’enregistrer.")
    }

    fun buildFiche(): Bitmap = Fiche.render(form, photo).also { fiche = it }

    fun save() = viewModelScope.launch {
        if (savedRow != 0) { toast("Déjà enregistré (ligne $savedRow)."); return@launch }
        if (form.desc.isBlank() || form.resp.isBlank()) { toast("Description et responsable sont obligatoires."); return@launch }
        val body = JSONObject().put("tag", form.toApi())
        photo?.let { body.put("photo", Img.toBase64(Img.scaleMax(it, 1600), 88)) }
        val d = task("Enregistrement dans le Sheet et Drive…") { api.call("add", body).getJSONObject("data") } ?: return@launch
        savedRow = d.optInt("row")
        nextNumber = d.optInt("n") + 1
        edit(form.copy(n = d.optString("n"), row = savedRow, photo = d.optString("photo")))
        toast("Tag N° ${form.n} enregistré (ligne $savedRow)")
        if (open.isNotEmpty()) loadOpen()
    }

    fun newTag() {
        val keepPar = form.par
        form = blank().copy(n = if (nextNumber > 0) nextNumber.toString() else "", par = keepPar)
        photo = null; fiche = null; note = ""; sharp = false; savedRow = 0
    }

    private fun shortTitle(t: Tag): String {
        val place = t.empl.ifBlank { t.desc.take(60) }
        return "Safety Tag N° ${t.n} – $place" + if (t.zone.isNotBlank()) " (${t.zone})" else ""
    }

    private fun greeting(names: String): String {
        val list = names.split("/").map { it.trim() }.filter { it.isNotEmpty() }
        return if (list.size == 1) "Bonjour Si ${list[0].split(" ").first()}," else "Bonjour à tous,"
    }

    private fun signature() = params["signature"].orEmpty().replace("\\n", "\n").ifBlank { form.par }

    private fun openBody(t: Tag) = buildString {
        appendLine(greeting(t.resp)); appendLine()
        appendLine("Suite au constat HSE du ${D.day(t.date)}, un Safety Tag a été ouvert sous votre responsabilité :"); appendLine()
        appendLine("Tag N° ${t.n} – ${t.empl}" + if (t.zone.isNotBlank()) ", ${t.zone}" else "")
        appendLine("- Anomalie : ${t.desc}")
        appendLine("- Gravité : ${t.grav}")
        appendLine("- Risque : ${t.risque}")
        appendLine("- Action urgente : ${t.action}")
        appendLine("- Proposition : ${t.prop}")
        appendLine("- Date cible : ${D.day(t.cible)}"); appendLine()
        appendLine("Vous trouverez la fiche du tag en pièce jointe. Merci de nous informer dès la réalisation de l'action."); appendLine()
        appendLine("Cordialement,"); append(signature())
    }

    private fun closeBody(t: Tag) = buildString {
        appendLine("Bonjour,"); appendLine()
        appendLine("Le Safety Tag N° ${t.n} (${t.empl}${if (t.zone.isNotBlank()) ", ${t.zone}" else ""}) est clôturé.")
        appendLine("- Anomalie : ${t.desc}")
        appendLine("- Responsable : ${t.resp}"); appendLine()
        appendLine("Cordialement,"); append(signature())
    }

    private fun sendMail(ctx: Context, toNames: String, ccNames: String, subject: String, body: String, bmp: Bitmap?, fileName: String) {
        val (to, miss1) = Rules.resolve(toNames, contacts)
        val (cc0, miss2) = Rules.resolve(ccNames, contacts)
        val cc = cc0.filter { it !in to }
        val missing = (miss1 + miss2).distinct()
        if (missing.isNotEmpty()) toast("Email inconnu pour : ${missing.joinToString(", ")} (Réglages › Contacts)")
        val uri = bmp?.let { Share.savePng(ctx, it, fileName) }
        Share.email(ctx, to, cc, subject, body, uri)
    }

    fun emailNew(ctx: Context) {
        if (form.resp.isBlank()) { toast("Indiquez le responsable."); return }
        if (savedRow == 0) toast("Astuce : enregistrez aussi le tag dans le Sheet.")
        val bmp = fiche ?: buildFiche()
        if (form.statut == "Clôturé") sendMail(ctx, params["to_cloture"].orEmpty(), params["cc_cloture"].orEmpty(),
            "Clôture " + shortTitle(form), closeBody(form), bmp, "Fiche_Tag_${form.n}.png")
        else sendMail(ctx, form.resp, params["cc_ouverture"].orEmpty(), shortTitle(form), openBody(form), bmp, "Fiche_Tag_${form.n}.png")
    }

    fun shareNew(ctx: Context) {
        val bmp = fiche ?: buildFiche()
        Share.image(ctx, Share.savePng(ctx, bmp, "Fiche_Tag_${form.n}.png"), shortTitle(form))
    }

    /* ---------- tags ouverts ---------- */
    fun loadOpen() = viewModelScope.launch {
        loadingList = true
        val d = task("Chargement des tags ouverts…") { api.call("list", JSONObject().put("status", "open")).getJSONArray("data") }
        loadingList = false
        if (d != null) open = d.objs().map { Tag.fromApi(it) }.reversed()
    }

    private suspend fun fetchPhoto(t: Tag): Bitmap? = runCatching {
        val b64 = api.call("photo", JSONObject().put("row", t.row)).getJSONObject("data").optString("base64")
        if (b64.isBlank()) null else Img.fromBase64(b64)
    }.getOrNull()

    fun emailExisting(ctx: Context, t: Tag, closed: Boolean = false) = viewModelScope.launch {
        val pic = task("Préparation de la fiche…") { fetchPhoto(t) }
        val tag = t.copy(statut = if (closed) "Clôturé" else t.statut, par = t.par.ifBlank { params["constate_par"].orEmpty() })
        val bmp = Fiche.render(tag, pic)
        if (closed) sendMail(ctx, params["to_cloture"].orEmpty(), params["cc_cloture"].orEmpty(),
            "Clôture " + shortTitle(tag), closeBody(tag), bmp, "Fiche_Tag_${tag.n}.png")
        else sendMail(ctx, tag.resp, params["cc_ouverture"].orEmpty(), shortTitle(tag), openBody(tag), bmp, "Fiche_Tag_${tag.n}.png")
    }

    fun closeRows(rows: List<Int>, after: Bitmap? = null, onDone: () -> Unit = {}) = viewModelScope.launch {
        if (rows.isEmpty()) return@launch
        val body = JSONObject().put("rows", JSONArray(rows))
        after?.let { body.put("photoAfter", Img.toBase64(Img.scaleMax(it, 1600), 88)) }
        val d = task("Clôture en cours…") { api.call("close", body).getJSONObject("data") } ?: return@launch
        val set = rows.toSet()
        open = open.filter { it.row !in set }
        toast("${d.optInt("closed")} tag(s) clôturé(s)")
        onDone()
    }

    fun updateResp(t: Tag, resp: String) = viewModelScope.launch {
        val d = task("Mise à jour…") {
            api.call("update", JSONObject().put("row", t.row).put("fields", JSONObject().put("resp", resp))).getJSONObject("data")
        } ?: return@launch
        val nt = Tag.fromApi(d)
        open = open.map { if (it.row == nt.row) nt else it }
        toast("Responsable mis à jour")
    }
}

private fun JSONArray?.objs(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
