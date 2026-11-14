/**
 * Safety Tags — relais « lien du Sheet » (fichier à ajouter au projet Safety Tags Pro).
 *
 * L'utilisateur colle simplement le lien de son Google Sheet dans l'application.
 * Le Sheet doit être partagé en « Éditeur » (Toute personne disposant du lien, ou avec son compte Google).
 * Le relais n'écrit que dans un Sheet que l'utilisateur connecté peut lui-même modifier.
 * Les tags sont rangés dans l'onglet « Tags » (créé au premier usage).
 */

var RELAY = {
  TAB: 'Tags',
  FIRST_ROW: 2,
  HEADER: ['N°', 'Date création', 'Date cible', 'Type', 'Gravité', 'Emplacement', 'Zone', 'Description',
    'Risque', 'Action urgente', 'Proposition', 'Photo', 'Statut', 'Responsable', 'Photo après']
};
var R_COLS = { n: 1, date: 2, cible: 3, type: 4, grav: 5, empl: 6, zone: 7, desc: 8, risque: 9,
  action: 10, prop: 11, photo: 12, statut: 13, resp: 14, apres: 15 };
var R_GRAV = { 'Élevée': '🟥 Élevée', 'Moyenne': '🟧 Moyenne', 'Faible': '🟩 Faible' };

/** Point d'entrée appelé par doPost (action « sheet »). */
function relay_(email, p) {
  var id = sheetId_(p.sheetUrl);
  checkAccess_(id, email);
  var ss = SpreadsheetApp.openById(id);
  var ops = { info: rInfo_, add: rAdd_, list: rList_, close: rClose_, update: rUpdate_, photo: rPhoto_ };
  var fn = ops[p.op];
  if (!fn) throw new Error('Action inconnue : ' + p.op);
  return fn(ss, p);
}

function sheetId_(url) {
  var m = String(url || '').match(/docs\.google\.com\/spreadsheets\/d\/([-\w]{25,})/);
  if (!m) throw new Error('Lien de Google Sheet invalide.');
  return m[1];
}

/** N'autorise que les Sheets que l'utilisateur peut déjà modifier lui-même. */
function checkAccess_(id, email) {
  var f;
  try { f = DriveApp.getFileById(id); } catch (e) {
    throw new Error('Sheet inaccessible : partagez-le en « Éditeur » (Toute personne disposant du lien).');
  }
  var linkEdit = f.getSharingPermission() === DriveApp.Permission.EDIT &&
    (f.getSharingAccess() === DriveApp.Access.ANYONE_WITH_LINK || f.getSharingAccess() === DriveApp.Access.ANYONE);
  if (linkEdit) return;
  var allowed = f.getEditors().map(function (u) { return String(u.getEmail()).toLowerCase(); });
  try { allowed.push(String(f.getOwner().getEmail()).toLowerCase()); } catch (e) {}
  if (allowed.indexOf(email) < 0)
    throw new Error('Ce Sheet n’est pas partagé en « Éditeur » avec votre compte Google.');
}

function rTab_(ss) {
  var sh = ss.getSheetByName(RELAY.TAB);
  if (sh) return sh;
  sh = ss.insertSheet(RELAY.TAB, 0);
  sh.getRange(1, 1, 1, RELAY.HEADER.length).setValues([RELAY.HEADER]).setFontWeight('bold')
    .setBackground('#C62828').setFontColor('#FFFFFF');
  sh.setFrozenRows(1);
  return sh;
}

function rLast_(sh) {
  var vals = sh.getRange(1, 1, sh.getMaxRows(), 1).getValues();
  for (var i = vals.length - 1; i >= 0; i--) if (String(vals[i][0]).trim() !== '') return i + 1;
  return RELAY.FIRST_ROW - 1;
}

function rNext_(sh, last) {
  if (last < RELAY.FIRST_ROW) return 1;
  var start = Math.max(RELAY.FIRST_ROW, last - 80), m = 0;
  sh.getRange(start, 1, last - start + 1, 1).getValues().forEach(function (r) {
    var n = parseInt(r[0], 10); if (n > m) m = n;
  });
  return m + 1;
}

function rTable_(ss, name) {
  var sh = ss.getSheetByName(name);
  if (!sh || sh.getLastRow() < 2) return [];
  return sh.getRange(2, 1, sh.getLastRow() - 1, sh.getLastColumn()).getValues()
    .filter(function (r) { return String(r[0]).trim() !== ''; });
}

function rDate_(s) {
  var m = String(s || '').match(/^(\d{4})-(\d{2})-(\d{2})(?:[ T](\d{1,2}):(\d{2}))?/);
  if (!m) return String(s || '');
  return new Date(+m[1], +m[2] - 1, +m[3], m[4] ? +m[4] : 0, m[5] ? +m[5] : 0);
}

function rFmt_(ss, v, pattern) {
  if (v instanceof Date) return Utilities.formatDate(v, ss.getSpreadsheetTimeZone(), pattern);
  return String(v || '');
}

function rTag_(ss, r, row) {
  return {
    row: row, n: String(r[0]), date: rFmt_(ss, r[1], 'yyyy-MM-dd HH:mm'), cible: rFmt_(ss, r[2], 'dd/MM/yyyy'),
    type: String(r[3]), grav: String(r[4]).replace(/[^A-Za-zÀ-ÿ]/g, ''), empl: String(r[5]), zone: String(r[6]),
    desc: String(r[7]), risque: String(r[8]), action: String(r[9]), prop: String(r[10]), photo: '',
    statut: /ferm|clôtur|clotur|closed/i.test(String(r[12])) ? 'Clôturé' : 'Ouvert', resp: String(r[13]), apres: ''
  };
}

function rInfo_(ss) {
  var sh = rTab_(ss), last = rLast_(sh), params = {};
  rTable_(ss, 'parametres').forEach(function (r) { params[String(r[0])] = String(r[1]); });
  return {
    nextNumber: rNext_(sh, last), nextRow: last + 1, params: params,
    regles: rTable_(ss, 'regles').map(function (r) { return { service: String(r[0]), zone: String(r[1]), responsable: String(r[2]) }; }),
    contacts: rTable_(ss, 'contacts').map(function (r) { return { nom: String(r[0]), email: String(r[1]) }; })
  };
}

function rAdd_(ss, p) {
  var lock = LockService.getScriptLock();
  lock.waitLock(20000);
  try {
    var sh = rTab_(ss), last = rLast_(sh), row = last + 1, t = p.tag || {};
    var n = parseInt(t.n, 10) || rNext_(sh, last);
    sh.getRange(row, 1, 1, 14).setValues([[
      n, rDate_(t.date), rDate_(t.cible), t.type || '', R_GRAV[t.grav] || t.grav || '', t.empl || '', t.zone || '',
      t.desc || '', t.risque || '', t.action || '', t.prop || '', '',
      t.statut === 'Clôturé' ? '✅ Fermé' : '🟥 Ouvert', t.resp || ''
    ]]);
    sh.getRange(row, 2).setNumberFormat('yyyy-mm-dd h:mm');
    sh.getRange(row, 3).setNumberFormat('dd/mm/yyyy');
    return { n: n, row: row, photo: '' };
  } finally { lock.releaseLock(); }
}

function rList_(ss, p) {
  var sh = rTab_(ss), last = rLast_(sh);
  if (last < RELAY.FIRST_ROW) return [];
  var vals = sh.getRange(RELAY.FIRST_ROW, 1, last - RELAY.FIRST_ROW + 1, 15).getValues(), out = [];
  for (var i = 0; i < vals.length; i++) {
    if (String(vals[i][0]).trim() === '') continue;
    var t = rTag_(ss, vals[i], RELAY.FIRST_ROW + i);
    if (p.status === 'open' && t.statut === 'Clôturé') continue;
    if (p.status === 'closed' && t.statut !== 'Clôturé') continue;
    out.push(t);
  }
  return p.limit ? out.slice(-p.limit) : out;
}

function rClose_(ss, p) {
  var sh = rTab_(ss);
  var rows = (p.rows || []).map(Number).filter(function (r) { return r >= RELAY.FIRST_ROW; });
  rows.forEach(function (r) { sh.getRange(r, R_COLS.statut).setValue('✅ Fermé'); });
  return { closed: rows.length, photoAfter: '' };
}

function rUpdate_(ss, p) {
  var sh = rTab_(ss), row = Number(p.row), f = p.fields || {};
  if (!(row >= RELAY.FIRST_ROW)) throw new Error('Ligne invalide');
  Object.keys(f).forEach(function (k) {
    if (!R_COLS[k] || k === 'n' || k === 'photo' || k === 'apres') return;
    var v = f[k];
    if (k === 'date' || k === 'cible') v = rDate_(v);
    if (k === 'grav') v = R_GRAV[v] || v;
    if (k === 'statut') v = (v === 'Clôturé' ? '✅ Fermé' : '🟥 Ouvert');
    sh.getRange(row, R_COLS[k]).setValue(v);
  });
  return rTag_(ss, sh.getRange(row, 1, 1, 15).getValues()[0], row);
}

function rPhoto_() { return { base64: '' }; }
