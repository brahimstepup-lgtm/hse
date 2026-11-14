package app.safetytags

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.io.FileOutputStream

class MainVm(app: Application) : AndroidViewModel(app) {
    val prefs = Prefs(app)
    private val api = Api(prefs)
    private val local = LocalStore(app)
    private val msgChannel = Channel<String>(Channel.BUFFERED)
    val messages = msgChannel.receiveAsFlow()

    var cfg by mutableStateOf(prefs.config); private set
    var connected by mutableStateOf(false); private set
    private var nextNumber = 0

    // formulaire « Nouveau tag »
    var form by mutableStateOf(Tag())
    var note by mutableStateOf("")
    var photo by mutableStateOf<Bitmap?>(null); private set
    var fiche by mutableStateOf<Bitmap?>(null); private set
    var savedRow by mutableStateOf(0); private set
    var busy by mutableStateOf(""); private set
    var learn by mutableStateOf<Learn?>(null)

    // compte et logo
    var onboarded by mutableStateOf(prefs.onboarded); private set
    var userName by mutableStateOf(prefs.userName); private set
    var userEmail by mutableStateOf(prefs.userEmail); private set
    private val logoFile = File(app.filesDir, "logo.png")
    var logo by mutableStateOf<Bitmap?>(if (logoFile.exists()) BitmapFactory.decodeFile(logoFile.absolutePath) else null); private set

    // Safety Tags Pro
    var isPro by mutableStateOf(prefs.proCached); private set
    var showPro by mutableStateOf(false)
    private var idToken = ""
    private var tokenAt = 0L

    // liste des tags ouverts
    var open by mutableStateOf<List<Tag>>(emptyList()); private set

    fun setLang(l: String) { prefs.lang = l; L10n.lang = l }

    init {
        L10n.lang = prefs.lang
        form = blank()
        refreshNumber()
    }

    private fun blank(): Tag {
        val now = D.now()
        return Tag(date = now, cible = D.plusDays(now, cfg.delai("Moyenne").toLong()), par = cfg.constatePar)
    }

    fun toast(s: String) { msgChannel.trySend(s) }

    private suspend fun <T> task(label: String, block: suspend () -> T): T? {
        busy = label
        return try { block() } catch (e: Exception) { toast(e.message ?: "Erreur"); null } finally { busy = "" }
    }

    /* ---------- Safety Tags Pro ---------- */
    private fun updatePro(v: Boolean) { isPro = v; prefs.proCached = v }

    /** Ouvre la présentation Pro si l'utilisateur n'est pas Pro. */
    fun requirePro(): Boolean { if (!isPro) showPro = true; return isPro }

    private suspend fun token(activity: Activity): String {
        if (idToken.isNotBlank() && System.currentTimeMillis() - tokenAt < 50 * 60_000L) return idToken
        val t = Auth.silentToken(activity) ?: throw IOException(tr("Reconnectez-vous avec Google (Réglages › Compte)."))
        idToken = t; tokenAt = System.currentTimeMillis()
        return t
    }

    fun refreshPro(activity: Activity, showResult: Boolean = false) = viewModelScope.launch {
        if (userEmail.isBlank() || !ProApi.configured) {
            updatePro(false)
            if (showResult) toast(tr("Connectez-vous avec Google pour activer Pro."))
            return@launch
        }
        val r = runCatching { ProApi.status(token(activity)) }
        r.onSuccess { (pro, _) ->
            updatePro(pro)
            if (showResult) toast(if (pro) tr("✅ Safety Tags Pro est actif sur ce compte") else tr("Ce compte est en version gratuite."))
        }.onFailure { if (showResult) toast(it.message ?: tr("Vérification impossible")) }
    }

    /* ---------- compte ---------- */
    fun signInGoogle(activity: Activity) = viewModelScope.launch {
        val u = task(tr("Connexion avec Google…")) { Auth.signIn(activity) } ?: return@launch
        prefs.userName = u.name; prefs.userEmail = u.email
        userName = u.name; userEmail = u.email
        idToken = u.idToken; tokenAt = System.currentTimeMillis()
        if (ProApi.configured) runCatching { ProApi.status(u.idToken) }.onSuccess { updatePro(it.first) }
        var c = cfg
        if (c.constatePar.isBlank()) c = c.copy(constatePar = u.name)
        if (c.signature.isBlank()) c = c.copy(signature = u.name)
        if (c.personnes.none { it.email.equals(u.email, true) }) c = c.copy(personnes = c.personnes + Contact(u.name, u.email))
        saveConfig(c)
        if (form.par.isBlank()) form = form.copy(par = u.name)
        finishOnboarding()
        toast(tr("Bienvenue {0} !", u.name.split(" ").first()))
    }

    fun finishOnboarding() { prefs.onboarded = true; onboarded = true }

    fun signOut(ctx: Context) = viewModelScope.launch {
        Auth.signOut(ctx)
        prefs.userName = ""; prefs.userEmail = ""; userName = ""; userEmail = ""
        idToken = ""; tokenAt = 0L; updatePro(false)
        prefs.onboarded = false; onboarded = false
    }

    /* ---------- logo de l'entreprise ---------- */
    fun setLogo(ctx: Context, uri: Uri) = viewModelScope.launch {
        val b = withContext(Dispatchers.IO) {
            Img.load(ctx, uri, 600)?.also { bmp -> FileOutputStream(logoFile).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        }
        if (b == null) toast(tr("Image illisible")) else { logo = b; fiche = null; toast(tr("Logo enregistré : il apparaîtra sur les fiches")) }
    }

    fun clearLogo() { logoFile.delete(); logo = null; fiche = null }

    /* ---------- configuration ---------- */
    fun saveConfig(c: Config) {
        cfg = c; prefs.config = c
        if (form.par.isBlank() && c.constatePar.isNotBlank()) form = form.copy(par = c.constatePar)
    }

    fun refreshNumber() = viewModelScope.launch {
        if (!prefs.sheetMode) {
            connected = false
            nextNumber = withContext(Dispatchers.IO) { local.nextNumber() }
            if (savedRow == 0) form = form.copy(n = nextNumber.toString())
            return@launch
        }
        val d = runCatching { api.call("info").getJSONObject("data") }.getOrNull()
        connected = d != null
        if (d != null) {
            nextNumber = d.optInt("nextNumber")
            if (savedRow == 0) form = form.copy(n = nextNumber.toString())
        }
    }

    fun testSheet() = viewModelScope.launch {
        if (!prefs.sheetMode) { connected = false; toast(tr("Mode local : les tags sont gardés sur ce téléphone.")); refreshNumber(); return@launch }
        val d = task(tr("Connexion au Google Sheet…")) { api.call("info").getJSONObject("data") } ?: run { connected = false; return@launch }
        connected = true
        nextNumber = d.optInt("nextNumber")
        if (savedRow == 0) form = form.copy(n = nextNumber.toString())
        toast(tr("Connecté · prochain tag N° {0}", nextNumber))
    }

    /** Importe les onglets regles / contacts / parametres du Sheet dans les Réglages. */
    fun importFromSheet() = viewModelScope.launch {
        val d = task(tr("Import depuis le Sheet…")) { api.call("info").getJSONObject("data") } ?: return@launch
        val regles = d.optJSONArray("regles").objs().map {
            Regle(it.optString("categorie").ifBlank { it.optString("service") }, it.optString("etage").ifBlank { it.optString("zone") }, it.optString("responsable"))
        }
        val contacts = d.optJSONArray("contacts").objs().map { Contact(it.optString("nom"), it.optString("email")) }
        val p = d.optJSONObject("params") ?: JSONObject()
        var c = cfg
        val names = contacts.map { it.nom.lowercase() }.toSet()
        c = c.copy(
            personnes = c.personnes.filter { it.nom.lowercase() !in names } + contacts,
            regles = (c.regles + regles).distinctBy { (it.service + "|" + it.zone).lowercase() },
            services = (c.services + regles.map { it.service }.filter { it != "*" }).distinct(),
            zones = (c.zones + regles.map { it.zone }.filter { it != "*" }).distinct(),
            ccOuverture = p.optString("cc_ouverture").ifBlank { c.ccOuverture },
            toCloture = p.optString("to_cloture").ifBlank { c.toCloture },
            ccCloture = p.optString("cc_cloture").ifBlank { c.ccCloture },
            constatePar = p.optString("constate_par").ifBlank { c.constatePar },
            signature = p.optString("signature").replace("\\n", "\n").ifBlank { c.signature }
        )
        saveConfig(c)
        toast(tr("Importé : {0} contact(s), {1} règle(s)", contacts.size, regles.size))
    }

    /* ---------- nouveau tag ---------- */
    fun edit(t: Tag) { form = t; fiche = null }

    fun setPhoto(ctx: Context, uri: Uri) = viewModelScope.launch {
        val b = withContext(Dispatchers.IO) { Img.load(ctx, uri) }
        if (b == null) toast(tr("Image illisible")) else { photo = b; fiche = null }
    }

    fun suggestResp(service: String = form.service, zone: String = form.zone) {
        val s = Rules.suggest(cfg, service, zone)
        if (s.isNotBlank()) edit(form.copy(resp = s))
    }

    fun setGrav(g: String) = edit(form.copy(grav = g, cible = D.plusDays(form.date, cfg.delai(g).toLong())))

    fun aiFill(activity: Activity) = viewModelScope.launch {
        if (!requirePro()) return@launch
        if (photo == null && note.isBlank()) { toast(tr("Ajoutez une photo ou une note.")); return@launch }
        val r = task(tr("L’IA analyse la photo…")) {
            val img = photo?.let { Img.toBase64(Img.scaleMax(it, 1280), 80) }
            Ai.parse(ProApi.ai(token(activity), Ai.prompt(note, cfg), img))
        } ?: return@launch
        val grav = r.optString("gravite").takeIf { it in Gravites.all } ?: form.grav
        val zone = r.optString("zone").ifBlank { form.zone }
        val service = r.optString("service").ifBlank { form.service }
        val resp = r.optString("responsable").ifBlank { Rules.suggest(cfg, service, zone) }.ifBlank { form.resp }
        edit(form.copy(
            type = r.optString("type").ifBlank { form.type }, grav = grav, zone = zone, service = service,
            empl = r.optString("emplacement").ifBlank { form.empl },
            desc = r.optString("description").ifBlank { form.desc },
            risque = r.optString("risque").ifBlank { form.risque },
            action = r.optString("action_urgente").ifBlank { form.action },
            prop = r.optString("proposition").ifBlank { form.prop },
            cible = D.plusDays(form.date, cfg.delai(grav).toLong()),
            resp = resp
        ))
        toast(tr("Champs remplis : relisez avant d’enregistrer."))
    }

    fun buildFiche(): Bitmap = Fiche.render(form, photo, cfg, logo).also { fiche = it }

    private fun proposeLearning() {
        val l = Rules.learn(cfg, form)
        if (!l.isEmpty) learn = l
    }

    fun acceptLearning(l: Learn) { saveConfig(Rules.apply(cfg, l)); learn = null; toast(tr("Ajouté à vos réglages")) }

    fun save() = viewModelScope.launch {
        if (savedRow != 0) { toast(tr("Déjà enregistré.")); return@launch }
        if (form.desc.isBlank()) { toast(tr("La description est obligatoire.")); return@launch }
        if (prefs.sheetMode) {
            val body = JSONObject().put("tag", form.toApi())
            photo?.let { body.put("photo", Img.toBase64(Img.scaleMax(it, 1600), 88)) }
            val d = task(tr("Enregistrement dans le Sheet et Drive…")) { api.call("add", body).getJSONObject("data") } ?: return@launch
            savedRow = d.optInt("row")
            nextNumber = d.optInt("n") + 1
            edit(form.copy(n = d.optString("n"), row = savedRow, photo = d.optString("photo")))
            toast(tr("Tag N° {0} enregistré dans le Sheet (ligne {1})", form.n, savedRow))
        } else {
            val saved = task(tr("Enregistrement sur le téléphone…")) {
                withContext(Dispatchers.IO) {
                    var t = local.add(form)
                    photo?.let { b ->
                        val f = File(local.photoDir, "tag_${t.row}.jpg")
                        FileOutputStream(f).use { b.compress(Bitmap.CompressFormat.JPEG, 88, it) }
                        t = t.copy(photo = f.absolutePath); local.update(t)
                    }
                    t
                }
            } ?: return@launch
            savedRow = saved.row
            nextNumber = (saved.n.toIntOrNull() ?: 0) + 1
            edit(form.copy(n = saved.n, row = saved.row, photo = saved.photo))
            toast(tr("Tag N° {0} enregistré sur le téléphone", saved.n))
        }
        proposeLearning()
        if (open.isNotEmpty()) loadOpen()
    }

    fun newTag() {
        form = blank().copy(n = if (nextNumber > 0) nextNumber.toString() else "")
        photo = null; fiche = null; note = ""; savedRow = 0
    }

    /* ---------- emails ---------- */
    private fun shortTitle(t: Tag): String {
        val place = t.empl.ifBlank { t.desc.take(60) }
        return tr("Safety Tag N° {0} – {1}", t.n, place) + if (t.zone.isNotBlank()) " (${t.zone})" else ""
    }

    private fun greeting(names: String): String {
        val list = Rules.splitNames(names).filter { "@" !in it }
        return if (list.size == 1) tr("Bonjour {0},", list[0].split(" ").first()) else tr("Bonjour à tous,")
    }

    private fun signature(t: Tag) = cfg.signature.ifBlank { t.par }

    private fun openBody(t: Tag) = buildString {
        appendLine(greeting(t.resp)); appendLine()
        appendLine(tr("Suite au constat du {0}, un Safety Tag a été ouvert sous votre responsabilité :", D.day(t.date))); appendLine()
        appendLine(tr("Tag N° {0} – {1}", t.n, t.empl) + if (t.zone.isNotBlank()) ", ${t.zone}" else "")
        appendLine(tr("- Anomalie : {0}", t.desc))
        appendLine(tr("- Gravité : {0}", tr(t.grav)))
        if (t.risque.isNotBlank()) appendLine(tr("- Risque : {0}", t.risque))
        if (t.action.isNotBlank()) appendLine(tr("- Action urgente : {0}", t.action))
        if (t.prop.isNotBlank()) appendLine(tr("- Proposition : {0}", t.prop))
        appendLine(tr("- Date cible : {0}", D.day(t.cible))); appendLine()
        appendLine(tr("Vous trouverez la fiche du tag en pièce jointe. Merci de nous informer dès la réalisation de l'action.")); appendLine()
        appendLine(tr("Cordialement,")); append(signature(t))
    }

    private fun closeBody(t: Tag) = buildString {
        appendLine(tr("Bonjour,")); appendLine()
        appendLine(tr("Le Safety Tag N° {0} ({1}) est clôturé.", t.n, t.empl + if (t.zone.isNotBlank()) ", ${t.zone}" else ""))
        appendLine(tr("- Anomalie : {0}", t.desc))
        appendLine(tr("- Responsable : {0}", t.resp)); appendLine()
        appendLine(tr("Cordialement,")); append(signature(t))
    }

    private fun sendMail(ctx: Context, toNames: String, ccNames: String, subject: String, body: String, bmp: Bitmap?, fileName: String) {
        val (to, miss1) = Rules.resolve(toNames, cfg.personnes)
        val (cc0, miss2) = Rules.resolve(ccNames, cfg.personnes)
        val cc = cc0.filter { it !in to }
        val missing = (miss1 + miss2).distinct()
        if (missing.isNotEmpty()) toast(tr("Email inconnu pour : {0} (Réglages › Personnes)", missing.joinToString(", ")))
        val uri = bmp?.let { Share.savePng(ctx, it, fileName) }
        Share.email(ctx, to, cc, subject, body, uri, prefs.mailApp)
    }

    fun emailNew(ctx: Context) {
        if (form.resp.isBlank()) { toast(tr("Indiquez le responsable.")); return }
        val bmp = fiche ?: buildFiche()
        val name = "Fiche_Tag_${form.n}.png"
        if (form.statut == "Clôturé") sendMail(ctx, cfg.toCloture, cfg.ccCloture, tr("Clôture : {0}", shortTitle(form)), closeBody(form), bmp, name)
        else sendMail(ctx, form.resp, cfg.ccOuverture, shortTitle(form), openBody(form), bmp, name)
        if (savedRow == 0) proposeLearning()
    }

    fun shareNew(ctx: Context) {
        val bmp = fiche ?: buildFiche()
        Share.image(ctx, Share.savePng(ctx, bmp, "Fiche_Tag_${form.n}.png"), shortTitle(form))
    }

    /* ---------- tags ouverts ---------- */
    fun loadOpen() = viewModelScope.launch {
        val list = if (prefs.sheetMode)
            task(tr("Chargement des tags ouverts…")) { api.call("list", JSONObject().put("status", "open")).getJSONArray("data").objs().map { Tag.fromApi(it) } }
        else withContext(Dispatchers.IO) { local.all().filter { it.statut != "Clôturé" } }
        if (list != null) open = list.reversed()
    }

    private suspend fun photoOf(t: Tag): Bitmap? = if (!prefs.sheetMode)
        withContext(Dispatchers.IO) { if (t.photo.isNotBlank() && File(t.photo).exists()) BitmapFactory.decodeFile(t.photo) else null }
    else runCatching {
        val b64 = api.call("photo", JSONObject().put("row", t.row)).getJSONObject("data").optString("base64")
        if (b64.isBlank()) null else Img.fromBase64(b64)
    }.getOrNull()

    fun emailExisting(ctx: Context, t: Tag, closed: Boolean = false) = viewModelScope.launch {
        val pic = task(tr("Préparation de la fiche…")) { photoOf(t) }
        val tag = t.copy(statut = if (closed) "Clôturé" else t.statut, par = t.par.ifBlank { cfg.constatePar })
        val bmp = Fiche.render(tag, pic, cfg, logo)
        val name = "Fiche_Tag_${tag.n}.png"
        if (closed) sendMail(ctx, cfg.toCloture, cfg.ccCloture, tr("Clôture : {0}", shortTitle(tag)), closeBody(tag), bmp, name)
        else sendMail(ctx, tag.resp, cfg.ccOuverture, shortTitle(tag), openBody(tag), bmp, name)
    }

    fun closeRows(rows: List<Int>, after: Bitmap? = null, onDone: () -> Unit = {}) = viewModelScope.launch {
        if (rows.isEmpty()) return@launch
        val ok = if (prefs.sheetMode) {
            val body = JSONObject().put("rows", JSONArray(rows))
            after?.let { body.put("photoAfter", Img.toBase64(Img.scaleMax(it, 1600), 88)) }
            task(tr("Clôture en cours…")) { api.call("close", body) } != null
        } else {
            withContext(Dispatchers.IO) {
                var path = ""
                if (after != null && rows.size == 1) {
                    val f = File(local.photoDir, "apres_${rows[0]}.jpg")
                    FileOutputStream(f).use { after.compress(Bitmap.CompressFormat.JPEG, 88, it) }
                    path = f.absolutePath
                }
                local.close(rows.toSet(), path)
            }
            true
        }
        if (!ok) return@launch
        val set = rows.toSet()
        open = open.filter { it.row !in set }
        toast(tr("{0} tag(s) clôturé(s)", rows.size))
        onDone()
    }

    fun updateResp(t: Tag, resp: String) = viewModelScope.launch {
        val nt: Tag
        if (prefs.sheetMode) {
            nt = task(tr("Mise à jour…")) {
                Tag.fromApi(api.call("update", JSONObject().put("row", t.row).put("fields", JSONObject().put("resp", resp))).getJSONObject("data"))
            } ?: return@launch
        } else {
            nt = t.copy(resp = resp)
            withContext(Dispatchers.IO) { local.update(nt) }
        }
        open = open.map { if (it.row == nt.row) nt else it }
        toast(tr("Responsable mis à jour"))
    }

    /* ---------- Pro : tableau de bord, rapport PDF, relances ---------- */
    var allTags by mutableStateOf<List<Tag>>(emptyList()); private set

    fun loadAll() = viewModelScope.launch {
        val list = if (prefs.sheetMode)
            task(tr("Chargement des statistiques…")) { api.call("list", JSONObject().put("status", "all")).getJSONArray("data").objs().map { Tag.fromApi(it) } }
        else withContext(Dispatchers.IO) { local.all() }
        if (list != null) { allTags = list; open = list.filter { it.statut != "Clôturé" }.reversed() }
    }

    fun isLate(t: Tag): Boolean {
        if (t.statut == "Clôturé") return false
        val c = D.parse(t.cible)?.toLocalDate() ?: return false
        return c.isBefore(LocalDate.now())
    }

    fun remind(ctx: Context, person: String, tags: List<Tag>) {
        if (!requirePro()) return
        val body = buildString {
            appendLine(if (Rules.splitNames(person).size == 1) tr("Bonjour {0},", person.split(" ").first()) else tr("Bonjour,")); appendLine()
            appendLine(tr("Les Safety Tags suivants sous votre responsabilité ont dépassé leur date cible :")); appendLine()
            tags.sortedBy { D.parse(it.cible) }.forEach { t ->
                appendLine(tr("- N° {0} ({1}) : {2}", t.n, t.zone.ifBlank { "—" }, t.desc))
                appendLine(tr("  Date cible : {0} · Gravité : {1}", D.day(t.cible), tr(t.grav)))
            }
            appendLine(); appendLine(tr("Merci de nous indiquer l’avancement ou la date de réalisation prévue.")); appendLine()
            appendLine(tr("Cordialement,")); append(cfg.signature.ifBlank { cfg.constatePar })
        }
        sendMail(ctx, person, cfg.ccOuverture, tr("Relance : {0} Safety Tag(s) en retard", tags.size), body, null, "")
    }

    fun exportPdf(ctx: Context, list: List<Tag>) {
        if (!requirePro()) return
        val f = Report.pdf(ctx, list, cfg, logo) { isLate(it) }
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(Intent.EXTRA_SUBJECT, tr("Rapport Safety Tags – {0}", D.day(D.now())))
        }, tr("Partager le rapport PDF")))
    }

    /* ---------- export / import ---------- */
    fun exportCsv(ctx: Context, list: List<Tag>) {
        fun q(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
        val head = listOf("N°", "Date", "Date cible", "Type", "Gravité", "Zone", "Service", "Emplacement", "Description",
            "Risque principal", "Action urgente", "Proposition", "Statut", "Responsable").map { tr(it) }
        val csv = buildString {
            append('﻿'); appendLine(head.joinToString(";") { q(it) })
            list.forEach { t ->
                appendLine(listOf(t.n, t.date, t.cible, t.type, t.grav, t.zone, t.service, t.empl, t.desc, t.risque,
                    t.action, t.prop, t.statut, t.resp).joinToString(";") { q(it) })
            }
        }
        val f = File(File(ctx.cacheDir, "exports").apply { mkdirs() }, "safety_tags.csv").apply { writeText(csv) }
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, tr("Exporter les tags")))
    }

    fun exportConfig(ctx: Context) {
        val f = File(File(ctx.cacheDir, "exports").apply { mkdirs() }, "safety_tags_reglages.json")
        val json = cfg.toJson()
        logo?.let { json.put("logo", Img.toBase64Png(it)) }
        f.writeText(json.toString(2))
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "application/json"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, tr("Partager les réglages")))
    }

    fun importConfig(ctx: Context, uri: Uri) = viewModelScope.launch {
        val json = withContext(Dispatchers.IO) {
            runCatching { ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { JSONObject(it.readText()) } }.getOrNull()
        }
        if (json == null) { toast(tr("Fichier de réglages illisible")); return@launch }
        saveConfig(Config.fromJson(json))
        json.optString("logo").takeIf { it.isNotBlank() }?.let { b64 ->
            Img.fromBase64(b64)?.let { bmp ->
                withContext(Dispatchers.IO) { FileOutputStream(logoFile).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
                logo = bmp
            }
        }
        toast(tr("Réglages importés"))
    }
}

private fun JSONArray?.objs(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
