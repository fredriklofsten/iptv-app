# IPTV-spelare

Mediaspelare för IPTV (M3U, Xtream API och gratiskanaler) med inloggning, så att telefon och TV delar samma profil.

## Installera på Android-telefon eller Android TV / Fire TV
1. Gå till **Releases** här på GitHub och ladda ner den senaste `IPTV-spelare.apk`.
2. Öppna filen och tillåt "Installera okända appar" när du blir tillfrågad.
3. Tryck **Installera**.

Appen byggs automatiskt av GitHub Actions varje gång koden ändras.

## Innehåll
- `www/` – själva appen (HTML/JS)
- `android/` – Android-projektet (Capacitor)
- `supabase/` – databas och funktion för TV-inloggning med kod
