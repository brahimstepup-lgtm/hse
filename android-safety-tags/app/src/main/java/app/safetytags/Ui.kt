@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.safetytags

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snack.showSnackbar(it) } }
    val titles = listOf("Nouveau tag", "Tags ouverts", "Réglages")
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(titles[tab], fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Red, titleContentColor = Color.White,
                    actionIconContentColor = Color.White),
                actions = {
                    if (tab == 0) IconButton(onClick = { vm.newTag() }) { Icon(Icons.Default.Add, "Nouveau tag") }
                    if (tab == 1) IconButton(onClick = { vm.loadOpen() }) { Icon(Icons.Default.Refresh, "Actualiser") }
                }
            )
        },
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Icon(Icons.Default.Edit, null) }, label = { Text("Nouveau") })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Icon(Icons.Default.List, null) }, label = { Text("Ouverts") })
                NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, icon = { Icon(Icons.Default.Settings, null) }, label = { Text("Réglages") })
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
                else -> SettingsScreen(vm)
            }
        }
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

/* ---------------- Nouveau tag ---------------- */

@Composable
fun NewTagScreen(vm: MainVm) {
    val ctx = LocalContext.current
    val f = vm.form
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (vm.savedRow != 0) {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))) {
                Text("✅ Tag N° ${f.n} enregistré dans le Sheet (ligne ${vm.savedRow}). Touchez + pour un nouveau tag.",
                    color = Color(0xFF1B5E20), modifier = Modifier.padding(12.dp))
            }
        }
        Section("Photo de constat")
        PhotoButtons { vm.setPhoto(ctx, it) }
        vm.photo?.let {
            Image(it.asImageBitmap(), "Photo", Modifier.fillMaxWidth().heightIn(max = 260.dp), contentScale = ContentScale.Fit)
        }
        Field("Note rapide (ex : RIA salle lavage étage 1, volant absent)", vm.note, { vm.note = it }, lines = 2)
        Button(onClick = { vm.aiFill() }, enabled = vm.busy.isEmpty(), modifier = Modifier.fillMaxWidth()) {
            Text("Remplir avec l’IA")
        }

        Section("Identification")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field("N°", f.n, { vm.edit(f.copy(n = it)) }, Modifier.width(110.dp), keyboard = KeyboardOptions(keyboardType = KeyboardType.Number))
            Column { Text("Statut", style = MaterialTheme.typography.labelMedium); Chips(listOf("Ouvert", "Clôturé"), f.statut) { vm.edit(f.copy(statut = it)) } }
        }
        Picker("Type", f.type, Lists.types, { vm.edit(f.copy(type = it)) })
        Text("Gravité", style = MaterialTheme.typography.labelMedium)
        Chips(Lists.gravites, f.grav) { vm.edit(f.copy(grav = it)) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field("Date de constat", f.date, { vm.edit(f.copy(date = it)) }, Modifier.weight(1.2f))
            Field("Date cible", f.cible, { vm.edit(f.copy(cible = it)) }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(1L, 3L, 7L, 15L).forEach { d ->
                OutlinedButton(onClick = { vm.edit(f.copy(cible = D.plusDays(f.date, d))) }) { Text("+$d j", fontSize = 12.sp) }
            }
        }

        Section("Localisation et responsable")
        Picker("Étage / zone", f.zone, Lists.etages, { vm.edit(f.copy(zone = it)); vm.suggestResp() })
        Picker("Catégorie (pour le responsable)", vm.categorie, Lists.categories, { vm.categorie = it; vm.suggestResp() })
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { vm.sharp = !vm.sharp; vm.suggestResp() }) {
            Checkbox(checked = vm.sharp, onCheckedChange = { vm.sharp = it; vm.suggestResp() })
            Text("Partie tranchante (responsable supplémentaire)")
        }
        Field("Responsable(s) — séparés par /", f.resp, { vm.edit(f.copy(resp = it)) })
        Field("Emplacement exact", f.empl, { vm.edit(f.copy(empl = it)) })

        Section("Analyse")
        Field("Description", f.desc, { vm.edit(f.copy(desc = it)) }, lines = 2)
        Field("Risque principal", f.risque, { vm.edit(f.copy(risque = it)) }, lines = 2)
        Field(if (f.statut == "Clôturé") "Action réalisée" else "Action urgente", f.action, { vm.edit(f.copy(action = it)) }, lines = 2)
        Field("Proposition", f.prop, { vm.edit(f.copy(prop = it)) }, lines = 2)
        Field("Constaté par", f.par, { vm.edit(f.copy(par = it)) })

        Section("Fiche et envoi")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.buildFiche() }) { Text("Aperçu fiche") }
            Button(onClick = { vm.save() }, enabled = vm.savedRow == 0 && vm.busy.isEmpty()) {
                Icon(Icons.Default.Check, null); Spacer(Modifier.width(6.dp)); Text("Enregistrer (Sheet + Drive)")
            }
            Button(onClick = { vm.emailNew(ctx) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F6CBD))) {
                Icon(Icons.Default.Send, null); Spacer(Modifier.width(6.dp)); Text("Email Outlook")
            }
            OutlinedButton(onClick = { vm.shareNew(ctx) }) { Icon(Icons.Default.Share, null); Spacer(Modifier.width(6.dp)); Text("Partager") }
        }
        vm.fiche?.let { FicheImage(it) }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun FicheImage(b: Bitmap) {
    Image(b.asImageBitmap(), "Fiche Safety Tag", Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
}

/* ---------------- Tags ouverts ---------------- */

@Composable
fun OpenTagsScreen(vm: MainVm) {
    LaunchedEffect(Unit) { if (vm.open.isEmpty()) vm.loadOpen() }
    var query by rememberSaveable { mutableStateOf("") }
    var resp by rememberSaveable { mutableStateOf("Tous") }
    var before by rememberSaveable { mutableStateOf("") }
    var hideBuilding by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<Int>()) }
    var detail by remember { mutableStateOf<Tag?>(null) }
    var confirm by remember { mutableStateOf(false) }

    val people = vm.open.flatMap { t -> t.resp.split("/").map { it.trim() } }.filter { it.isNotEmpty() }
        .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }
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
            Picker("Responsable", resp, listOf("Tous") + people.map { it.key }, { resp = it }, Modifier.weight(1f))
            Field("Avant le (jj/mm/aaaa)", before, { before = it }, Modifier.weight(1f))
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { hideBuilding = !hideBuilding }) {
            Checkbox(hideBuilding, { hideBuilding = it }); Text("Masquer les actions bâtiment")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${shown.size} affiché(s) · ${vm.open.size} ouvert(s)", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = { selected = if (selected.isEmpty()) shown.map { it.row }.toSet() else emptySet() }) {
                Text(if (selected.isEmpty()) "Tout sélectionner" else "Désélectionner")
            }
        }
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
        text = { Text("Le statut passera à « ✅ Fermé » dans le Sheet. Aucun email ne sera envoyé.") },
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
                    Box(Modifier.size(10.dp).padding(0.dp)) { Card(colors = CardDefaults.cardColors(containerColor = gravColor), modifier = Modifier.fillMaxSize()) {} }
                    Spacer(Modifier.width(6.dp))
                    Text("N° ${t.n}", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Text(D.day(t.date), style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.weight(1f))
                    Text(t.zone, style = MaterialTheme.typography.labelMedium)
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
                Text("Constat : ${D.label(t.date)} · Cible : ${t.cible}")
                Text("Lieu : ${t.empl} (${t.zone})")
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
fun SettingsScreen(vm: MainVm) {
    var url by rememberSaveable { mutableStateOf(vm.prefs.url) }
    var token by rememberSaveable { mutableStateOf(vm.prefs.token) }
    var key by rememberSaveable { mutableStateOf(vm.prefs.aiKey) }
    var model by rememberSaveable { mutableStateOf(vm.prefs.aiModel) }
    val contacts = remember(vm.contacts) { mutableStateListOf<Contact>().apply { addAll(vm.contacts) } }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Section("Connexion au Google Sheet")
        Field("URL du script (…/exec)", url, { url = it })
        OutlinedTextField(token, { token = it }, label = { Text("Jeton") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Section("IA (optionnel)")
        OutlinedTextField(key, { key = it }, label = { Text("Clé API Anthropic") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Field("Modèle", model, { model = it })
        Button(onClick = {
            vm.prefs.url = url; vm.prefs.token = token; vm.prefs.aiKey = key; vm.prefs.aiModel = model.ifBlank { "claude-sonnet-5-5" }
            vm.loadInfo(showOk = true)
        }, modifier = Modifier.fillMaxWidth()) { Text("Enregistrer et tester la connexion") }
        Text(if (vm.connected) "✅ Connecté au Sheet" else "Non connecté", color = if (vm.connected) Color(0xFF2E7D32) else Color.Gray)

        Section("Contacts (emails des responsables)")
        contacts.forEachIndexed { i, c ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Field("Nom", c.nom, { contacts[i] = c.copy(nom = it) }, Modifier.weight(1f))
                Field("Email", c.email, { contacts[i] = c.copy(email = it) }, Modifier.weight(1.3f),
                    keyboard = KeyboardOptions(keyboardType = KeyboardType.Email))
                IconButton(onClick = { contacts.removeAt(i) }) { Icon(Icons.Default.Delete, "Supprimer") }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { contacts.add(Contact("", "")) }) { Text("Ajouter un contact") }
            Button(onClick = { vm.saveContacts(contacts.toList()) }) { Text("Enregistrer dans le Sheet") }
        }

        Section("Règles d’affectation (onglet « regles »)")
        if (vm.regles.isEmpty()) Text("Aucune règle chargée.")
        vm.regles.forEach { Text("• ${it.categorie} / ${it.etage} → ${it.responsable}", style = MaterialTheme.typography.bodySmall) }
        Section("Paramètres (onglet « parametres »)")
        vm.params.forEach { (k, v) -> Text("• $k : ${v.ifBlank { "—" }}", style = MaterialTheme.typography.bodySmall) }
        Text("Les règles et paramètres se modifient directement dans le Google Sheet, puis « Enregistrer et tester ».",
            style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        Spacer(Modifier.height(40.dp))
    }
}
