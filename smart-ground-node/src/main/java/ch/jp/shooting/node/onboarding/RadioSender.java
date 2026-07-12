package ch.jp.shooting.node.onboarding;

/**
 * Sende-Seam für Onboarding-Frames über den Funk. In Plan 2 nur Interface + Logging-Impl;
 * die echte Serial/UART-Anbindung folgt in einem späteren Plan (Baustein D liefert den UART-Codec).
 */
public interface RadioSender {
    void send(byte[] destMac, byte[] frame);
}
