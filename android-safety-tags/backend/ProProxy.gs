/**
 * Safety Tags Pro — serveur de l'abonnement Pro (Google Apps Script).
 *
 * - Vérifie l'identité de l'utilisateur (jeton Google « Continuer avec Google »).
 * - Indique s'il est Pro (liste PRO_EMAILS).
 * - Appelle l'IA (Groq) pour les utilisateurs Pro : la clé API reste sur ce serveur,
 *   elle n'est jamais dans l'application.
 *
 * Déploiement : script.google.com > Nouveau projet > coller ce fichier,
 * renseigner GROQ_API_KEY (ou la propriété de script du même nom),
 * puis Déployer > Application Web (Exécuter en tant que : Moi ; Accès : Tout le monde).
 */

var PRO = {
  WEB_CLIENT_ID: '990187324376-eddqqpq512mo2jo19b9kcv664gljnhim.apps.googleusercontent.com',
  MODEL: 'qwen/qwen3.8-27b',
  DAILY_LIMIT: 200,                 // analyses IA par utilisateur et par jour
  GROQ_API_KEY: '',                 // ou propriété de script GROQ_API_KEY
  PRO_EMAILS: ''                    // emails Pro séparés par des virgules (ou propriété PRO_EMAILS)
};

function prop_(k) { return PropertiesService.getScriptProperties().getProperty(k) || PRO[k] || ''; }

function doPost(e) {
  var out;
  try {
    var p = JSON.parse(e.postData.contents || '{}');
    var email = verify_(p.idToken);
    var pro = proList_().indexOf(email) >= 0;
    if (p.action === 'status') out = { ok: true, data: { email: email, pro: pro, model: PRO.MODEL } };
    else if (p.action === 'ai') {
      if (!pro) throw new Error('Fonction réservée à Safety Tags Pro.');
      out = { ok: true, data: { text: ai_(email, p.prompt, p.image) } };
    } else throw new Error('Action inconnue');
  } catch (err) {
    out = { ok: false, error: String((err && err.message) || err) };
  }
  return ContentService.createTextOutput(JSON.stringify(out)).setMimeType(ContentService.MimeType.JSON);
}

function doGet() {
  return ContentService.createTextOutput(JSON.stringify({ ok: true, service: 'Safety Tags Pro' }))
    .setMimeType(ContentService.MimeType.JSON);
}

function proList_() {
  return String(prop_('PRO_EMAILS')).toLowerCase().split(/[,;\s]+/).filter(String);
}

function verify_(idToken) {
  if (!idToken) throw new Error('Connectez-vous avec Google pour utiliser Pro.');
  var r = UrlFetchApp.fetch('https://oauth2.googleapis.com/tokeninfo?id_token=' + encodeURIComponent(idToken),
    { muteHttpExceptions: true });
  if (r.getResponseCode() !== 200) throw new Error('Session Google expirée : reconnectez-vous.');
  var t = JSON.parse(r.getContentText());
  if (t.aud !== PRO.WEB_CLIENT_ID) throw new Error('Jeton Google non reconnu.');
  if (String(t.email_verified) !== 'true') throw new Error('Adresse Google non vérifiée.');
  return String(t.email).toLowerCase();
}

function ai_(email, prompt, image) {
  var key = prop_('GROQ_API_KEY');
  if (!key) throw new Error('Clé IA non configurée sur le serveur.');
  // quota journalier
  var props = PropertiesService.getScriptProperties();
  var day = Utilities.formatDate(new Date(), 'GMT', 'yyyyMMdd');
  var ck = 'n_' + day + '_' + email;
  var n = Number(props.getProperty(ck) || 0);
  if (n >= PRO.DAILY_LIMIT) throw new Error('Limite quotidienne d’analyses IA atteinte.');
  props.setProperty(ck, String(n + 1));

  var content = [{ type: 'text', text: String(prompt || '') }];
  if (image) content.push({ type: 'image_url', image_url: { url: 'data:image/jpeg;base64,' + image } });
  var body = {
    model: PRO.MODEL,
    messages: [{ role: 'user', content: content }],
    temperature: 0.2,
    max_completion_tokens: 1500,
    response_format: { type: 'json_object' }
  };
  var r = UrlFetchApp.fetch('https://api.groq.com/openai/v1/chat/completions', {
    method: 'post', contentType: 'application/json', muteHttpExceptions: true,
    headers: { Authorization: 'Bearer ' + key }, payload: JSON.stringify(body)
  });
  var j = JSON.parse(r.getContentText());
  if (r.getResponseCode() >= 400) throw new Error('IA : ' + ((j.error && j.error.message) || r.getResponseCode()));
  return j.choices[0].message.content;
}

/** Test manuel depuis l'éditeur : vérifie que la clé et le modèle répondent. */
function testIA() {
  Logger.log(ai_('test@local', 'Réponds en JSON : {"ok": true}', null));
}
