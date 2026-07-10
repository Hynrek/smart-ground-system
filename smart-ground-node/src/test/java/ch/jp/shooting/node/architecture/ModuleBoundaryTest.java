package ch.jp.shooting.node.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.file.Path;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Node kommuniziert mit dem Hub ausschliesslich über contracts + HubClient — nie über
 * Hub-Interna (Persistenz, Services, Controller). Dieser Test parst pom.xml direkt (kein
 * ArchUnit — siehe Plan-Notiz zu Task 8): sobald jemand smart-ground-hub als Maven-Dependency
 * hinzufügt, bricht dieser Test den Build.
 */
class ModuleBoundaryTest {

    private static final String FORBIDDEN_GROUP_ID = "ch.jp.shooting";
    private static final String FORBIDDEN_ARTIFACT_ID = "smart-ground-hub";

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

            boolean isForbiddenHubDependency = FORBIDDEN_GROUP_ID.equals(groupId)
                    && FORBIDDEN_ARTIFACT_ID.equals(artifactId);

            assertThat(isForbiddenHubDependency)
                    .as("Node darf ausschliesslich über contracts + HubClient mit dem Hub sprechen — "
                            + "kein Repository-Durchgriff, keine geteilte Persistenz "
                            + "(Hub/Node-Architektur-Spec). Gefundene verbotene Dependency: %s:%s",
                            groupId, artifactId)
                    .isFalse();
        }
    }

    private static String textOf(Element parent, String tagName) {
        NodeList matches = parent.getElementsByTagName(tagName);
        return matches.getLength() == 0 ? null : matches.item(0).getTextContent();
    }
}
