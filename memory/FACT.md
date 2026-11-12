# Build-Ausgabe und Versionierung

- **Ausgabeordner**: `F:\001_Coding_Projekte\LoloTrans\Ausgabe`
- Neue APK-Versionen werden dort mit Versionsnummer abgelegt (z. B. `LoloTrans-v1.0.apk`, `LoloTrans-v1.1.apk`, `LoloTrans-v1.2.apk`, `LoloTrans-v1.3.apk`, `LoloTrans-v1.4.apk`, `LoloTrans-v1.5.apk`)
- Vor jedem Release die APK aus `app/build/outputs/apk/debug/app-debug.apk` dorthin kopieren
- Versionierung: `versionCode` und `versionName` in `app/build.gradle.kts` vor Release hochzählen
- **Jede neue Ausgabe bekommt eine hochgezählte Version**
- Aktueller Stand: versionCode=13, versionName="2.2" (Stand 2026-07-13, HEAD main = 218eb5b9)
- Hinweis: Ausgabeordner laut FACT.md zeigt `F:\DeepSeek\Projekte\...`; tatsächlich liegt `Ausgabe/` im Projektordner `F:/001_Coding_Projekte/LoloTrans/Ausgabe/` (bei nächster Release-Kopie Pfad prüfen).

# F-Droid-Veröffentlichung (geprüft 2026-11-12)

- LoloTrans **ist im offiziellen F-Droid** (`fdroid`-Flavor, ohne ML Kit): https://f-droid.org/de/packages/de.lolo.lolotrans/
- Veröffentlichte Versionen: 2.0/11 (28.05.2026), 2.1/12 (03.06.2026), 2.2/13 (05.07.2026); `suggestedVersionCode` = 13, APK 9 MiB, arm64-v8a + armeabi-v7a, minSdk 26.
- Reproduzierbare Builds: alle drei ✔ (Verifikation 2.2 am 11.09.2026).
- fdroiddata: `metadata/de.lolo.lolotrans.yml`, Builds 11/12/13 mit Commit-SHAs, AutoUpdateMode `Version`, UpdateCheckMode `Tags`. Kein Build für 2.3/14.
- **IzzyOnDroid: kein Eintrag** (`apt.izzysoft.de/.../de.lolo.lolotrans` → 404).
- Die 2.2-APK stammt aus Commit `11715eaa` (Tag `v2.2`) und enthält noch SYSTEM_ALERT_WINDOW + Foreground-Dienst `specialUse`; der Accessibility-Overlay-Umbau (`08958e49`) ist **nicht** veröffentlicht.
- Lokale Kopie `fdroid/de.lolo.lolotrans.yml` weicht von fdroiddata ab (Commit als Tag-Name, andere Summary/Description) — nicht die Quelle des veröffentlichten Builds.
- `IZZYONDROID_SUBMISSION.md` ist veraltet (Stand 1.8/9).

# Fremde Uebersetzungs-Endpunkte und F-Droid-Anti-Features (geprueft 2026-11-12)

- Einschlaegig sind nur **NonFreeNet** (proprietaerer Dienst) und **TetheredNet** (eine Instanz).
  TetheredNet entfaellt laut F-Droid ausdruecklich, wenn die App auf eine alternative, oeffentlich
  verfuegbare, selbst-hostbare Serverloesung umkonfiguriert werden kann — unser URL-Feld erfuellt das.
- Zulaessig: Instanzen freier, selbst hostbarer Software (LibreTranslate, Lingva), frei konfigurierbar.
- Unzulaessig: Google/Yandex/DeepL/Bing, eingebaute Fremd-API-Keys, Umgehung von Zugangskontrollen.
- `https://www.translate.eu/libre/` ("Powered by LibreTranslate", werbefinanziert): Software frei ->
  kein Anti-Feature, **aber praktisch nicht nutzbar** — `/languages` und `/translate` antworten
  ohne Session mit `{"tr":"","err":""}`, auch mit Browser-UA/Origin/Referer (4 Formate getestet 2026-11-12).
- `https://translate-pa.googleapis.com/v1/translateHtml` (FreeTranslations): Googles Backend, kein
  offizieller Zugang; `github`-Flavor faelscht `Origin`/`Referer`. Waere NonFreeNet + Verstoss gegen
  "keine Rechte Dritter". Darf nie in den `fdroid`-Flavor.
- Der `fdroid`-Flavor ist sauber: nur LibreTranslate, Default-URL leer (SettingsRepository.kt:81-83).

# App-Icon (festgeschrieben 2026-11-12, Nutzerfreigabe)

- **Erzeugung:** `tools/make_icon.py` — liest `res/drawable/lolo_soft_logo.png`, schreibt
  `mipmap-*/ic_launcher_foreground.png` (hell) und `mipmap-night-*/ic_launcher_foreground.png` (dunkel).
- **Motiv:** nur das Zeichen 文A, freigestellt (Kreis, Glanz und Schatten des Originals entfernt).
- **Groesse:** 60 dp auf der 108-dp-Leinwand, quadratisch zentriert.
- **Verschiebung:** 2 px (bezogen auf 180 px Icon-Darstellung) nach links und oben — Nutzerkorrektur.
- **Schatten:** weich, nach rechts/unten (3,5 % / 4,5 % der Motivgroesse, Blur 2 %).
- **Farben:** helles Theme schwarzes Zeichen (#000000) auf #FFFFFF
  (`values/colors.xml`); dunkles Theme weisses Zeichen auf #000000 (`values-night/colors.xml`).
- **Belegt auf ingo:** Raender links 13, rechts 19, oben 19, unten 25 px bei 180 px Feld; vom Nutzer
  am 2026-11-12 als gut freigegeben.
- Die frueheren Varianten (Kreis-Logo als Foreground mit `inset 0%`, 60-dp/63-dp/66-dp-Zentrierung,
  reines Zeichen ohne Schatten) sind verworfen — nicht wieder aufnehmen.