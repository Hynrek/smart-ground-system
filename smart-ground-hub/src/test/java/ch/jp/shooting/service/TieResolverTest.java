package ch.jp.shooting.service;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TieResolverTest {

    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();
    private static final UUID C = UUID.randomUUID();

    private TieResolver.PlayerStanding ps(UUID id, String name, int total) {
        return new TieResolver.PlayerStanding(id, name, total, 25);
    }

    @Test
    void noTies_assignsSequentialRanks() {
        var standings = List.of(ps(A, "Anna", 24), ps(B, "Ben", 20), ps(C, "Cara", 18));
        var resolved = new TieResolver().resolve(standings, List.of());

        assertEquals(List.of(1, 2, 3), resolved.stream().map(r -> r.rank()).toList());
        assertFalse(resolved.get(0).tied());
        assertFalse(resolved.get(0).tieResolvedByStechen());
    }

    @Test
    void twoWayTie_noStechen_sharesRank() {
        var standings = List.of(ps(A, "Anna", 24), ps(B, "Ben", 24), ps(C, "Cara", 18));
        var resolved = new TieResolver().resolve(standings, List.of());

        assertEquals(1, rankOf(resolved, A));
        assertEquals(1, rankOf(resolved, B));
        assertEquals(3, rankOf(resolved, C));
        assertTrue(byId(resolved, A).tied());
        assertTrue(byId(resolved, B).tied());
    }

    @Test
    void twoWayTie_stechenBreaksIt_ordersWithinBlock() {
        var standings = List.of(ps(A, "Anna", 24), ps(B, "Ben", 24), ps(C, "Cara", 18));
        var tieGroup = UUID.randomUUID();
        var round = new TieResolver.TiebreakerRound(
                tieGroup, 1, List.of(A, B), Map.of(A, 5, B, 8)); // Ben wins the Stechen
        var resolved = new TieResolver().resolve(standings, List.of(round));

        assertEquals(1, rankOf(resolved, B));
        assertEquals(2, rankOf(resolved, A));
        assertEquals(3, rankOf(resolved, C));
        assertFalse(byId(resolved, B).tied());
        assertTrue(byId(resolved, B).tieResolvedByStechen());
        assertTrue(byId(resolved, A).tieResolvedByStechen());
    }

    @Test
    void threeWayTie_round1PartiallySeparates_round2BreaksRest() {
        var standings = List.of(ps(A, "Anna", 24), ps(B, "Ben", 24), ps(C, "Cara", 24));
        var tieGroup = UUID.randomUUID();
        var r1 = new TieResolver.TiebreakerRound(tieGroup, 1, List.of(A, B, C), Map.of(A, 9, B, 9, C, 3));
        var r2 = new TieResolver.TiebreakerRound(tieGroup, 2, List.of(A, B), Map.of(A, 7, B, 6));
        var resolved = new TieResolver().resolve(standings, List.of(r1, r2));

        assertEquals(1, rankOf(resolved, A));
        assertEquals(2, rankOf(resolved, B));
        assertEquals(3, rankOf(resolved, C));
        assertFalse(byId(resolved, A).tied());
        assertFalse(byId(resolved, B).tied());
    }

    @Test
    void stechenStillTied_playersRemainTied() {
        var standings = List.of(ps(A, "Anna", 24), ps(B, "Ben", 24));
        var tieGroup = UUID.randomUUID();
        var r1 = new TieResolver.TiebreakerRound(tieGroup, 1, List.of(A, B), Map.of(A, 5, B, 5));
        var resolved = new TieResolver().resolve(standings, List.of(r1));

        assertEquals(1, rankOf(resolved, A));
        assertEquals(1, rankOf(resolved, B));
        assertTrue(byId(resolved, A).tied());
        assertTrue(byId(resolved, B).tied());
    }

    private int rankOf(List<TieResolver.ResolvedStanding> rs, UUID id) { return byId(rs, id).rank(); }
    private TieResolver.ResolvedStanding byId(List<TieResolver.ResolvedStanding> rs, UUID id) {
        return rs.stream().filter(r -> r.playerId().equals(id)).findFirst().orElseThrow();
    }
}
