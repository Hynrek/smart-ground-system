package ch.jp.shooting.node.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.file.Path;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Node kommuniziert mit dem Hub ausschliesslich über contracts + HubClient — nie über
 * Hub-Interna (Persistenz, Services, Controller). Dieser Test parst pom.xml direkt (kein
 * ArchUnit — siehe Plan-Notiz zu Task 8): sobald jemand irgendein First-Party-Artefakt
 * (z.B. den Hub, egal unter welchem Namen) als Maven-Dependency hinzufügt statt der beiden
 * erlaubten geteilten Module, bricht dieser Test den Build. Eine Allowlist statt einer
 * Denylist auf einem einzelnen Artefaktnamen, damit ein erneutes Umbenennen des Hubs
 * (wie in Task 5 geschehen) diesen Schutz nicht lautlos aushebelt.
 */
class ModuleBoundaryTest {

    private static final Set<String> FIRST_PARTY_GROUP_IDS = Set.of("ch.jp.shooting", "ch.jp.smartground");
    private static final Set<String> PERMITTED_SHARED_ARTIFACT_IDS = Set.of("contracts", "domain");

    @Test
    void nodePomMustNotDependOnHub() throws Exception {
        Path pomPath = Path.of("pom.xml").toAbsolutePath();
        File pomFile = pomPath.toFile();
        assertThat(pomFile).exists();

        Document pom = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(pomFile);

        NodeList dependencyNodes = pom.getElementsByTagName("dependency");
        for (int i = 0; i < dependencyNodes.getLength(); i++) {
            Element dependency = (Element) dependencyNodes.item(i);
            String groupId = textOf(dependency, "groupId");
            String artifactId = textOf(dependency, "artifactId");

            boolean isFirstPartyDependency = FIRST_PARTY_GROUP_IDS.contains(groupId);
            boolean isPermittedSharedModule = PERMITTED_SHARED_ARTIFACT_IDS.contains(artifactId);
            boolean isForbiddenFirstPartyDependency = isFirstPartyDependency && !isPermittedSharedModule;

            assertThat(isForbiddenFirstPartyDependency)
                    .as("Node darf ausschliesslich über contracts + HubClient mit dem Hub sprechen — "
                            + "kein Repository-Durchgriff, keine geteilte Persistenz, kein First-Party-Artefakt "
                            + "ausser den erlaubten geteilten Modulen %s (Hub/Node-Architektur-Spec). "
                            + "Gefundene verbotene Dependency: %s:%s",
                            PERMITTED_SHARED_ARTIFACT_IDS, groupId, artifactId)
                    .isFalse();
        }
    }

    private static String textOf(Element parent, String tagName) {
        NodeList matches = parent.getElementsByTagName(tagName);
        return matches.getLength() == 0 ? null : matches.item(0).getTextContent();
    }
}
