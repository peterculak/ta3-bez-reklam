# TA3 bez reklám – Open in VLC (rozšírenie pre Chrome)

**TA3 bez reklám**: pozerajte relácie a archív TA3 bez reklám a bez zaseknutého prehrávača.

Rozšírenie robí na stránkach s archívnym videom (`ta3.com/relacie/...`) dve veci:

1. **Vlastný prehrávač.** Nahradí prehrávač TA3 jednoduchým prehrávačom, ktorý spustí video hneď, bez reklám.
   Funguje aj vtedy, keď sa pôvodný prehrávač len točí. Ak by niečo zlyhalo, vráti sa pôvodný prehrávač, a
   kedykoľvek naň môžete prepnúť odkazom **Pôvodný prehrávač TA3** v rohu videa.
2. **Tlačidlo ▶ Open in VLC** vedľa červeného tlačidla *Live* v hlavičke. Jedným kliknutím sa relácia otvorí vo VLC.

Prehrávanie priamych prenosov (Live) zatiaľ nie je podporované. Výber kvality vo vlastnom prehrávači zatiaľ
nie je. **Titulky nie sú podporované a ani sa neplánujú** (nie sú potrebné).

## JOJ play

Rovnaký princíp funguje aj pre JOJ (napr. Bez servítky). JOJ používa dva rôzne prehrávače a rozšírenie
zvláda oba:

- **`play.joj.sk/player/...`**: prehrávač Tivio s prúdom **DASH** (`.mpd`), preto rozšírenie obsahuje aj
  knižnicu dash.js. Adresa prúdu obsahuje token a pôvodný prehrávač ju získa až za behu.
- **`www.joj.sk/relacia/.../epizoda/...`**: prehrávač vložený cez `media.joj.sk/embed/...` s bežným **HLS**
  (`master.m3u8`), ktorý sa prehráva cez hls.js.
- V oboch prípadoch rozšírenie zachytí adresu prúdu, ktorú si stránka vyžiada, pôvodný prehrávač stlmí
  a pozastaví a ten istý prúd prehrá vo vlastnom prehrávači (až 1080p, bez DRM).
- V rohu videa je **▶ Open in VLC** a odkaz **Pôvodný prehrávač** na návrat k pôvodnému prehrávaču.
- Chrome blokuje automatické spustenie so zvukom, video spustíte jedným kliknutím na play.
- Pôvodný prehrávač sa načíta ako prvý (z neho pochádza token), takže chvíľu po otvorení stránky ešte môže
  krátko hrať. Rozšírenie ho hneď po zachytení prúdu stíši.
- Podporené sú len prehrávače `play.joj.sk/player/*` a `media.joj.sk/embed/*` (vrátane vložených na `www.joj.sk`),
  nie ostatné časti JOJ (napr. živé vysielanie).

Kľúčové slová: TA3 bez reklám, TA3 archív, Hlavné správy, JOJ play, Bez servítky, stiahnuť TA3, TA3 VLC, rozšírenie Chrome.

## Ako to funguje

Vlastný prehrávač funguje priamo v prehliadači a nepotrebuje nič ďalšie. Na otvorenie vo VLC je potrebný malý
pomocný skript v Bashi (`host/ta3_vlc_host.sh`), s ktorým rozšírenie komunikuje cez
[native messaging](https://developer.chrome.com/docs/extensions/develop/concepts/native-messaging), pretože Chrome
nemôže sám spúšťať programy na počítači. Skript spustí VLC a prijíma iba adresy `https://*.livebox.sk` a `https://*.joj.sk`
(prísny zoznam povolených znakov).

Súbor `key.pem` a pole `"key"` v `manifest.json` slúžia len na to, aby mal rozšírenie stále rovnaké ID
(`extension-id.txt`). Vďaka tomu môže registrácia pomocníka povoliť presne toto rozšírenie.

Rozšírenie obsahuje knižnice [hls.js](https://github.com/video-dev/hls.js) (licencia Apache-2.0) a
[dash.js](https://github.com/Dash-Industry-Forum/dash.js) (licencia BSD-3-Clause), súbory v `extension/vendor/`.

## Požiadavky

- macOS (pre Windows a Linux treba vlastného pomocníka, zatiaľ nie je hotový)
- Google Chrome
- [VLC](https://www.videolan.org/) nainštalovaný v `/Applications`

Nič iné netreba. Pomocník používa len nástroje, ktoré sú súčasťou macOS.

## Inštalácia

Ak chcete iba vlastný prehrávač, stačí **krok 2**. Krok 1 (pomocník) je potrebný len pre tlačidlo **Open in VLC**.

### Krok 1: Zaregistrujte pomocníka (stačí raz)

V termináli:

```sh
cd ta3-vlc-extension
./install.sh
```

Skript vytvorí súbor
`~/Library/Application Support/Google/Chrome/NativeMessagingHosts/sk.ta3.vlc.json`,
ktorý Chromu povie, kde je `host/ta3_vlc_host.sh`, a povolí komunikáciu iba tomuto rozšíreniu.

Ak sa po spustení zobrazí `Host registered.`, krok 1 je hotový.

### Krok 2: Nahrajte rozšírenie do Chrome

1. Otvorte v Chrome adresu `chrome://extensions`.
2. Vpravo hore zapnite **Režim pre vývojárov** (*Developer mode*).
3. Kliknite na **Načítať rozbalené** (*Load unpacked*) a vyberte priečinok `ta3-vlc-extension/extension`.
4. Skontrolujte, že je rozšírenie **zapnuté** (prepínač na jeho karte).

Na karte rozšírenia musí byť ID `hmeepeicibmdpfpbcjmiaaekmkaiijle` (rovnaké ako v `extension-id.txt`).
Ak je iné, pomocník odmietne spojenie.

### Krok 3: Vyskúšajte

1. Otvorte ľubovoľnú reláciu na TA3, napríklad
   `https://www.ta3.com/relacie/hlavne-spravy/relacia/1082413/hlavne-spravy-z-12-novembra`.
2. Ak bola stránka otvorená už predtým, obnovte ju (F5).
3. V hlavičke vedľa červeného **Live** uvidíte oranžové tlačidlo **▶ Open in VLC**. Kliknite naň.
4. Spustí sa VLC a začne prehrávať video.
5. Video na stránke by sa malo prehrať aj v novom prehrávači (po kliknutí na play), bez reklám.

## Aktualizácia

Po stiahnutí zmien v súboroch rozšírenia kliknite na `chrome://extensions` na ikonu obnovenia na jeho karte
a potom obnovte kartu s TA3. Skript `./install.sh` spúšťajte znova iba vtedy, ak ste presunuli priečinok projektu.

## Riešenie problémov

| Problém | Príčina / riešenie |
|---|---|
| Na stránke nie je tlačidlo | Obnovte kartu, skontrolujte, či je rozšírenie zapnuté a či stránka obsahuje video |
| Tlačidlo sa zobrazí ako plávajúce okienko, nie vedľa *Live* | TA3 zmenila hlavičku stránky, ide o zámernú záložnú verziu |
| Na JOJ sa objaví pôvodný prehrávač | Rozšírenie do cca 30 s nezachytilo adresu `.mpd` alebo dash.js zlyhal (chyba `[JOJ VLC]` v konzole), pôvodný prehrávač zostáva ako záloha |
| Hlásenie `Specified native messaging host not found` | Spustite `./install.sh` a reštartujte Chrome |
| Hlásenie `Access to the specified native messaging host is forbidden` | ID rozšírenia sa líši od `extension-id.txt`, nahrajte ho znova z tohto priečinka |
| Hlásenie `Native host has exited` | Spustite znova `./install.sh` (presunuli ste priečinok?) a skontrolujte, či je VLC v `/Applications` |
| VLC sa otvorí, ale zobrazí chybu | Vypršal prístupový token alebo TA3 zmenila formát adresy. Skúste znova a porovnajte `background.js` s `ta3-cli/downloader.js` |

Skutočnú chybu nájdete v konzole vývojárskych nástrojov stránky (`[TA3 VLC]`) alebo v logu service workera
(`chrome://extensions` → *service worker*).

## Odinštalovanie

```sh
rm "$HOME/Library/Application Support/Google/Chrome/NativeMessagingHosts/sk.ta3.vlc.json"
```

Potom odstráňte rozšírenie na `chrome://extensions`.
