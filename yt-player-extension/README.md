# YouTube – vlastný prehrávač (rozšírenie pre Chrome)

Nahradí prehrávač na `youtube.com/watch?v=...` jednoduchým vlastným prehrávačom. Video sa získa cez
[`yt-dlp`](https://github.com/yt-dlp/yt-dlp), takže sa neprehrávajú reklamy. V rohu videa sú tlačidlá
**▶ Open in VLC** (otvorí video vo VLC) a **Pôvodný prehrávač** (vráti prehrávač YouTube). Zobrazia sa pri
prejdení myšou.

Je to osobný nástroj. Nie je v Chrome Web Store a používanie môže byť v rozpore s podmienkami YouTube.

## Požiadavky

- macOS
- Google Chrome
- [`yt-dlp`](https://github.com/yt-dlp/yt-dlp) (`brew install yt-dlp`) a pravidelne ho aktualizujte (`brew upgrade yt-dlp`),
  YouTube sa často mení
- [VLC](https://www.videolan.org/) v `/Applications` (len pre tlačidlo Open in VLC)

## Inštalácia

1. V termináli, v priečinku `yt-player-extension`:

   ```sh
   ./install.sh
   ```

   Ak sa zobrazí `Host registered.`, pokračujte.
2. V Chrome otvorte `chrome://extensions`, zapnite **Režim pre vývojárov** a kliknite na **Načítať rozbalené**.
   Vyberte priečinok `yt-player-extension/extension`.
3. Otvorte ľubovoľné video na YouTube. Po pár sekundách (kým `yt-dlp` zistí adresu videa) sa spustí nový prehrávač.

## Ovládanie

Medzerník alebo `k` prehrá/pozastaví, šípky vľavo/vpravo posun o 5 s, `j`/`l` o 10 s, `m` stlmí, `f` celá obrazovka.

## Obmedzenia

- Kvalita je až 1080p (H.264). Video a zvuk sa prehrávajú v dvoch synchronizovaných prvkoch, preto sa pri
  pretáčaní môže zvuk na chvíľu oneskoriť.
- Podporené sú len bežné videá (`/watch`), nie Shorts, živé prenosy, komentáre ani zoznamy skladieb.
- Videá s vekovým obmedzením alebo len pre členov nemusia fungovať. V takom prípade sa vráti pôvodný prehrávač.
- Titulky nie sú podporované a ani sa neplánujú.

## Riešenie problémov

| Problém | Riešenie |
|---|---|
| Vráti sa pôvodný prehrávač s hlásením | Spustite `yt-dlp` v termináli na tom videu a pozrite chybu, potom `brew upgrade yt-dlp` |
| `Specified native messaging host not found` | Spustite znova `./install.sh` a reštartujte Chrome |
| `Access to the specified native messaging host is forbidden` | Nahrajte rozšírenie znova z priečinka `yt-player-extension/extension` |
| `yt-dlp not found` | `brew install yt-dlp` |

## Odinštalovanie

```sh
rm "$HOME/Library/Application Support/Google/Chrome/NativeMessagingHosts/sk.yt.player.json"
```

Potom odstráňte rozšírenie na `chrome://extensions`.
