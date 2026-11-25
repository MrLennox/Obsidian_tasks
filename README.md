# Obsidian Todo Widget (Android)

Eine schlanke, native Android-App mit interaktivem **Homescreen-Widget (Jetpack Glance & Material 3)**, die offene Aufgaben aus deinem lokalen **Obsidian-Vault** direkt auf dem Startbildschirm anzeigt.

## Funktionen

- **Gesamter Vault als Quelle:** Durchsucht rekursiv alle `.md`-Dateien deines lokalen Obsidian-Vaults über das Android Storage Access Framework (versteckte Ordner wie `.obsidian`, `.trash` oder `.git` sowie Codeblöcke werden ignoriert).
- **Obsidian Tasks & Dataview Support:** Erkennt neben Standard-Checkboxen (`- [ ]`, `* [ ]`, `1. [ ]`) auch Fälligkeits-/Planungsdaten (`📅 YYYY-MM-DD`, `⏳ YYYY-MM-DD`, `[due:: ...]`, `(due:: ...)`) sowie Prioritäten (`🔺`, `⏫`, `🔼`, `🔽`, `⏬`, `[priority:: ...]`).
- **Direkt abhaken:** Ein Tipp auf die Checkbox im Widget schreibt `- [ ]` direkt in der jeweiligen Markdown-Datei zu `- [x]` um (wahlweise mit `✅ YYYY-MM-DD`, `[completion:: YYYY-MM-DD]` oder schlicht `- [x]`).
- **Quick-Add (`+`) vom Homescreen:** Öffnet einen kompakten Dialog zum schnellen Erfassen neuer Aufgaben (inkl. Schnellwahl für Heute/Morgen und Priorität) in eine konfigurierbare Zieldatei (Standard: `Inbox.md`).
- **Direkt in Obsidian öffnen:** Ein Tipp auf den Aufgabentext öffnet die zugehörige Notiz direkt in Obsidian (`obsidian://open?...`).
- **Automatische Hintergrund-Aktualisierung:** `WorkManager` aktualisiert das Widget regelmäßig im Hintergrund, zusätzlich gibt es einen `↻`-Button direkt im Widget.

---

## APK über GitHub Actions bauen & installieren

1. Erstelle ein neues GitHub-Repository und pushe dieses Projekt dorthin:
   ```bash
   git add .
   git commit -m "Initial commit: Obsidian Todo Widget"
   git remote add origin https://github.com/<DEIN-USER>/<DEIN-REPO>.git
   git push -u origin main
   ```
2. Öffne auf GitHub den Reiter **Actions** → **Android CI & APK Build**.
3. Nach erfolgreichem Durchlauf kannst du ganz unten bei **Artifacts** die Datei `obsidian-todo-widget-apk` (`app-debug.apk`) herunterladen.
4. Übertrage die APK auf dein Android-Smartphone und installiere sie (ggf. *„Apps aus unbekannten Quellen installieren“* für deinen Browser/Dateimanager erlauben).
   - *Tipp:* Wenn du einen Git-Tag wie `v1.0.0` pushst (`git tag v1.0.0 && git push origin v1.0.0`), erstellt GitHub Actions automatisch ein **GitHub Release** mit angehängter APK.

## Einrichtung auf dem Smartphone

1. Öffne **Obsidian Todos** und tippe auf **Obsidian-Vault auswählen**.
2. Wähle deinen lokalen Obsidian-Vault-Ordner aus und erlaube den Zugriff.
3. Tippe auf **Widget zum Startbildschirm hinzufügen** (oder füge das Widget *„Obsidian Todos“* manuell über langes Drücken auf den Homescreen hinzu).

## Lokaler Build (optional mit Android Studio)

- Öffne diesen Ordner direkt in **Android Studio** (JDK 17 und Android SDK 35 werden automatisch erkannt/heruntergeladen), oder baue per Terminal:
  ```bash
  ./gradlew testDebugUnitTest assembleDebug
  ```
