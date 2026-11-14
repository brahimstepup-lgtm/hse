# Safety Tags — application Android

Application Android pour créer et suivre les Safety Tags HSE :

- **Nouveau tag** : photo (appareil photo, galerie, ou partage direct depuis WhatsApp), remplissage automatique par IA (optionnel), choix du responsable selon les règles, aperçu de la fiche « SAFETY TAG » en PNG, enregistrement dans le Google Sheet + photo dans Drive, email Outlook prérempli avec la fiche jointe.
- **Tags ouverts** : recherche, filtre par responsable / date / hors bâtiment, clôture unitaire (avec photo après) ou en lot, relance par email.
- **Réglages** : connexion au script, clé IA, contacts.

Les noms, emails, règles d'affectation et paramètres ne sont pas dans ce dépôt : ils sont lus dans les onglets `regles`, `contacts` et `parametres` du Google Sheet.

## Installation du pont Google Sheet

Voir l'en-tête de [`backend/Code.gs`](backend/Code.gs).

## Compilation

GitHub Actions (`.github/workflows/android-safety-tags.yml`) compile l'APK à chaque push sur la branche `android-safety-tags`. L'APK est publié sur la branche `android-build` et en artefact du workflow.
