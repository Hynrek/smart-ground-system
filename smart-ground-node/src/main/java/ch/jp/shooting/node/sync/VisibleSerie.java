package ch.jp.shooting.node.sync;

import java.util.UUID;

/**
 * Read-your-writes-Sicht auf eine Serie (Teilprojekt #3): vereinigt SyncedSerie (bereits
 * vom Hub bestätigt) mit noch nicht bestätigten Outbox-Einträgen. `provenance` sagt der
 * node-api-Fassade (#5, hier noch nicht exponiert) später, wie sie das Dokument kennzeichnen soll.
 */
public record VisibleSerie(
        UUID id,
        String name,
        String ownership,
        UUID rangeId,
        UUID ownerId,
        String stepsJson,
        boolean published,
        String provenance // "synced" | "pending" | "failed"
) {}
