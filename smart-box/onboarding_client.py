"""Kopplungs-Frontseite einer unprovisionierten Box: sendet periodisch HELLO (mit
Backoff), wartet auf ein passendes ONBOARD_OFFER (Echo-Nonce = Replay-Schutz),
tritt dem im Angebot genannten AP bei und provisioniert sich über box-api mit dem
einmaligen Token. Siehe docs/superpowers/specs/2026-07-11-smartbox-coupling-design.md.

Radio-Sende-/Empfangs-Seam: es existiert noch KEINE echte ESP-NOW-Anbindung in
diesem Repo (Phase 2a/2b, siehe docs/superpowers/plans/2026-07-10-hub-node-roadmap.md
– "Orthogonal, unberührt"). radio_send/radio_poll sind austauschbare Modul-Level-
Funktionen nach dem Vorbild von ota.py's http_stream-Seam; die Default-Implementierung
protokolliert nur, spiegelbildlich zu smart-ground-node's LoggingRadioSender.

Zert-Fingerprint aus dem Angebot wird geparst, aber NICHT separat gepinnt: ussl/
urequests bietet in MicroPython keinen Zugriff auf das je Verbindung präsentierte
Zertifikat, um es gegen einen mitgelieferten Fingerprint zu prüfen. Die
Provisionierungs-Anfrage läuft stattdessen über das bestehende node_ca.crt-CA-
Pinning (box_api_client), wie jeder andere box-api-Aufruf auch.

main.py ruft dieses Modul in diesem Plan bewusst NICHT auf — ohne echten Radio-
Empfang liefe die HELLO-Schleife ohnehin nie in ein Angebot; die main.py-Integration
folgt, sobald Phase 2a/2b eine echte Sende-/Empfangsseam bereitstellt.
"""
import os
import network

import box_provisioning
import onboarding_codec
from frame_envelope import TYPE_ONBOARD_OFFER, unpack_header
from networkutils import connect_wifi

BROADCAST_MAC = b'\xff\xff\xff\xff\xff\xff'

# --- KONFIGURATION ---
HELLO_INITIAL_INTERVAL_MS = 2000
HELLO_MAX_INTERVAL_MS = 30000
HELLO_BACKOFF_FACTOR = 2
AP_JOIN_TIMEOUT_S = 15


def _log_only_radio_send(dest_mac, frame):
    print("Radio-Send (Platzhalter, keine echte Uebertragung):", len(frame), "Byte an",
          ''.join('{:02x}'.format(b) for b in dest_mac))


def _log_only_radio_poll():
    return None  # kein echter Empfangspfad -- Phase 2a/2b


radio_send = _log_only_radio_send
radio_poll = _log_only_radio_poll


class OnboardingResult:
    """Ergebnis einer verarbeiteten Kopplungs-Anfrage. status: 'ap_join_failed',
    'provisioning_failed' oder 'provisioned'."""

    def __init__(self, status, k_box_base64=None, error=None):
        self.status = status
        self.k_box_base64 = k_box_base64
        self.error = error


class OnboardingClient:
    """Zustandsmaschine der unprovisionierten Box. tick() einmal pro Hauptschleifen-
    Durchlauf aufrufen. Bedingungsloser Rückweg: nach jedem verarbeiteten Angebot
    (Erfolg oder Fehlschlag) verlässt die Box den AP und setzt den HELLO-Backoff
    zurück, statt in einem Fehlerzustand hängen zu bleiben."""

    def __init__(self, mac_hex, box_type, app_version, capabilities_json):
        self.mac_hex = mac_hex
        self.box_type = box_type
        self.app_version = app_version
        self.capabilities_json = capabilities_json
        self._mac_bytes = bytes.fromhex(mac_hex)
        self._box_nonce = None
        self._next_hello_at_ms = 0
        self._hello_interval_ms = HELLO_INITIAL_INTERVAL_MS
        self._frame_id = 0

    def tick(self, now_ms):
        if now_ms >= self._next_hello_at_ms:
            self._send_hello(now_ms)

        frame = radio_poll()
        if frame is None:
            return None

        dest_mac, src_mac, frame_id, ttl, type_ = unpack_header(frame)
        if type_ != TYPE_ONBOARD_OFFER or dest_mac != self._mac_bytes:
            return None
        if self._box_nonce is None or onboarding_codec.echo_nonce_of(frame) != self._box_nonce:
            return None

        return self._handle_offer(frame)

    def _send_hello(self, now_ms):
        self._box_nonce = os.urandom(8)
        self._frame_id = (self._frame_id + 1) & 0xFFFF
        frame = onboarding_codec.build_hello(BROADCAST_MAC, self._mac_bytes, self._frame_id, 1, self._box_nonce)
        radio_send(BROADCAST_MAC, frame)
        self._next_hello_at_ms = now_ms + self._hello_interval_ms
        self._hello_interval_ms = min(self._hello_interval_ms * HELLO_BACKOFF_FACTOR, HELLO_MAX_INTERVAL_MS)

    def _handle_offer(self, frame):
        ssid = onboarding_codec.ssid_of(frame).decode()
        psk = onboarding_codec.psk_of(frame).decode()
        url = onboarding_codec.url_of(frame).decode()
        token_hex = onboarding_codec.token_of(frame).hex()
        _fingerprint = onboarding_codec.fingerprint_of(frame)  # siehe Modul-Docstring: nicht separat gepinnt

        joined = connect_wifi(ssid, psk, timeout=AP_JOIN_TIMEOUT_S)
        if not joined:
            result = OnboardingResult(status="ap_join_failed")
        else:
            try:
                response = box_provisioning.discover_and_provision(
                    self.mac_hex, url, token_hex,
                    app_version=self.app_version, box_type=self.box_type,
                    capabilities_json=self.capabilities_json)
                result = OnboardingResult(status="provisioned", k_box_base64=response.get("kBoxBase64"))
            except Exception as e:
                result = OnboardingResult(status="provisioning_failed", error=str(e))

        self._deactivate_sta()
        self._reset_hello_backoff()
        return result

    def _reset_hello_backoff(self):
        self._hello_interval_ms = HELLO_INITIAL_INTERVAL_MS
        self._next_hello_at_ms = 0

    def _deactivate_sta(self):
        try:
            network.WLAN(network.STA_IF).active(False)
        except Exception as e:
            print("AP-Verlassen fehlgeschlagen (ignoriert):", e)
