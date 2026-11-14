@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.safetytags

import android.app.Activity
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Red = Color(0xFFC62828)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val scheme = if (isSystemInDarkTheme())
        darkColorScheme(primary = Color(0xFFEF5350), secondary = Color(0xFF90A4AE), onPrimary = Color.White)
    else lightColorScheme(primary = Red, secondary = Color(0xFF455A64), onPrimary = Color.White)
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
fun AppRoot(vm: MainVm) {
    if (!vm.onboarded) { LoginScreen(vm); return }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snack = remember { SnackbarHostState() }
    val activity = LocalContext.current as Activity
    LaunchedEffect(Unit) { vm.refreshPro(activity) }
    LaunchedEffect(Unit) { vm.messages.collect { snack.showSnackbar(it) } }
    val titles = listOf("Nouveau tag", "Tags ouverts", "Tableau de bord", "Réglages")
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(painterResource(R.drawable.logo_app), null, Modifier.size(30.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(titles[tab], fontWeight = FontWeight.Bold)
                        if (vm.isPro) { Spacer(Modifier.width(8.dp)); ProBadge(onRed = true) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Red, titleContentColor = Color.White,
                    actionIconContentColor = Color.White),
                actions = {
                    if (tab == 0) IconButton(onClick = { vm.newTag() }) { Icon(Icons.Default.Add, "Nouveau tag") }
                    if (tab == 1) IconButton(onClick = { vm.loadOpen() }) { Icon(Icons.Default.Refresh, "Actualiser") }
                    if (tab == 2 && vm.isPro) IconButton(onClick = { vm.loadAll() }) { Icon(Icons.Default.Refresh, "Actualiser") }
                }
            )
        },
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Icon(Icons.Default.Edit, null) }, label = { Text("Nouveau") })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Icon(Icons.Default.List, null) }, label = { Text("Ouverts") })
                NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, icon = { Icon(Icons.Default.Star, null) }, label = { Text("Tableau") })
                NavigationBarItem(selected = tab == 3, onClick = { tab = 3 }, icon = { Icon(Icons.Default.Settings, null) }, label = { Text("Réglages") })
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (vm.busy.isNotEmpty()) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(vm.busy, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
            when (tab) {
                0 -> NewTagScreen(vm)
                1 -> OpenTagsScreen(vm)
                2 -> DashboardScreen(vm)
                else -> SettingsScreen(vm)
            }
        }
    }
    if (vm.showPro) ProDialog(vm)
}

/* ---------------- Pro ---------------- */

@Composable
fun ProBadge(onRed: Boolean = false) {
    Box(Modifier.background(if (onRed) Color.White else Color(0xFFFFB300), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 1.dp)) {
        Text("PRO", color = if (onRed) Red else Color(0xFF3E2723), fontWeight = FontWeight.Bold, fontSize = 11.sp)
    }
}

private val proFeatures = listOf(
    "Remplissage automatique par l’IA à partir de la photo",
    "Tableau de bord : tags ouverts, en retard, par responsable et par zone",
    "Rapport PDF des tags à partager en réunion",
    "Relances groupées des tags en retard, par responsable"
)

@Composable
fun ProFeatures() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        proFeatures.forEach { f -> Row { Text("★ ", color = Color(0xFFFFB300)); Text(f) } }
    }
}

@Composable
fun ProDialog(vm: MainVm) {
    val activity = LocalContext.current as Activity
    AlertDialog(
        onDismissRequest = { vm.showPro = false },
        title = { Row(verticalAlignment = Alignment.CenterVertically) { Text("Safety Tags "); ProBadge() } },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Cette fonction fait partie de Safety Tags Pro.")
                ProFeatures()
                Text(if (vm.userEmail.isBlank()) "Connectez-vous avec Google (Réglages › Compte) pour activer Pro sur votre compte."
                    else "L’abonnement Pro arrive bientôt sur Google Play. Pour un accès anticipé, contactez le développeur depuis la fiche Google Play.",
                    style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        },
        confirmButton = { TextButton(onClick = { vm.showPro = false; vm.refreshPro(activity, showResult = true) }) { Text("Vérifier mon statut") } },
        dismissButton = { TextButton(onClick = { vm.showPro = false }) { Text("Fermer") } }
    )
}

@Composable
private fun Kpi(label: String, value: Int, color: Color, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f))) {
        Column(Modifier.padding(12.dp)) {
            Text(value.toString(), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = color)
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun Bars(title: String, data: List<Pair<String, Int>>, color: Color) {
    if (data.isEmpty()) return
    Section(title)
    val max = data.maxOf { it.second }.coerceAtLeast(1)
    data.forEach { (k, v) ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
            Text(k.ifBlank { "—" }, modifier = Modifier.width(130.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall)
            Box(Modifier.weight(1f).height(16.dp)) {
                Box(Modifier.fillMaxWidth(v.toFloat() / max).height(16.dp).background(color, RoundedCornerShape(4.dp)))
            }
            Text(" $v", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(36.dp))
        }
    }
}

@Composable
fun DashboardScreen(vm: MainVm) {
    val ctx = LocalContext.current
    val activity = ctx as Activity
    if (!vm.isPro) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Lock, null, tint = Red, modifier = Modifier.size(48.dp))
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Safety Tags ", fontSize = 22.sp, fontWeight = FontWeight.Bold); ProBadge() }
            ProFeatures()
            Button(onClick = { vm.refreshPro(activity, showResult = true) }) { Text("Vérifier mon statut Pro") }
            Text(if (vm.userEmail.isBlank()) "Connectez-vous avec Google pour activer Pro sur votre compte."
                else "Compte : ${vm.userEmail}", style = MaterialTheme.typography.bodySmall, color = Color.Gray, textAlign = TextAlign.Center)
        }
        return
    }
    LaunchedEffect(Unit) { vm.loadAll() }
    val all = vm.allTags
    val open = all.filter { it.statut != "Clôturé" }
    val late = open.filter { vm.isLate(it) }
    val month = D.now().substring(3, 10)
    val closedMonth = all.count { it.statut == "Clôturé" && D.day(it.date).endsWith(month) }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Kpi("Ouverts", open.size, Color(0xFF1565C0), Modifier.weight(1f))
            Kpi("En retard", late.size, Red, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Kpi("Gravité élevée", open.count { it.grav == "Élevée" }, Color(0xFFE65100), Modifier.weight(1f))
            Kpi("Créés ce mois, clôturés", closedMonth, Color(0xFF2E7D32), Modifier.weight(1f))
        }
        Button(onClick = { vm.exportPdf(ctx, open) }, modifier = Modifier.fillMaxWidth(), enabled = open.isNotEmpty()) {
            Text("Rapport PDF des tags ouverts")
        }
        Bars("Ouverts par responsable", open.flatMap { Rules.splitNames(it.resp) }.groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }.take(10).map { it.key to it.value }, Color(0xFF1565C0))
        Bars("Ouverts par zone", open.groupingBy { it.zone }.eachCount().entries.sortedByDescending { it.value }.take(10)
            .map { it.key to it.value }, Color(0xFF6A1B9A))
        Bars("Ouverts par type", open.groupingBy { it.type }.eachCount().entries.sortedByDescending { it.value }.take(8)
            .map { it.key to it.value }, Color(0xFF00838F))

        Section("Relances des tags en retard")
        val byPerson = late.flatMap { t -> Rules.splitNames(t.resp).map { it to t } }.groupBy({ it.first }, { it.second })
            .entries.sortedByDescending { it.value.size }
        if (byPerson.isEmpty()) Text("Aucun tag en retard 👍", color = Color.Gray)
        byPerson.forEach { (person, tags) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(person, fontWeight = FontWeight.Bold)
                    Text("${tags.size} tag(s) en retard · N° " + tags.joinToString(", ") { it.n }, style = MaterialTheme.typography.bodySmall,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(onClick = { vm.remind(ctx, person, tags) }) { Text("Relancer") }
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

/* ---------------- composants ---------------- */

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier.fillMaxWidth(),
                  lines: Int = 1, keyboard: KeyboardOptions = KeyboardOptions.Default) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, modifier = modifier,
        singleLine = lines == 1, minLines = lines, keyboardOptions = keyboard)
}

@Composable
private fun Picker(label: String, value: String, options: List<String>, onChange: (String) -> Unit,
                   modifier: Modifier = Modifier.fillMaxWidth()) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = { IconButton(onClick = { open = true }) { Icon(Icons.Default.ArrowDropDown, "Choisir") } })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { o -> DropdownMenuItem(text = { Text(o) }, onClick = { onChange(o); open = false }) }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title.uppercase(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
    HorizontalDivider()
}

@Composable
private fun Chips(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o -> FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { Text(o) }) }
    }
}

@Composable
private fun PhotoButtons(onPicked: (Uri) -> Unit) {
    val ctx = LocalContext.current
    var capture by rememberSaveable { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) capture?.let(onPicked)
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let(onPicked) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { val u = Img.newCaptureUri(ctx); capture = u; camera.launch(u) }) { Text("Appareil photo") }
        OutlinedButton(onClick = { gallery.launch("image/*") }) { Text("Galerie / WhatsApp") }
    }
}

/* ---------------- Connexion ---------------- */

@Composable
fun LoginScreen(vm: MainVm) {
    val ctx = LocalContext.current
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snack.showSnackbar(it) } }
    Scaffold(snackbarHost = { SnackbarHost(snack) }) { pad ->
        Column(
            Modifier.fillMaxSize()
                .background(Brush.linearGradient(listOf(Color(0xFFE53935), Color(0xFF8E0000))))
                .padding(pad).padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(painterResource(R.drawable.logo_app), "Safety Tags",
                Modifier.size(120.dp).border(3.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(30.dp)))
            Spacer(Modifier.height(24.dp))
            Text("Safety Tags", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Photographiez une anomalie, générez la fiche HSE\net envoyez-la au responsable.",
                color = Color.White.copy(alpha = 0.9f), textAlign = TextAlign.Center)
            Spacer(Modifier.height(48.dp))
            Button(
                onClick = { vm.signInGoogle(ctx as Activity) },
                enabled = vm.busy.isEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF1F1F1F)),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) {
                Box(Modifier.size(26.dp).background(Color.White, CircleShape).border(1.dp, Color(0xFFDADCE0), CircleShape),
                    contentAlignment = Alignment.Center) {
                    Text("G", color = Color(0xFF4285F4), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Spacer(Modifier.width(12.dp))
                Text("Continuer avec Google", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
            Text("Connexion ou création de compte en un geste", color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.height(20.dp))
            TextButton(onClick = { vm.finishOnboarding() }) {
                Text("Continuer sans compte", color = Color.White, fontSize = 15.sp)
            }
            if (vm.busy.isNotEmpty()) {
                Spacer(Modifier.height(16.dp)); LinearProgressIndicator(color = Color.White, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(36.dp))
            Text("Vos tags restent sur votre téléphone ou dans votre propre Google Sheet.",
                color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        }
    }
}

/* ---------------- Nouveau tag ---------------- */

@Composable
fun NewTagScreen(vm: MainVm) {
    val ctx = LocalContext.current
    val f = vm.form
    val cfg = vm.cfg
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (cfg.zones.isEmpty() && cfg.personnes.isEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Text("Première utilisation : remplissez simplement les champs. Les zones, types, services et responsables " +
                    "que vous tapez vous seront proposés à l’enregistrement pour les retrouver la prochaine fois. " +
                    "Tout se personnalise aussi dans Réglages.", modifier = Modifier.padding(12.dp))
            }
        }
        if (vm.savedRow != 0) {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))) {
                Text("✅ Tag N° ${f.n} enregistré. Touchez + pour un nouveau tag.", color = Color(0xFF1B5E20), modifier = Modifier.padding(12.dp))
            }
        }
        Section("Photo de constat")
        PhotoButtons { vm.setPhoto(ctx, it) }
        vm.photo?.let {
            Image(it.asImageBitmap(), "Photo", Modifier.fillMaxWidth().heightIn(max = 260.dp), contentScale = ContentScale.Fit)
        }
        Field("Note rapide (ce que vous avez constaté)", vm.note, { vm.note = it }, lines = 2)
        Button(onClick = { vm.aiFill(ctx as Activity) }, enabled = vm.busy.isEmpty(), modifier = Modifier.fillMaxWidth()) {
            Text("Remplir avec l’IA"); Spacer(Modifier.width(8.dp)); ProBadge(onRed = true)
        }

        Section("Identification")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field("N°", f.n, { vm.edit(f.copy(n = it)) }, Modifier.width(110.dp), keyboard = KeyboardOptions(keyboardType = KeyboardType.Number))
            Column { Text("Statut", style = MaterialTheme.typography.labelMedium); Chips(listOf("Ouvert", "Clôturé"), f.statut) { vm.edit(f.copy(statut = it)) } }
        }
        Picker("Type de danger", f.type, cfg.types, { vm.edit(f.copy(type = it)) })
        Text("Gravité", style = MaterialTheme.typography.labelMedium)
        Chips(Gravites.all, f.grav) { vm.setGrav(it) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field("Date de constat", f.date, { vm.edit(f.copy(date = it)) }, Modifier.weight(1.2f))
            Field("Date cible", f.cible, { vm.edit(f.copy(cible = it)) }, Modifier.weight(1f))
        }

        Section("Localisation et responsable")
        Picker("Zone", f.zone, cfg.zones, { vm.edit(f.copy(zone = it)); vm.suggestResp(zone = it) })
        Picker("Service concerné", f.service, cfg.services, { vm.edit(f.copy(service = it)); vm.suggestResp(service = it) })
        Picker("Responsable(s) — séparés par /", f.resp, cfg.personnes.map { it.nom }, { v ->
            val cur = Rules.splitNames(f.resp)
            // choisir dans la liste ajoute la personne si un nom est déjà saisi
            val next = if (v in cfg.personnes.map { it.nom } && cur.isNotEmpty() && v !in cur && f.resp.trim().endsWith("/")) f.resp.trim() + " " + v else v
            vm.edit(f.copy(resp = next))
        })
        Field("Emplacement exact", f.empl, { vm.edit(f.copy(empl = it)) })

        Section("Analyse")
        Field("Description", f.desc, { vm.edit(f.copy(desc = it)) }, lines = 2)
        Field("Risque principal", f.risque, { vm.edit(f.copy(risque = it)) }, lines = 2)
        Field(if (f.statut == "Clôturé") "Action réalisée" else "Action urgente", f.action, { vm.edit(f.copy(action = it)) }, lines = 2)
        Field("Proposition", f.prop, { vm.edit(f.copy(prop = it)) }, lines = 2)
        Field("Constaté par", f.par, { vm.edit(f.copy(par = it)) })

        Section("Fiche et envoi")
        Text(if (vm.prefs.sheetMode) "Enregistrement dans votre Google Sheet" else "Enregistrement sur ce téléphone (aucun Google Sheet connecté)",
            style = MaterialTheme.typography.labelMedium, color = Color.Gray)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.buildFiche() }) { Text("Aperçu fiche") }
            Button(onClick = { vm.save() }, enabled = vm.savedRow == 0 && vm.busy.isEmpty()) {
                Icon(Icons.Default.Check, null); Spacer(Modifier.width(6.dp)); Text("Enregistrer")
            }
            Button(onClick = { vm.emailNew(ctx) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F6CBD))) {
                Icon(Icons.Default.Send, null); Spacer(Modifier.width(6.dp)); Text("Email")
            }
            OutlinedButton(onClick = { vm.shareNew(ctx) }) { Icon(Icons.Default.Share, null); Spacer(Modifier.width(6.dp)); Text("Partager") }
        }
        vm.fiche?.let { FicheImage(it) }
        Spacer(Modifier.height(40.dp))
    }
    vm.learn?.let { LearnDialog(vm, it) }
}

@Composable
private fun LearnDialog(vm: MainVm, l: Learn) {
    var zones by remember { mutableStateOf(l.zones.toSet()) }
    var types by remember { mutableStateOf(l.types.toSet()) }
    var services by remember { mutableStateOf(l.services.toSet()) }
    var people by remember { mutableStateOf(l.personnes.toSet()) }
    var rule by remember { mutableStateOf(l.regle != null) }
    @Composable
    fun Line(label: String, checked: Boolean, onToggle: () -> Unit) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(onClick = onToggle)) {
            Checkbox(checked, { onToggle() }); Text(label)
        }
    }
    AlertDialog(
        onDismissRequest = { vm.learn = null },
        title = { Text("Enregistrer pour la prochaine fois ?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                l.zones.forEach { v -> Line("Zone : $v", v in zones) { zones = if (v in zones) zones - v else zones + v } }
                l.types.forEach { v -> Line("Type : $v", v in types) { types = if (v in types) types - v else types + v } }
                l.services.forEach { v -> Line("Service : $v", v in services) { services = if (v in services) services - v else services + v } }
                l.personnes.forEach { v -> Line("Personne : $v", v in people) { people = if (v in people) people - v else people + v } }
                l.regle?.let { r -> Line("Règle : ${r.service} + ${r.zone} → ${r.responsable}", rule) { rule = !rule } }
                if (l.personnes.isNotEmpty()) Text("Ajoutez leur email ensuite dans Réglages › Personnes.",
                    style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                vm.acceptLearning(Learn(l.zones.filter { it in zones }, l.types.filter { it in types },
                    l.services.filter { it in services }, l.personnes.filter { it in people }, if (rule) l.regle else null))
            }) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = { vm.learn = null }) { Text("Non merci") } }
    )
}

@Composable
private fun FicheImage(b: Bitmap) {
    Image(b.asImageBitmap(), "Fiche Safety Tag", Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
}

/* ---------------- Tags ouverts ---------------- */

@Composable
fun OpenTagsScreen(vm: MainVm) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { vm.loadOpen() }
    var query by rememberSaveable { mutableStateOf("") }
    var resp by rememberSaveable { mutableStateOf("Tous") }
    var before by rememberSaveable { mutableStateOf("") }
    var hideBuilding by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<Int>()) }
    var detail by remember { mutableStateOf<Tag?>(null) }
    var confirm by remember { mutableStateOf(false) }

    val people = vm.open.flatMap { Rules.splitNames(it.resp) }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }
    val limit = D.parse(before)
    val q = query.trim().lowercase()
    val shown = vm.open.filter { t ->
        (resp == "Tous" || t.resp.contains(resp, ignoreCase = true)) &&
            (limit == null || (D.parse(t.date)?.isBefore(limit) ?: false)) &&
            (!hideBuilding || !Rules.isBuilding(t)) &&
            (q.isEmpty() || listOf(t.n, t.desc, t.empl, t.zone, t.type, t.resp).any { it.lowercase().contains(q) })
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Field("Rechercher (N°, description, zone…)", query, { query = it }, Modifier.fillMaxWidth().padding(top = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Picker("Responsable", resp, listOf("Tous") + people.map { "${it.key}" }, { resp = it }, Modifier.weight(1f))
            Field("Avant le (jj/mm/aaaa)", before, { before = it }, Modifier.weight(1f))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f).clickable { hideBuilding = !hideBuilding }) {
                Checkbox(hideBuilding, { hideBuilding = it }); Text("Masquer bâtiment", fontSize = 14.sp)
            }
            TextButton(onClick = { vm.exportCsv(ctx, shown) }) { Text("Exporter CSV") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${shown.size} affiché(s) · ${vm.open.size} ouvert(s)", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = { selected = if (selected.isEmpty()) shown.map { it.row }.toSet() else emptySet() }) {
                Text(if (selected.isEmpty()) "Tout sélectionner" else "Désélectionner")
            }
        }
        if (vm.open.isEmpty() && vm.busy.isEmpty()) Text("Aucun tag ouvert.", color = Color.Gray, modifier = Modifier.padding(8.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(shown, key = { it.row }) { t ->
                TagCard(t, t.row in selected,
                    onCheck = { selected = if (t.row in selected) selected - t.row else selected + t.row },
                    onOpen = { detail = t })
            }
        }
        if (selected.isNotEmpty()) {
            Button(onClick = { confirm = true }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text("Clôturer ${selected.size} tag(s)")
            }
        }
    }

    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Clôturer ${selected.size} tag(s) ?") },
        text = { Text("Leur statut passera à « Clôturé ». Aucun email ne sera envoyé.") },
        confirmButton = { TextButton(onClick = { confirm = false; vm.closeRows(selected.toList()) { selected = emptySet() } }) { Text("Clôturer") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Annuler") } }
    )
    detail?.let { t -> TagDialog(vm, t) { detail = null } }
}

@Composable
private fun TagCard(t: Tag, checked: Boolean, onCheck: () -> Unit, onOpen: () -> Unit) {
    val gravColor = when (t.grav) { "Élevée" -> Color(0xFFD32F2F); "Faible" -> Color(0xFF689F38); else -> Color(0xFFF57C00) }
    Card(Modifier.fillMaxWidth().clickable(onClick = onOpen), shape = RoundedCornerShape(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
            Checkbox(checked, { onCheck() })
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Card(colors = CardDefaults.cardColors(containerColor = gravColor), shape = RoundedCornerShape(5.dp), modifier = Modifier.size(10.dp)) {}
                    Spacer(Modifier.width(6.dp))
                    Text("N° ${t.n}", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Text(D.day(t.date), style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.weight(1f))
                    Text(t.zone, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(t.desc, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(t.resp, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun TagDialog(vm: MainVm, t: Tag, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var after by remember { mutableStateOf<Bitmap?>(null) }
    var mail by remember { mutableStateOf(true) }
    var resp by remember { mutableStateOf(t.resp) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Tag N° ${t.n}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${t.type} · Gravité ${t.grav}", fontWeight = FontWeight.Bold)
                Text("Constat : ${D.label(t.date)} · Cible : ${D.day(t.cible)}")
                Text("Lieu : ${t.empl}" + if (t.zone.isNotBlank()) " (${t.zone})" else "")
                Text(t.desc)
                if (t.action.isNotBlank()) Text("Action : ${t.action}")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Field("Responsable", resp, { resp = it }, Modifier.weight(1f))
                    if (resp != t.resp) IconButton(onClick = { vm.updateResp(t, resp) }) { Icon(Icons.Default.Check, "Enregistrer") }
                }
                OutlinedButton(onClick = { vm.emailExisting(ctx, t) }) { Text("Fiche + email au responsable") }
                HorizontalDivider()
                Text("Clôturer", fontWeight = FontWeight.Bold)
                PhotoButtons { uri -> after = Img.load(ctx, uri) }
                after?.let { Image(it.asImageBitmap(), "Photo après", Modifier.fillMaxWidth().heightIn(max = 180.dp), contentScale = ContentScale.Fit) }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { mail = !mail }) {
                    Checkbox(mail, { mail = it }); Text("Ouvrir l’email de clôture")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                vm.closeRows(listOf(t.row), after) { if (mail) vm.emailExisting(ctx, t, closed = true) }
                onClose()
            }) { Text("Clôturer") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Fermer") } }
    )
}

/* ---------------- Réglages ---------------- */

@Composable
private fun EditableList(title: String, items: List<String>, onChange: (List<String>) -> Unit, extra: (@Composable () -> Unit)? = null) {
    var input by remember { mutableStateOf("") }
    Section(title)
    if (items.isEmpty()) Text("Liste vide : ajoutez vos valeurs, ou elles seront proposées quand vous les taperez dans un tag.",
        style = MaterialTheme.typography.bodySmall, color = Color.Gray)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { v ->
            FilterChip(selected = true, onClick = { onChange(items - v) }, label = { Text(v) },
                trailingIcon = { Icon(Icons.Default.Delete, "Supprimer", Modifier.size(16.dp)) })
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Field("Ajouter", input, { input = it }, Modifier.weight(1f))
        IconButton(onClick = {
            val v = input.trim()
            if (v.isNotEmpty() && items.none { it.equals(v, true) }) onChange(items + v)
            input = ""
        }) { Icon(Icons.Default.Add, "Ajouter") }
    }
    extra?.invoke()
}

@Composable
fun SettingsScreen(vm: MainVm) {
    val ctx = LocalContext.current
    val c = vm.cfg
    fun set(n: Config) = vm.saveConfig(n)
    var url by rememberSaveable { mutableStateOf(vm.prefs.url) }
    var token by rememberSaveable { mutableStateOf(vm.prefs.token) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { vm.importConfig(ctx, it) } }

    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { vm.setLogo(ctx, it) } }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Section("Compte")
        if (vm.userEmail.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                    Text(vm.userName.split(" ").mapNotNull { it.firstOrNull()?.uppercase() }.take(2).joinToString(""),
                        color = Color.White, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) { Text(vm.userName, fontWeight = FontWeight.Bold); Text(vm.userEmail, style = MaterialTheme.typography.bodySmall) }
                TextButton(onClick = { vm.signOut(ctx) }) { Text("Se déconnecter") }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Non connecté", modifier = Modifier.weight(1f), color = Color.Gray)
                TextButton(onClick = { vm.signOut(ctx) }) { Text("Se connecter") }
            }
        }

        Section("Logo de l’entreprise (haut gauche de la fiche)")
        vm.logo?.let {
            Box(Modifier.background(Red, RoundedCornerShape(8.dp)).padding(10.dp)) {
                Box(Modifier.background(Color.White, RoundedCornerShape(6.dp)).padding(6.dp)) {
                    Image(it.asImageBitmap(), "Logo", Modifier.height(56.dp))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { logoPicker.launch("image/*") }) { Text(if (vm.logo == null) "Ajouter un logo" else "Changer le logo") }
            if (vm.logo != null) OutlinedButton(onClick = { vm.clearLogo() }) { Text("Retirer") }
        }
        Text("Conseil : une image PNG à fond transparent ou blanc rend le mieux.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)

        Section("Entreprise et fiche")
        Field("Nom affiché sur la fiche (ex : Usine X · Suivi HSE)", c.organisation, { set(c.copy(organisation = it)) })
        Field("Pied de fiche (optionnel)", c.pied, { set(c.copy(pied = it)) })
        Field("Constaté par (par défaut)", c.constatePar, { set(c.copy(constatePar = it)) })
        Field("Signature des emails", c.signature, { set(c.copy(signature = it)) }, lines = 2)
        Text("Délai de la date cible (jours) selon la gravité", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val num = KeyboardOptions(keyboardType = KeyboardType.Number)
            Field("Élevée", c.delaiElevee.toString(), { set(c.copy(delaiElevee = it.toIntOrNull() ?: c.delaiElevee)) }, Modifier.weight(1f), keyboard = num)
            Field("Moyenne", c.delaiMoyenne.toString(), { set(c.copy(delaiMoyenne = it.toIntOrNull() ?: c.delaiMoyenne)) }, Modifier.weight(1f), keyboard = num)
            Field("Faible", c.delaiFaible.toString(), { set(c.copy(delaiFaible = it.toIntOrNull() ?: c.delaiFaible)) }, Modifier.weight(1f), keyboard = num)
        }

        EditableList("Zones", c.zones, { set(c.copy(zones = it)) })
        EditableList("Types de danger", c.types, { set(c.copy(types = it)) }) {
            if (c.types.isEmpty()) TextButton(onClick = { set(c.copy(types = Config.exemplesTypes)) }) { Text("Charger une liste type HSE") }
        }
        EditableList("Services", c.services, { set(c.copy(services = it)) })

        Section("Personnes (nom et email)")
        c.personnes.forEachIndexed { i, p ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Field("Nom", p.nom, { v -> set(c.copy(personnes = c.personnes.toMutableList().also { it[i] = p.copy(nom = v) })) }, Modifier.weight(1f))
                Field("Email", p.email, { v -> set(c.copy(personnes = c.personnes.toMutableList().also { it[i] = p.copy(email = v) })) },
                    Modifier.weight(1.3f), keyboard = KeyboardOptions(keyboardType = KeyboardType.Email))
                IconButton(onClick = { set(c.copy(personnes = c.personnes.filterIndexed { j, _ -> j != i })) }) { Icon(Icons.Default.Delete, "Supprimer") }
            }
        }
        OutlinedButton(onClick = { set(c.copy(personnes = c.personnes + Contact("", ""))) }) { Text("Ajouter une personne") }

        Section("Règles : service + zone → responsable")
        Text("« * » veut dire « toutes les zones » ou « tous les services ».", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        c.regles.forEachIndexed { i, r ->
            fun upd(n: Regle) = set(c.copy(regles = c.regles.toMutableList().also { it[i] = n }))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Picker("Service", r.service, listOf("*") + c.services, { upd(r.copy(service = it)) }, Modifier.weight(1f))
                        Picker("Zone", r.zone, listOf("*") + c.zones, { upd(r.copy(zone = it)) }, Modifier.weight(1f))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Picker("Responsable(s)", r.responsable, c.personnes.map { it.nom }, { upd(r.copy(responsable = it)) }, Modifier.weight(1f))
                        IconButton(onClick = { set(c.copy(regles = c.regles.filterIndexed { j, _ -> j != i })) }) { Icon(Icons.Default.Delete, "Supprimer") }
                    }
                }
            }
        }
        OutlinedButton(onClick = { set(c.copy(regles = c.regles + Regle("", "", ""))) }) { Text("Ajouter une règle") }

        Section("Destinataires des emails")
        Text("Noms (de la liste Personnes) ou adresses, séparés par « ; ».", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        Field("Copie (Cc) des emails d’ouverture", c.ccOuverture, { set(c.copy(ccOuverture = it)) })
        Field("Destinataires des emails de clôture", c.toCloture, { set(c.copy(toCloture = it)) })
        Field("Copie (Cc) des emails de clôture", c.ccCloture, { set(c.copy(ccCloture = it)) })

        Section("Google Sheet (optionnel)")
        Text("Sans Google Sheet, les tags sont gardés sur ce téléphone (export CSV possible).",
            style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        Field("URL du script (…/exec)", url, { url = it })
        OutlinedTextField(token, { token = it }, label = { Text("Jeton") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.prefs.url = url; vm.prefs.token = token; vm.testSheet() }) { Text("Enregistrer et tester") }
            OutlinedButton(onClick = { vm.importFromSheet() }, enabled = vm.connected) { Text("Importer règles et contacts") }
        }
        Text(if (vm.connected) "✅ Connecté au Google Sheet" else if (vm.prefs.sheetMode) "Non connecté" else "Mode local",
            color = if (vm.connected) Color(0xFF2E7D32) else Color.Gray)

        Section("Abonnement")
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (vm.isPro) { ProBadge(); Text("  Safety Tags Pro est actif", fontWeight = FontWeight.Bold) }
            else Text("Version gratuite", color = Color.Gray)
        }
        ProFeatures()
        OutlinedButton(onClick = { vm.refreshPro(ctx as Activity, showResult = true) }) { Text("Vérifier mon statut Pro") }

        Section("Sauvegarde des réglages")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.exportConfig(ctx) }) { Text("Exporter / partager") }
            OutlinedButton(onClick = { importer.launch("*/*") }) { Text("Importer un fichier") }
        }
        Text("Partagez vos réglages avec un collègue pour qu’il retrouve les mêmes listes.",
            style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        Spacer(Modifier.height(40.dp))
    }
}
