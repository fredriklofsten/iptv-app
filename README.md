# IPTV-spelare

Mediaspelare för IPTV (M3U, Xtream API och gratiskanaler) med inloggning, profiler och synk mellan telefon och TV.

Alla filer finns under **Releases** här på GitHub. Den senaste versionen finns alltid på:
https://github.com/fredriklofsten/iptv-app/releases/latest

## Android-telefon, Android TV, Google TV och Fire TV
1. Ladda ner `IPTV-spelare.apk` (på TV: använd appen **Downloader** och skriv in länken nedan).
   https://github.com/fredriklofsten/iptv-app/releases/latest/download/IPTV-spelare.apk
2. Öppna filen och tillåt "Installera okända appar" när du blir tillfrågad.
3. Tryck **Installera**. Nya versioner meddelas i appen och installeras ovanpå den gamla.

### Vilken fil passar min TV?
| TV-märke | System | Så installerar du |
|---|---|---|
| TCL, Sony, Philips, Xiaomi, Hisense (Android-modeller), Nvidia Shield, Chromecast med Google TV | Google TV / Android TV | `IPTV-spelare.apk` via **Downloader** (se nedan) |
| Amazon Fire TV | Fire OS | `IPTV-spelare.apk` via **Downloader** |
| LG | webOS | `IPTV-spelare-LG.ipk` (se LG nedan) |
| Samsung | Tizen | `IPTV-spelare-Samsung-osignerad.wgt` (se Samsung nedan) |
| Hisense (VIDAA), Panasonic, Philips (Titan OS) och andra | Egen webbläsare | Öppna TV:ns webbläsare och gå till `https://fredriklofsten.github.io/iptv-app/#tv` |

Appen känner själv av att den körs på en TV och ritar alltid i 1920×1080, så den ser likadan ut på alla märken.

### Google TV (t.ex. TCL): tillåt appar från Downloader
Google TV blockerar appar utanför Play Butik tills du slår på utvecklarläget en gång:
1. **Inställningar → System → Om → Android TV OS-version**: tryck på raden **7 gånger** tills det står att du är utvecklare.
2. **Inställningar → Appar → Säkerhet och begränsningar → Okända källor**: slå på **Downloader**.
3. Öppna Downloader, skriv in länken ovan och tryck **Installera**.

Får du "Appen installerades inte": avinstallera den gamla versionen först. Är minnet fullt (vanligt på TCL): rensa cache under Inställningar → Appar.

## LG-TV (webOS)
1. Installera appen **Developer Mode** från LG Content Store på TV:n och logga in med ett gratis LG-utvecklarkonto (webostv.developer.lge.com).
2. Slå på **Dev Mode Status** och starta om TV:n. Notera TV:ns IP-adress.
3. På datorn: installera Node.js och kör `npm install -g @webos-tools/cli`.
4. Koppla datorn till TV:n: `ares-setup-device` (lägg till TV:ns IP, användare `prisoner`), sedan `ares-novacom --device tv --getkey` med koden som visas i Developer Mode-appen.
5. Ladda ner `IPTV-spelare-LG.ipk` och kör `ares-install --device tv IPTV-spelare-LG.ipk`.
6. Utvecklarläget måste förlängas med jämna mellanrum i Developer Mode-appen, annars tas appen bort.

## Samsung-TV (Tizen)
Samsung kräver att appen signeras med ett certifikat som är kopplat till just din TV.
1. Installera **Tizen Studio** med *TV Extensions* och *Samsung Certificate Extension* på datorn.
2. På TV:n: öppna **Apps**, tryck **1 2 3 4 5** på fjärrkontrollen, slå på **Developer mode** och ange datorns IP. Starta om TV:n.
3. I Tizen Studio: **Device Manager** → lägg till TV:n. **Certificate Manager** → skapa ett Samsung-certifikat (logga in med Samsung-konto, välj TV:ns DUID).
4. Ladda ner `IPTV-spelare-Samsung-osignerad.wgt`, packa upp den i ett nytt Tizen-projekt (Web App) eller signera den med `tizen package -t wgt -s <certifikatprofil> -- <mapp>`.
5. Installera: `tizen install -n IPTV-spelare.wgt -t <TV-namn>`.

## Engångsinställningar för ägaren
- **Webbversion och bekräftelsemejl:** Settings → Pages → Source: **GitHub Actions**. Webbversionen hamnar på https://fredriklofsten.github.io/iptv-app/.
  I Supabase: Authentication → URL Configuration → **Site URL** = `https://fredriklofsten.github.io/iptv-app/` och lägg samma adress under *Redirect URLs*.
- **Signeringsnyckel som hemlighet:** Settings → Secrets and variables → Actions → New repository secret:
  `KEYSTORE_B64` och `KEYSTORE_PASSWORD` (redan inlagda). Nyckeln finns inte i repot.

## Innehåll
- `www/` – själva appen (HTML/JS), används av alla plattformar
- `android/` – Android-projektet (Capacitor) med plugin för extern spelare
- `tv/` – inställningar och ikoner för LG (webOS) och Samsung (Tizen)
- `supabase/` – databas och funktion för TV-inloggning med kod
