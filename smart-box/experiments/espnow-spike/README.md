# ESP-NOW-Spike (Phase 1, plan-espnow-migration.md)

Minimaler Hands-on-Test: Sender schickt alle 5 s einen Befehl, der beim Empfänger die Onboard-LED 1 s aufleuchten lässt — direkt oder über ein Relay (Hop-Szenario aus ADR-002, unverschlüsselt und statisch konfiguriert).

**Kein Produktionscode.** Frames sind unverschlüsselt; der Routing-Header (Ziel, Quelle, Frame-ID, TTL) nimmt nur das Konzept aus ADR-002 vorweg. Die Scripte sind für **leere / frisch geflashte** ESPs gedacht — sie werden als `main.py` hochgeladen und überschreiben ein allfällig vorhandenes `main.py` (z. B. der SmartBox-Firmware).

## Struktur

Ein Ordner pro Rolle, jedes Script self-contained (keine gemeinsame Hilfsdatei) — pro Board wird genau **eine Datei** hochgeladen und startet dank `main.py` automatisch beim Boot:

```
espnow-spike/
├── sender/main.py     # sendet alle 5 s einen LED-Befehl
├── receiver/main.py   # LED 1 s an bei Befehl an die eigene MAC
└── relay/main.py      # leitet Frames weiter (Hop)
```

Wichtig: Das 21-Byte-Frame-Format ist in allen drei Scripten dupliziert — Änderungen immer in allen dreien nachziehen.

## Vorbereitung

XIAO ESP32-S3 mit dem MicroPython-Kernel aus `smart-box/setup/` flashen (falls noch nicht geschehen). Dann pro Board die passende Rolle hochladen:

```bash
mpremote connect <port> cp receiver/main.py :main.py   # bzw. sender/ oder relay/
```

Nach dem Upload Board neu starten (`mpremote connect <port> reset`) — es läuft dann autonom, auch an einer Powerbank. Für Konsolen-Ausgaben: `mpremote connect <port>` (REPL anhängen) oder das Script einmalig mit `mpremote run sender/main.py` am Kabel testen.

Jedes Script gibt beim Start die eigene MAC aus — diese Werte in die `# --- KONFIGURATION ---`-Blöcke der anderen Scripte eintragen (vor dem Upload). Alle Boards müssen auf demselben Kanal stehen (`CHANNEL = 1`).

Sender und Relay sind **Fan-out-fähig**: statt einem einzelnen `NEXT_HOP` konfiguriert man eine Routing-Tabelle (`DESTINATIONS` beim Sender, `ROUTES` beim Relay), die pro finalem Ziel (`dest_mac`) den nächsten Funk-Hop angibt. So sendet ein Sender an mehrere Empfänger (direkt oder gemischt über Relays), und ein Relay bedient mehrere Boxen hinter sich (ADR-002: „ein Relay am Bunkereingang, mehrere Maschinen dahinter"). Ein Empfänger hinter dem Sender/Relay ist **direkt** erreichbar, wenn `next_hop_mac == dest_mac`.

## Szenario 1: Direkt (2+ Boards, 1..n Empfänger)

```
Sender ──ESP-NOW──▶ Empfänger 1
       └─ESP-NOW──▶ Empfänger 2
       └─ESP-NOW──▶ ...
```

1. Jeden Empfänger hochladen und starten, MAC notieren.
2. In `sender/main.py`: für jeden Empfänger einen Eintrag in `DESTINATIONS` mit `dest_mac == next_hop_mac` = Empfänger-MAC.
3. Sender hochladen und starten → jede LED blinkt reihum alle 5 s für 1 s; Konsole zeigt pro Ziel `MAC-ACK: True`.

## Szenario 2: Hop (3+ Boards — stellt den Platz nach, mit Fan-out)

```
Sender ──▶ Relay ──▶ Empfänger 1
                └──▶ Empfänger 2
                └──▶ ...
```

1. Jeden Empfänger hochladen und starten, MAC notieren.
2. In `relay/main.py`: für jeden Empfänger einen Eintrag in `ROUTES` mit `dest_mac == next_hop_mac` = Empfänger-MAC. Hochladen, starten, Relay-MAC notieren.
3. In `sender/main.py`: für jeden über das Relay erreichbaren Empfänger einen Eintrag in `DESTINATIONS` mit `next_hop_mac` = **Relay**-MAC, `dest_mac` = jeweilige **Empfänger**-MAC. Direkt erreichbare Empfänger können weiterhin parallel als eigene Einträge mit `next_hop_mac == dest_mac` konfiguriert werden. Hochladen, starten.
4. Konsole des Relays zeigt die Weiterleitung pro Ziel (`ttl 3->2`), der Empfänger meldet `(via Relay?)`, wenn der Frame nicht direkt von der Quelle kam. Ein Frame an ein nicht in `ROUTES` gelistetes Ziel wird vom Relay verworfen und geloggt.

Um den Hop ehrlich zu testen, Sender und Empfänger ausser Reichweite bringen (Distanz, Stahlbeton) — sonst kann der Empfänger Frames auch direkt hören (er ignoriert zwar nichts Falsches, aber der Beweis ist schwächer).

## Long-Range-Variante

Alle drei Scripte haben im Konfigurationsblock einen Schalter `LONG_RANGE`. Auf `True` gesetzt, funkt das Board im proprietären **802.11-LR-Modus** von Espressif — laut Espressif bis ~1 km Sichtlinie, dafür sinkt der Durchsatz auf 0,25–0,5 Mbit/s (für unsere 21-Byte-Frames irrelevant).

Regeln:

- **Alle Boards gleich einstellen.** Ein LR-Board und ein Normal-Board hören sich gegenseitig nicht — das ist ein anderes PHY, kein Kompatibilitätsmodus.
- LR funktioniert nur ESP32↔ESP32 (proprietär), was für unser System ohnehin gilt.
- Spannender Vergleichstest: Szenario 1 einmal mit `LONG_RANGE = False` und einmal mit `True` an derselben Grenzposition — zeigt direkt, ob LR ein Relay auf dem Platz überflüssig machen könnte (der „billige Fix" vor Relay-Komplexität aus ADR-002).

Falls `LR-Modus nicht verfuegbar` erscheint, kennt der geflashte MicroPython-Build `network.MODE_LR` nicht — dann auf dem Standard-PHY bleiben.

## Was dabei nebenbei zu beobachten lohnt

Reboot eines Boards (Peers sind im RAM — verbindet es sich wieder?), grobe Reichweite im Gebäude, Verhalten von `MAC-ACK` an der Reichweitengrenze. Formale Messungen folgen erst in Phase 3.
