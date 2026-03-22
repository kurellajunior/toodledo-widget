# Projektplan.md

Ziel ist ein einfaches Widget für Android zu erstellen, dass die fälligen und
anstehenden Tasks aus Toodledo anzeigt. Dieses Widget soll über den Appstore
verfügbar sein.

# Produktbeschreibung

## Design

Ein Beispieldesign liegt in der Datei Layout-Beispiel.png vor. Dort sind 3
überfällige Aufgaben (rötlicher Hintergrund) mit normaler Dringlichkeit
(grünes Kästchen) und eine Aufgabe für heute mit höchster Dringlichkeit zu
sehen. Farbschema: 
  Höchste ⇒ rötlich
  Hoch ⇒ orange
  Normal ⇒ grün
  Niedrig ⇒ bläulich
  ohne/Negativ ⇒ grau

- Das Widget soll die Aufgaben Zeilenweise anzeigen.
- am linken Rand ein quadratisches Kästchen. Die Farbe entsprechend der
  Dringlichkeit (priority in Toodledo) der Aufgabe
- daneben der Titel der Aufgabe. Wenn länger als das Widget Platz hat wird der
  Titel abgeschnitten
- Aufgaben mit Notiztext bekommen ein Dokument-Symbol am rechten Rand
- sich wiederholende Aufgaben bekommen einen Kreispfeil am rechten Rand
- Überfällige Aufgaben werden rötlich hinterlegt.
- aktuell fällige Aufgaben werden normal angezeigt. Als aktuell fällig gelten
  Aufgaben deren Beginndatum erreicht oder überschritten ist, deren due date
  heute oder in der Zukunft ist.
- kommende Aufgaben werden durch einen schmalen, dunkelgrauen, beschrifteten
  Balken abgetrennt. Anzahl und Beschriftung der Sektionen soll
  automatisch/sinnvoll erstellt werden. Keine Aufgaben ohne due date
- Sortierung
  1. nach date (due date bei Vergangenen und aktuellen Tasks, begin date bei zukünftigen Tasks)
  2. nach Drinlichkeit
  3. Alphabetisch nach Titel
- Die Hintergrundfarbe des Widgets soll standardmäßig vom Systemdesign des
  Starters übernommen werden
- Die Schriftgröße soll sich an der Systemschriftgröße orientieren
- Darstellung insgesamt kompakt, nur sehr geringe Abstände zw. Schrift und
  Zeile
- Größe der Zeile richtet sich nach der Schriftgröße, Größe des Quadrats nach
  der Zeilenhöhe.
- Das Widget wird vollständig ausgefüllt. Ein sehr dünner, kontrastreicher
  Rahmen drumherum.
- Statusinformationen (Login-Prompt, Fehler) werden als erstes Listenelement
  angezeigt, gestylt wie Sektions-Header (grau, zentriert, klickbar)
- Widget soll rezisable sein. Defaultgröße 1 hoch, 4 breit. Nur eine
  Widgetinstanz vorgesehen. Der Nutzer kann es auf die gewünschte Größe
  ziehen. Designidee ist, dass nur die nächsten 3-5 Tasks sichtbar sind, da
  sie ja nach Abarbeitung verschwinden.

## Interaktion

- Wenn der Nutzer nicht eingeloggt ist oder der Login abgelaufen ist: zeige
  entsprechende Meldung als erste Zeile an.
  - beim Tip auf diese Meldung öffnet sich die Einstellungsseite zum Anmelden
- Swipe: Nach oben und nach unten wischen scrollt durch die Liste, wenn das
  Widget nicht alle Aufgaben anzeigen kann
- Nach unten swipen über das Ende hinaus löst eine Aktualisierung vom Server
  aus (Refresh-Zeile am Listenende)
- Tippen auf das Quadrat links markiert die Aufgabe als jetzt erledigt auf dem
  Server und aktualisiert danach die Liste.
- Tippen auf die Aufgabe sonst öffnet die Aufgabe in Toodledo, wenn
  installiert, sonst im Browser auf toodledo
- long press auf das Widget öffnet das Systemseitige Widgetmenü mit der
  Einstellungsseite
- Fehlerverhalten: Bei Netzwerk- oder API-Fehlern wird die bisherige
  Aufgabenliste beibehalten. Der Fehlerstatus wird als erstes Listenelement
  angezeigt (Tap löst erneuten Fetch aus). Beim allerersten Fehler nach
  Install ist die Liste leer.
- Die Liste sollte sich automatisch im Hintergrund aktualisieren. Stromsparend
  sollte alle Stunde reichen (WorkManager).

- Keine Benachrichtigungen, keine Sonderrechte.

## Einstellmöglichkeiten

- Die Transparenz der Hintergrundfarbe (standard sollte 25% sein, kaum
  durchsichtig)
- Die Schriftgröße relativ zur Systemgröße (in %, Standard ist 100%)
- Die Hintergrundfarbe (standard sollte vom aktuellen Theme genommen werden)
- Login / Logout Button
- Schließen-Button (schließt die Einstellungsseite)
- Dynamische Button-Hervorhebung: Anmelden ist CTA wenn abgemeldet,
  Schließen ist CTA wenn angemeldet (über Alpha-Werte)
- Spendenhinweis mit PayPal-Link am unteren Rand

## Sprachen

Die wenigen Worte (Trennlinie zu zukünftigen Aufgaben, Einstellungen) sollen
in allen von Toodledo unterstützten Sprachen verfügbar sein: English,
简体中文, Nederlands, Français, Deutsch, Italiano, 日本語, 한국어,
Português, Русский, Español, Svenska.

Der Eintrag im Playstore soll Englisch sein.

## Architektur

1. Toodledo Connection
  - Login über Toodledos OAuth Protokoll. Tokenspeicherung mit sinnvollem
    Standard auf Android
  - Rate-Limits and scheduling: follow guidelines on https://api.toodledo.com/3/account/doc_sync.php
  - Synchronization: We should only read, no local data modification, no
    sync-concept. Modifications should happen via API and then read again the
    data.
  - Nur die Informationen laden, die benötigt werden: id, priority, title,
    begind and due date, repeat, note. Keine completed laden/zeigen. Repeat
    nur für die Anzeige des Kreispfeils. Note nur für die Anzeige des
    Dokument-Symbols (ob vorhanden, nicht der Inhalt).
  - API-Key Handling: Client ID und Client Secret werden nicht im Repo
    gespeichert. Sie werden über `local.properties` (gitignored) eingetragen
    und zur Build-Zeit als BuildConfig-Felder injiziert. OAuth-Tokens des
    Nutzers werden in EncryptedSharedPreferences gespeichert.
    Hinweis: In der kompilierten APK sind Client ID/Secret prinzipiell
    extrahierbar — ein Backend-Proxy wäre sicherer, ist aber für dieses
    Projekt unverhältnismäßig. Bei Missbrauch kann der Key bei Toodledo
    revoked werden.
  - `FetchResult` sealed interface: typisiertes Ergebnis von `fetchTasks()`
    mit den Varianten `Success(tasks)`, `AuthError`, `NetworkError`, `ApiError`.
2. Widget-Status
  - `WidgetStatus` Enum als Single Source of Truth (in SharedPreferences):
    INITIAL, LOADED, OFFLINE, API_ERROR, LOGGED_OUT
  - Die Factory setzt den Status nach jedem Fetch und erzeugt bei Nicht-LOADED
    ein `StatusItem` als erstes Listenelement.
  - Statusanzeige ist ein reguläres ListView-Item (kein separates View außerhalb
    der Liste). Dadurch keine Race Conditions zwischen `updateAppWidget` und
    `partiallyUpdateAppWidget` — insbesondere relevant für Android 16, wo
    `updateAppWidget` mit `setRemoteAdapter` die ListView zurücksetzt.
  - Bei Fehler-Status bleibt die bisherige Aufgabenliste erhalten.
3. Tech-Stack
  - Kompatibilität: Testgerät hat Android 16 (Fairphone 6). Getestet auf
    API 36 Emulator. Ideal bis Android 11. Maximal back bis Android 8.
  - Verwende möglichst verbreitet Standard-Technologien.
4. Offline/Cache
  - Kein lokaler Speicher. Widget zeigt StatusItem an, wenn noch keine
    Daten geladen wurden/werden konnten

## Lizenz
                                                                                                                                                    
CC BY-NC-SA 4.0 (Creative Commons Attribution-NonCommercial-ShareAlike).
LICENSE-Datei mit dem offiziellen Lizenztext. README enthält einen kurzen
Hinweis mit Link zur Lizenz und Namensnennung (Jan Kurella).

# Implementierung

## Phase 0 – prepare repo

Initialisiere ein git-repo auf github. Konfiguriere meinen privaten user
kurellajunior als git user

Erstelle eine erste Readme, die den Zweck dieses Repos erklärt. Nutze Englisch
dafür. Mache klar, dass es keine Verbindung zu Toodledo sondern dies ein
privates Fan-projekt ist. Nenne die Lizenz.

Erstelle ein Icon für die App. Genutzt sowohl im PladStore, Appliste als auch
auf Toodledo. Alle notwendigen Formate für den Playstore plus 64x64px png für
Toodledo.

Nachdem das git repo angelegt ist, muss die App bei Toodledo registriert
werden und der API-key erstellt werden. Dazu muss die Redirect-URL festgelegt
werden für das Login. Schlage eine sinnvolle vor.


## Phase 1 – make it work

Implementiere das Widget, so dass ich es über ein USB-Kabel auf meinem Telefon
testen kann. Teste, wenn möglich selber über Simulatoren.

Toodledo API docs: https://api.toodledo.com/3/index.php

Iteriere die Implementierung, bis ich zufrieden bin.

Commite die funktionierende Implementierung.

## Phase 2 – make it beautiful

Verwende die notwendigen Skills (Coder, Reviewer, Android-Dev) um den
Source-code reif für die Veröffentlichung zu bekommen.

Ziel ist möglichst einfacher, leicht zu wartender Code. Keine fancy Pattern,
keine unnötigen Abstraktionen. Wenige files, möglichst wenige Pakete.

## Phase 3 – Publish im Playstore

Bereite die Veröffentlichung im Playstore vor.

- Privacy Policy über github-Projekt? Verwende einen sinnvollen Standard
- starte mit Version 0.1.0
- Weitere Schritte im Dialog ermitteln
  - Screenshots?

Information im PlayStore, kann noch sinnvoll ergänzt werden mit tatsächlichen
Features und Einstellmöglichkeiten:

  This is a simple Widget to show the current and upcoming tasks from Toodledo.

  It is a private project, meant to be simple and useful. It fetches your
  tasks and displays them without persisting them locally, so they will be
  only visible after a restart once you were online.

  Features
  - Trigger a manual refetch
  - mark done by hitting the check box
  - open the task in App or Website by hitting the title

  12 Languages supported, generated by @claude. I added Languages Toodledo
  wants to support too

Dokumentiere, was ich tun muss, um im Playstore zu publishen, erstelle
notwendige Scripte, um den Prozess in der Zukunft einfach zu gestalten.

### Erledigt in Phase 3

- Play Store Listings in 12 Sprachen erstellt
- Status State Machine (5 Zustände) mit typisiertem FetchResult
- Statusanzeige als ListView-Item (statt separatem View) — behebt
  RemoteViews-Race-Condition auf Android 16
- Shortcut „Aufgabe hinzufügen" entfernt (AddTaskActivity, shortcuts.xml,
  ic_add_task.xml). Wird durch ein separates 1x1 Add-Task Widget ersetzt.
- Settings: Schließen-Button, dynamische CTA-Hervorhebung, Spendenhinweis
- Projektplan aktualisiert

### Offen

- 1x1 Add-Task Widget (Icon-Design-Phase, dann Implementierung)

