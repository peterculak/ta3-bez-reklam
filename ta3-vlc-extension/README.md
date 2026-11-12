# TA3 bez reklám – Open in VLC (rozšírenie pre Chrome)

**TA3 bez reklám**: pozerajte relácie a archív TA3 vo VLC, bez reklám a bez webového prehrávača.
Rozšírenie pridá oranžové tlačidlo **▶ Open in VLC** vedľa červeného tlačidla *Live* v hlavičke TA3 na
stránkach s videom (`ta3.com/relacie/...`). Kliknutím sa video otvorí priamo vo VLC. Hodí sa aj vtedy, keď
sa prehrávač na stránke len točí a video nenaštartuje.

Kľúčové slová: TA3 bez reklám, TA3 archív, Hlavné správy, stiahnuť TA3, TA3 VLC, rozšírenie Chrome.

## Ako to funguje

Chrome nemôže spúšťať programy na počítači, preto rozšírenie komunikuje s malým pomocným skriptom v Bashi
(`host/ta3_vlc_host.sh`) cez
[native messaging](https://developer.chrome.com/docs/extensions/develop/concepts/native-messaging).
Skript spustí VLC a prijíma iba adresy `https://*.livebox.sk` (prísny zoznam povolených znakov).

Súbor `key.pem` a pole `"key"` v `manifest.json` slúžia len na to, aby mal rozšírenie stále rovnaké ID
(`extension-id.txt`). Vďaka tomu môže registrácia pomocníka povoliť presne toto rozšírenie.

## Požiadavky

- macOS (pre Windows a Linux treba vlastného pomocníka, zatiaľ nie je hotový)
- Google Chrome
- [VLC](https://www.videolan.org/) nainštalovaný v `/Applications`

Nič iné netreba. Pomocník používa len nástroje, ktoré sú súčasťou macOS.

## Inštalácia

Inštalácia má dva kroky, oba je potrebné urobiť. Rozšírenie samotné bez pomocníka nič neotvorí.

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

## Aktualizácia

Po stiahnutí zmien v súboroch rozšírenia kliknite na `chrome://extensions` na ikonu obnovenia na jeho karte
a potom obnovte kartu s TA3. Skript `./install.sh` spúšťajte znova iba vtedy, ak ste presunuli priečinok projektu.

## Riešenie problémov

| Problém | Príčina / riešenie |
|---|---|
| Na stránke nie je tlačidlo | Obnovte kartu, skontrolujte, či je rozšírenie zapnuté a či stránka obsahuje video |
| Tlačidlo sa zobrazí ako plávajúce okienko, nie vedľa *Live* | TA3 zmenila hlavičku stránky, ide o zámernú záložnú verziu |
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
