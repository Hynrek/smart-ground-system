package ch.jp.shooting.service;

import ch.jp.shooting.model.BracketMatch;
import org.springframework.stereotype.Service;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service zur Generierung von Bracket-Strukturen aus gesäten Spielerlisten.
 * Erstellt Match-Slots für alle Runden mit Bye-Handling.
 */
@Service
@NullMarked
public class BracketGenerationService {

    /**
     * Repräsentiert einen Knoten im Bracket-Baum.
     */
    public static class BracketNode {
        public UUID matchId;
        public int matchNumber;
        public int roundNumber;
        public @Nullable UUID contestant1;
        public @Nullable UUID contestant2;
        public BracketNode parent;
        public BracketNode child1;  // Aufsteiger 1 im nächsten Match
        public BracketNode child2;  // Aufsteiger 2 im nächsten Match
        public boolean isBye;

        public BracketNode(int matchNumber, int roundNumber) {
            this.matchNumber = matchNumber;
            this.roundNumber = roundNumber;
        }
    }

    /**
     * Erstellt ein Single-Elimination-Bracket als Baum-Struktur.
     * 
     * @param seededPlayerIds Spieler in Seeding-Reihenfolge (1 = beste)
     * @return Root node (Finale) des Bracket-Baums
     */
    public BracketNode buildSingleEliminationBracket(List<UUID> seededPlayerIds) {
        int playerCount = seededPlayerIds.size();
        int nextPowerOf2 = (int) Math.pow(2, Math.ceil(Math.log(playerCount) / Math.log(2)));
        int byeCount = nextPowerOf2 - playerCount;

        // Erstelle Erste Runde mit Byes
        List<BracketNode> round1Nodes = new ArrayList<>();
        int matchNum = 1;

        for (int i = 0; i < nextPowerOf2; i += 2) {
            UUID player1 = i < seededPlayerIds.size() ? seededPlayerIds.get(i) : null;
            UUID player2 = (i + 1) < seededPlayerIds.size() ? seededPlayerIds.get(i + 1) : null;

            BracketNode node = new BracketNode(matchNum++, 1);
            node.contestant1 = player1;
            node.contestant2 = player2;
            node.isBye = (player1 == null && player2 != null) || (player1 != null && player2 == null);

            round1Nodes.add(node);
        }

        // Rekursiv höhere Runden bauen
        List<BracketNode> currentRound = round1Nodes;
        int roundNum = 2;

        while (currentRound.size() > 1) {
            List<BracketNode> nextRound = new ArrayList<>();
            matchNum = 1;

            for (int i = 0; i < currentRound.size(); i += 2) {
                BracketNode node = new BracketNode(matchNum++, roundNum);

                if (i < currentRound.size()) {
                    node.child1 = currentRound.get(i);
                    currentRound.get(i).parent = node;
                }
                if (i + 1 < currentRound.size()) {
                    node.child2 = currentRound.get(i + 1);
                    currentRound.get(i + 1).parent = node;
                }

                nextRound.add(node);
            }

            currentRound = nextRound;
            roundNum++;
        }

        // Rückgabe des Root (Finale)
        return currentRound.isEmpty() ? round1Nodes.get(0) : currentRound.get(0);
    }

    /**
     * Generiert alle Matches aus einem Bracket-Baum für die Persistenz.
     * Durchsucht den Baum und erstellt BracketMatch-Objekte.
     */
    public List<BracketMatch> flattenBracketToMatches(UUID sessionId, BracketNode root) {
        List<BracketMatch> matches = new ArrayList<>();
        Set<BracketNode> visited = new HashSet<>();

        // DFS durch den Baum
        traverseAndCollect(root, sessionId, matches, visited);

        return matches;
    }

    private void traverseAndCollect(BracketNode node, UUID sessionId, List<BracketMatch> matches,
                                     Set<BracketNode> visited) {
        if (node == null || visited.contains(node)) {
            return;
        }

        visited.add(node);

        // Erstelle BracketMatch für diesen Knoten (wenn nicht nur ein Link)
        if (node.roundNumber > 0) {
            BracketMatch match = new BracketMatch(sessionId, node.matchNumber, node.roundNumber,
                node.contestant1, node.contestant2);
            match.setBye(node.isBye);
            matches.add(match);
        }

        // Rekursiv Kinder besuchen
        if (node.child1 != null) {
            traverseAndCollect(node.child1, sessionId, matches, visited);
        }
        if (node.child2 != null) {
            traverseAndCollect(node.child2, sessionId, matches, visited);
        }
    }

    /**
     * Findet den nächsten Match-Knoten im Bracket basierend auf aktuellen Match-Ergebnissen.
     * TODO: Implementierung folgt nach Integration mit BracketMatch-Repository
     */
    public @Nullable BracketNode findNextUnplayedMatch(BracketNode root) {
        // BFS durch den Baum, erste Runde mit beiden Spielern, aber kein Gewinner
        Queue<BracketNode> queue = new LinkedList<>();
        queue.add(root);

        while (!queue.isEmpty()) {
            BracketNode node = queue.poll();

            // Prüfe ob dieses Match gespielt werden kann
            if (node.contestant1 != null && node.contestant2 != null && !node.isBye) {
                // Check ob bereits gespielt (würde vom Repository kommen)
                // Für jetzt: return first valid
                return node;
            }

            if (node.child1 != null) queue.add(node.child1);
            if (node.child2 != null) queue.add(node.child2);
        }

        return null;
    }
}
