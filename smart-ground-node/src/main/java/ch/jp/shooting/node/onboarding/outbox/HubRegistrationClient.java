package ch.jp.shooting.node.onboarding.outbox;

/**
 * Push-Seam für die Geräte-Registrierung zum Hub. Das echte Hub-Registrierungs-Endpoint
 * (idempotente Annahme der node-vergebenen Box-UUID) gehört dem Sync-Fundament (#2);
 * bis dahin meldet die Default-Impl "nicht gesendet", die Zeile bleibt in der Outbox.
 */
public interface HubRegistrationClient {
    boolean register(RegistrationOutboxRecord row);
}
