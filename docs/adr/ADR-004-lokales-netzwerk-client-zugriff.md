# ADR-004: Lokales Netzwerk und Client-Zugriff auf den SmartNode

**Status:** Proposed
**Datum:** 2026-07-10
**Entscheider:** Jonas Studer
**Bezug:** ADR-001 (SmartNode als Edge-Cache mit lokalem Webserver — konkretisiert dessen offenen Punkt „Zugriff für den Schützen offline")

> ⚠️ Teile dieses ADR sind durch die [Hub/Node-Spec](../superpowers/specs/2026-07-10-hub-node-architecture-design.md) überholt (u. a. TLS: selbstsigniertes Zertifikat statt DNS-01). Die Spec ist die Autorität. Zusätzlich siehe **Amendment 2026-07-10** unten zur AP-Hardware.

## Amendment 2026-07-10: AP-Funkzelle von der Node-Hardware entkoppelt

**Präzisiert Entscheidung 1.** Die Client-Funkzelle wird **nicht** vom WLAN-Radio des Node aufgespannt, sondern von **dedizierter, am Node-LAN gebridgeter AP-Hardware**. `hostapd` verlässt damit den Node.

**Unverändert am Node:** die Rolle als alleinige DHCP-/DNS-/Origin-Autorität. Der Node betreibt weiter `dnsmasq` (DHCP-Vergabe + autoritative Auflösung des festen Hostnamens auf seine eigene IP) und liefert die Vue-App unter `https://node-{id}.…` mit dem selbstsignierten Zertifikat aus.

**Rolle der AP-Hardware:** reiner **Dumb-AP / L2-Bridge** — eigenes DHCP und DNS **abgeschaltet**. Es darf genau einen DHCP-/DNS-Server im Segment geben (den Node); zwei würden sich um die Adressvergabe streiten und die Namensauflösung des `.lan`-Hostnamens brechen.

**Warum:** Die Reichweite des Pi-internen Radios trägt einen offenen Schiessplatz nicht (bestätigt durch die Contra-Spalte von Option A und die Trade-off-Analyse unten, die genau diese Hardware-Option bereits als Remedium nennt). Eine dedizierte AP mit externen/Outdoor-Antennen und PoE entkoppelt die Funkabdeckung von der Compute-Hardware des Node und lässt sich unabhängig platzieren. Für den Feldtest genügt eine kleine AP im Dumb-AP-Modus (z. B. GL.iNet/OpenWrt); Outdoor-AP (z. B. UniFi) nur, falls die Reichweite marginal bleibt.

**Konsequenz für das Node-Image:** `hostapd` entfällt aus dem Node-Image (Action Item 1); dafür kommt die einmalige Konfiguration der externen AP als Dumb-AP hinzu, und der Node muss einen Ethernet-Bridge-Port ins Client-Segment bereitstellen. Die AP-Kanalplanung gegen die ESP-NOW-Radios (ADR-002) bleibt bestehen, verlagert sich aber auf die externe AP.

## Kontext

ADR-001 legt fest, dass der SmartNode bei totem Uplink die gecachten Schiessprogramme über einen lokalen Webserver serviert und Resultate entgegennimmt. Offen ist, **wie Client-Geräte (Smartphones/Tablets der Schützen) den Node erreichen**: über welches Netz, unter welchem Namen, und wie der Zugriff abgesichert wird.

Kräfte:

- Die Anlage muss **ohne Internet-Uplink** voll bedienbar sein — das lokale Netz darf von nichts Externem abhängen.
- Der Client ist eine **Browser-App** (Vue UI). Namensauflösung muss also im Browser des jeweiligen OS funktionieren, nicht nur in einer nativen App.
- **Android hat keinen systemweiten mDNS-Resolver** — `.local`-Namen sind in Android-Browsern unzuverlässig. iOS/macOS/Windows 10+/Linux (avahi) können es.
- Browser erzwingen zunehmend HTTPS (Secure-Context-Features, Mixed-Content); für private Namen/IPs gibt es keine öffentlichen Zertifikate.
- Der Node ist ein Pi-Klasse-Gerät (ADR-001); Uplink wahlweise Ethernet/WLAN/LTE.

## Entscheidung

1. **Der Node stellt die Client-Funkzelle bereit** — Schützen verbinden sich mit einem Node-eigenen WLAN; der Node kontrolliert DHCP und DNS vollständig und ist von der Netzinfrastruktur der Anlage unabhängig. *(Präzisiert durch Amendment 2026-07-10: die Funkzelle liefert dedizierte, gebridgete AP-Hardware im Dumb-AP-Modus; `hostapd` verlässt den Node, DHCP/DNS/Origin bleiben am Node.)*
2. **dnsmasq als DHCP- + DNS-Server.** Der Node vergibt Adressen und beantwortet einen **festen Node-Hostnamen** autoritativ mit seiner eigenen IP — für alle Plattformen gleich, inklusive Android. **Kein mDNS:** wegen der Android-Lücke müsste ohnehin ein zweiter Mechanismus her; dnsmasq deckt alle Fälle allein ab. Der Name liegt bewusst **nicht** unter `.local` (für mDNS reserviert, RFC 6762).
3. **Node-Hostname = echte Subdomain, TLS per DNS-01.** Jeder Node erhält eine öffentliche Subdomain (z. B. `node-{id}.smartground.example`) und ein Let's-Encrypt-Zertifikat via **DNS-01-Challenge über den Uplink**. dnsmasq beantwortet diese Subdomain im lokalen Netz mit der lokalen Node-IP (Split-Horizon). Ergebnis: gültiges HTTPS ohne Zertifikatswarnung, auch offline — das Zertifikat ist 90 Tage gültig und wird bei vorhandenem Uplink erneuert; tagelange Offline-Fenster (ADR-001) übersteht es problemlos.
4. **Uplink physisch getrennt vom Client-WLAN.** Bevorzugt Ethernet oder LTE. WLAN-Uplink und AP auf demselben Pi-Radio (AP+STA) ist fragil (Kanalbindung, Durchsatz) — falls WLAN-Uplink nötig, zweites USB-WLAN-Modul.
5. **Einstieg per QR-Code an der Anlage:** WLAN-Beitritt (WPA2-PSK, `WIFI:`-QR) plus URL. Kein Captive Portal in v1.

## Betrachtete Optionen

### Option A: Node als AP + dnsmasq (gewählt)

| Dimension | Bewertung |
|---|---|
| Komplexität | Niedrig — hostapd + dnsmasq, Standard-Pi-Setup |
| Abhängigkeiten | Keine — funktioniert auf der grünen Wiese |
| Plattform-Abdeckung | Vollständig, inkl. Android-Browser |
| Betrieb | Node ist ein in sich geschlossenes Gerät („anschalten, verbinden, schiessen") |

**Pro:** Ein Mechanismus für alle Clients; deterministisch; keine Annahmen über Vorhandensein/Konfiguration eines Anlagen-Routers.
**Contra:** WLAN-Reichweite des Pi begrenzt (ggf. externe Antenne/AP-Hardware); Clients verlieren beim Beitritt ggf. ihre Internetverbindung (Android „Netzwerk hat kein Internet"-Dialog — Nutzer muss „trotzdem verbinden" wählen, solange der Uplink tot ist).

### Option B: Node als Client im bestehenden Anlagen-Router-Netz

**Pro:** Nutzt vorhandene WLAN-Abdeckung; Clients behalten Internet.
**Contra:** Der Node kontrolliert DNS nicht — Namensauflösung hängt vom Router ab, auf Android bleibt sonst nur die IP (QR-Code). Konfiguration pro Anlage verschieden, nicht reproduzierbar. Nicht Standard, aber als **dokumentierter Alternativ-Modus** möglich (statische IP + QR mit IP-URL), wo eine Anlage bereits gutes WLAN hat.

### Option C: mDNS/DNS-SD (`smartnode.local`)

**Pro:** Zero-Conf, kein DHCP/DNS nötig; DNS-SD ermöglicht Auto-Discovery für künftige native Apps.
**Contra:** In Android-Browsern unzuverlässig → bräuchte ohnehin Fallback; im AP-Modus bringt es gegenüber dnsmasq nichts. **Verworfen für v1** — avahi ist später ein Einzeiler, falls eine native App DNS-SD-Discovery will.

### Option D: TLS-Alternativen zur echten Subdomain

- **Nur HTTP:** einfachst, aber Browser-Warnungen, keine Secure-Context-APIs, Downgrade der App. Verworfen.
- **Self-Signed/eigene CA:** Zertifikatswarnung auf jedem Schützen-Handy bzw. CA-Installation pro Gerät — für Publikumsbetrieb unzumutbar. Verworfen.
- **Echte Subdomain + DNS-01 (gewählt, siehe Entscheidung 3):** einzige Variante ohne Warnung auf fremden Geräten. Preis: Node braucht periodisch Uplink (≤ 90 Tage) und das Backend einen DNS-API-Zugang für die Challenge.

## Trade-off-Analyse

Der Kern-Trade-off ist **eigenes Netz (A) vs. vorhandenes Netz (B)**. A gewinnt, weil das erklärte Ziel — Anlage funktioniert autonom — sonst von der Router-Konfiguration jeder einzelnen Anlage abhinge; A macht den Node zum reproduzierbaren, in sich geschlossenen Produkt. Die Reichweiten-Schwäche des Pi-WLAN ist mit Hardware (externe Antenne, dedizierter AP am Node-Ethernet) lösbar und ein kleineres Problem als nicht-deterministisches DNS.

Bei TLS ist der Trade-off **Offline-Reinheit vs. Browser-Realität**: Die DNS-01-Lösung koppelt den Node schwach an den Uplink (Zertifikats-Erneuerung), aber mit 90 Tagen Puffer — verkraftbar gegenüber tagelangen Ausfällen. Die Alternativen scheitern an der Browser-Realität (Warnungen auf Geräten, die dem Verein nicht gehören).

## Konsequenzen

- **Einfacher:** Onboarding („QR scannen, fertig"); identisches Verhalten online wie offline — die App spricht immer denselben Hostnamen an; Android-Problem vollständig umgangen.
- **Schwerer:** Node-Image muss hostapd/dnsmasq/Zertifikats-Renewal enthalten und überwachen (Zertifikatsablauf als Monitoring-Punkt); DNS-API-Credentials fürs Backend; Kanalplanung AP-WLAN vs. ESP-NOW-Radios (ADR-002: getrennte Kanäle, Antennenabstand).
- **Neu erforderlich:** Subdomain-Schema + DNS-Zone; Entscheidung, ob die App offline vom Node oder weiterhin vom Hauptserver geladen wird (der lokale Webserver muss die Vue-App selbst ausliefern, sonst lädt sie offline gar nicht erst) → betrifft Deployment der UI auf den Node.
- **Später zu prüfen:** avahi/DNS-SD für native App-Discovery; Captive Portal fürs Onboarding; Alternativ-Modus B formal dokumentieren; WPA2-PSK-Rotation.

## Action Items

1. [ ] Node-Image: dnsmasq (DHCP + Split-Horizon-DNS für den Node-Hostnamen); Ethernet-Bridge-Port ins Client-Segment. **Kein hostapd am Node** (Amendment 2026-07-10) — stattdessen: externe AP-Hardware als Dumb-AP (WPA2-PSK, DHCP/DNS aus) einmalig konfigurieren und ans Node-LAN bridgen
2. [ ] Subdomain-Schema festlegen (`node-{id}.…`) + DNS-Zone mit API-Zugang für DNS-01
3. [ ] Zertifikats-Renewal auf dem Node (z. B. certbot/lego über Uplink) + Ablauf-Monitoring
4. [ ] Lokale Auslieferung der Vue-App durch den Node-Webserver (Build im Node-Deployment)
5. [ ] QR-Code-Generierung (WLAN-Beitritt + URL) im Backend/Admin-UI pro Node
6. [ ] Testfall: Uplink trennen → Handy (Android + iOS) verbindet per QR, App lädt, Programm abrufbar, Resultat erfasst, kein Zertifikatsfehler
