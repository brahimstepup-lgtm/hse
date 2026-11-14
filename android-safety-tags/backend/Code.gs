/**
 * Safety Tags — pont entre l'application Android et le Google Sheet des tags.
 *
 * Installation (une seule fois) :
 *  1. Ouvrir https://script.google.com > Nouveau projet (projet autonome, pour ne pas
 *     toucher aux scripts déjà liés au Sheet).
 *  2. Coller ce fichier dans Code.gs, renseigner CONFIG.SHEET_ID et CONFIG.FOLDER_ID
 *     et, si besoin, les listes DEFAULT_* plus bas.
 *  3. Exécuter la fonction « setup » (autoriser l'accès). Le jeton s'affiche dans le journal.
 *  4. Déployer > Nouveau déploiement > Application Web :
 *       Exécuter en tant que : Moi   —   Qui a accès : Tout le monde
 *     Copier l'URL /exec et le jeton dans les Réglages de l'application.
 */

var CONFIG = {
  SHEET_ID: 'ID_DU_GOOGLE_SHEET',
  TAB: 'plan',               // onglet des tags
  FIRST_ROW: 8,              // première ligne de données
  FOLDER_ID: 'ID_DU_DOSSIER_DRIVE_DES_PHOTOS'
};

// [Catégorie, Étage, Responsable]  — « * » = tous les étages.
// La catégorie « Partie tranchante » ajoute un responsable supplémentaire.
var DEFAULT_REGLES = [];
// [Nom, Email]
var DEFAULT_CONTACTS = [];
// [Clé, Valeur] — noms séparés par « ; » (résolus via l'onglet contacts)
var DEFAULT_PARAMS = [
  ['cc_ouverture', ''],
  ['to_cloture', ''],
  ['cc_cloture', ''],
  ['constate_par', ''],
  ['signature', '']
];

var COLS = { n: 1, date: 2, cible: 3, type: 4, grav: 5, empl: 6, zone: 7, desc: 8, risque: 9,
             action: 10, prop: 11, photo: 12, statut: 13, resp: 14, apres: 15 };
var GRAV = { 'Élevée': '🟥 Élevée', 'Moyenne': '🟧 Moyenne', 'Faible': '🟩 Faible' };

/* ---------- installation ---------- */
function ss_() { return SpreadsheetApp.openById(CONFIG.SHEET_ID); }

function setup() {
  var props = PropertiesService.getScriptProperties();
  var t = props.getProperty('TOKEN');
  if (!t) { t = Utilities.getUuid().replace(/-/g, ''); props.setProperty('TOKEN', t); }
  ensureTab_('regles', ['Catégorie', 'Étage', 'Responsable'], DEFAULT_REGLES);
  ensureTab_('contacts', ['Nom', 'Email'], DEFAULT_CONTACTS);
  ensureTab_('parametres', ['Clé', 'Valeur'], DEFAULT_PARAMS);
  DriveApp.getFolderById(CONFIG.FOLDER_ID); // vérifie l'accès au dossier
  Logger.log('Jeton à copier dans l’application : ' + t);
}

function ensureTab_(name, header, rows) {
  var ss = ss_();
  var sh = ss.getSheetByName(name);
  if (sh) return sh;
  sh = ss.insertSheet(name);
  sh.getRange(1, 1, 1, header.length).setValues([header]).setFontWeight('bold');
  if (rows.length) sh.getRange(2, 1, rows.length, header.length).setValues(rows);
  sh.setFrozenRows(1);
  return sh;
}

/* ---------- routage ---------- */
function doPost(e) {
  var p = {};
  try { p = JSON.parse(e.postData.contents); } catch (x) {}
  return route_(p);
}
function doGet(e) { return route_((e && e.parameter) || {}); }

var ACTIONS = {
  info: info_, add: add_, list: list_, close: close_, photo: photo_, update: update_,
  saveContacts: saveContacts_
};

function route_(p) {
  var out;
  try {
    var tok = PropertiesService.getScriptProperties().getProperty('TOKEN');
    if (!tok || p.token !== tok) throw new Error('Jeton invalide : vérifiez les Réglages de l’application.');
    var fn = ACTIONS[p.action];
    if (!fn) throw new Error('Action inconnue : ' + p.action);
    out = { ok: true, data: fn(p) };
  } catch (err) {
    out = { ok: false, error: String((err && err.message) || err) };
  }
  return ContentService.createTextOutput(JSON.stringify(out)).setMimeType(ContentService.MimeType.JSON);
}

/* ---------- utilitaires ---------- */
function plan_() { return ss_().getSheetByName(CONFIG.TAB); }
function tz_() { return ss_().getSpreadsheetTimeZone(); }

function lastRow_(sh) {
  var vals = sh.getRange(1, 1, sh.getMaxRows(), 1).getValues();
  for (var i = vals.length - 1; i >= 0; i--) if (String(vals[i][0]).trim() !== '') return i + 1;
  return CONFIG.FIRST_ROW - 1;
}

function nextNumber_(sh, last) {
  var start = Math.max(CONFIG.FIRST_ROW, last - 80);
  if (last < start) return 1;
  var m = 0;
  sh.getRange(start, 1, last - start + 1, 1).getValues().forEach(function (r) {
    var n = parseInt(r[0], 10); if (n > m) m = n;
  });
  return m + 1;
}

function table_(name) {
  var sh = ss_().getSheetByName(name);
  if (!sh || sh.getLastRow() < 2) return [];
  return sh.getRange(2, 1, sh.getLastRow() - 1, sh.getLastColumn()).getValues()
    .filter(function (r) { return String(r[0]).trim() !== ''; });
}

function parseDate_(s) {
  if (!s) return '';
  var m = String(s).match(/^(\d{4})-(\d{2})-(\d{2})(?:[ T](\d{1,2}):(\d{2}))?/);
  if (!m) return String(s);
  return new Date(+m[1], +m[2] - 1, +m[3], m[4] ? +m[4] : 0, m[5] ? +m[5] : 0);
}

function fmt_(v, pattern) {
  if (v instanceof Date) return Utilities.formatDate(v, tz_(), pattern);
  return String(v || '');
}

function savePhoto_(b64, name) {
  var blob = Utilities.newBlob(Utilities.base64Decode(b64), 'image/jpeg', name);
  var f = DriveApp.getFolderById(CONFIG.FOLDER_ID).createFile(blob);
  return 'https://drive.google.com/uc?export=view&id=' + f.getId();
}

function rowToTag_(r, rowNum) {
  var grav = String(r[COLS.grav - 1]).replace(/[^A-Za-zÀ-ÿ]/g, '');
  return {
    row: rowNum, n: String(r[0]), date: fmt_(r[1], 'yyyy-MM-dd HH:mm'), cible: fmt_(r[2], 'dd/MM/yyyy'),
    type: String(r[3]), grav: grav, empl: String(r[5]), zone: String(r[6]), desc: String(r[7]),
    risque: String(r[8]), action: String(r[9]), prop: String(r[10]), photo: String(r[11]),
    statut: /ferm/i.test(String(r[12])) ? 'Clôturé' : 'Ouvert', resp: String(r[13]), apres: String(r[14] || '')
  };
}

/* ---------- actions ---------- */
function info_() {
  var sh = plan_();
  var last = lastRow_(sh);
  var params = {};
  table_('parametres').forEach(function (r) { params[String(r[0])] = String(r[1]); });
  return {
    nextNumber: nextNumber_(sh, last),
    nextRow: last + 1,
    regles: table_('regles').map(function (r) {
      return { categorie: String(r[0]), etage: String(r[1]), responsable: String(r[2]) };
    }),
    contacts: table_('contacts').map(function (r) { return { nom: String(r[0]), email: String(r[1]) }; }),
    params: params
  };
}

function add_(p) {
  var lock = LockService.getScriptLock();
  lock.waitLock(20000);
  try {
    var sh = plan_();
    var last = lastRow_(sh);
    var row = last + 1;
    var t = p.tag || {};
    var n = parseInt(t.n, 10) || nextNumber_(sh, last);
    var link = p.photo ? savePhoto_(p.photo, n + '.Référence Photo.jpg') : '';
    sh.getRange(row, 1, 1, 14).setValues([[
      n, parseDate_(t.date), parseDate_(t.cible), t.type || '', GRAV[t.grav] || t.grav || '',
      t.empl || '', t.zone || '', t.desc || '', t.risque || '', t.action || '', t.prop || '', link,
      t.statut === 'Clôturé' ? '✅ Fermé' : '🟥 Ouvert', t.resp || ''
    ]]);
    sh.getRange(row, 2).setNumberFormat('yyyy-mm-dd h:mm');
    sh.getRange(row, 3).setNumberFormat('dd/mm/yyyy');
    return { n: n, row: row, photo: link };
  } finally {
    lock.releaseLock();
  }
}

function list_(p) {
  var sh = plan_();
  var last = lastRow_(sh);
  if (last < CONFIG.FIRST_ROW) return [];
  var vals = sh.getRange(CONFIG.FIRST_ROW, 1, last - CONFIG.FIRST_ROW + 1, 15).getValues();
  var out = [];
  for (var i = 0; i < vals.length; i++) {
    var r = vals[i];
    if (String(r[0]).trim() === '') continue;
    var closed = /ferm/i.test(String(r[12]));
    if (p.status === 'open' && closed) continue;
    if (p.status === 'closed' && !closed) continue;
    out.push(rowToTag_(r, CONFIG.FIRST_ROW + i));
  }
  if (p.limit) out = out.slice(-p.limit);
  return out;
}

function close_(p) {
  var sh = plan_();
  var rows = (p.rows || []).map(Number).filter(function (r) { return r >= CONFIG.FIRST_ROW; });
  rows.forEach(function (r) { sh.getRange(r, COLS.statut).setValue('✅ Fermé'); });
  var link = '';
  if (p.photoAfter && rows.length === 1) {
    var n = sh.getRange(rows[0], 1).getValue();
    link = savePhoto_(p.photoAfter, n + '.Photo après.jpg');
    sh.getRange(rows[0], COLS.apres).setValue(link);
  }
  return { closed: rows.length, photoAfter: link };
}

function update_(p) {
  var sh = plan_();
  var row = Number(p.row);
  if (!(row >= CONFIG.FIRST_ROW)) throw new Error('Ligne invalide');
  var f = p.fields || {};
  Object.keys(f).forEach(function (k) {
    if (!COLS[k] || k === 'n' || k === 'photo') return;
    var v = f[k];
    if (k === 'date' || k === 'cible') v = parseDate_(v);
    if (k === 'grav') v = GRAV[v] || v;
    if (k === 'statut') v = (v === 'Clôturé' ? '✅ Fermé' : '🟥 Ouvert');
    sh.getRange(row, COLS[k]).setValue(v);
  });
  return rowToTag_(sh.getRange(row, 1, 1, 15).getValues()[0], row);
}

function photo_(p) {
  var link = p.link;
  if (!link && p.row) link = String(plan_().getRange(Number(p.row), COLS.photo).getValue());
  var m = String(link || '').match(/[-\w]{25,}/);
  if (!m) return { base64: '' };
  var blob = DriveApp.getFileById(m[0]).getBlob();
  return { base64: Utilities.base64Encode(blob.getBytes()) };
}

function saveContacts_(p) {
  var sh = ensureTab_('contacts', ['Nom', 'Email'], []);
  if (sh.getLastRow() > 1) sh.getRange(2, 1, sh.getLastRow() - 1, 2).clearContent();
  var rows = (p.contacts || []).filter(function (c) { return c.nom; })
    .map(function (c) { return [c.nom, c.email || '']; });
  if (rows.length) sh.getRange(2, 1, rows.length, 2).setValues(rows);
  return { saved: rows.length };
}
