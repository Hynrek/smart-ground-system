package ch.jp.shooting.config;

import ch.jp.shooting.exception.MqttDynsecException;
import ch.jp.shooting.model.SmartBox;
import ch.jp.shooting.repository.SmartBoxRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SmartBoxCredentialServiceTest {

    @Mock MqttDynsecClient dynsecClient;
    @Mock SmartBoxRepository smartBoxRepository;

    private SmartBoxCredentialService service() {
        return new SmartBoxCredentialService(dynsecClient, smartBoxRepository);
    }

    private SmartBox box(String mac) {
        SmartBox b = new SmartBox();
        b.setMacAddress(mac);
        return b;
    }

    @Test
    void provisionCreatesBrokerLoginSetsUsernameAndReturnsPassword() {
        when(smartBoxRepository.save(any(SmartBox.class))).thenAnswer(i -> i.getArgument(0));
        SmartBox b = box("AA:BB:CC:DD:EE:FF");

        String password = service().provisionCredentials(b);

        assertThat(password).isNotBlank();
        // Username == MAC am Broker angelegt, mit genau diesem Passwort.
        verify(dynsecClient).createSmartboxClient(eq("AA:BB:CC:DD:EE:FF"), eq(password));
        assertThat(b.getMqttUsername()).isEqualTo("AA:BB:CC:DD:EE:FF");
        verify(smartBoxRepository).save(b);
    }

    @Test
    void provisionGeneratesDistinctHighEntropyPasswords() {
        when(smartBoxRepository.save(any(SmartBox.class))).thenAnswer(i -> i.getArgument(0));
        String p1 = service().provisionCredentials(box("AA:BB:CC:DD:EE:01"));
        String p2 = service().provisionCredentials(box("AA:BB:CC:DD:EE:02"));
        assertThat(p1).isNotEqualTo(p2);
        assertThat(p1.length()).isGreaterThanOrEqualTo(24);
    }

    @Test
    void provisionRejectsColonlessMacWithoutTouchingBroker() {
        SmartBox b = box("aabbccddeeff");
        assertThatThrownBy(() -> service().provisionCredentials(b))
            .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(dynsecClient);
        verify(smartBoxRepository, never()).save(any());
    }

    @Test
    void provisionRejectsWildcardInjectionAttempt() {
        // Ein Username mit MQTT-Wildcards würde die smartboxes/%u/#-Isolation aushebeln.
        assertThatThrownBy(() -> service().provisionCredentials(box("AA:BB:CC:DD:EE:+#")))
            .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(dynsecClient);
    }

    @Test
    void provisionDoesNotSaveUsernameWhenBrokerCreateFails() {
        SmartBox b = box("AA:BB:CC:DD:EE:FF");
        doThrow(new MqttDynsecException("Broker weg")).when(dynsecClient).createSmartboxClient(any(), any());

        assertThatThrownBy(() -> service().provisionCredentials(b))
            .isInstanceOf(MqttDynsecException.class);
        // Box bleibt im Zustand "nicht provisioniert" -> Retry bei nächster Discovery.
        assertThat(b.getMqttUsername()).isNull();
        verify(smartBoxRepository, never()).save(any());
    }

    @Test
    void revokeDeletesBrokerLoginAndClearsUsername() {
        when(smartBoxRepository.save(any(SmartBox.class))).thenAnswer(i -> i.getArgument(0));
        SmartBox b = box("AA:BB:CC:DD:EE:FF");
        b.setMqttUsername("AA:BB:CC:DD:EE:FF");

        service().revokeCredentials(b);

        verify(dynsecClient).deleteSmartboxClient("AA:BB:CC:DD:EE:FF");
        assertThat(b.getMqttUsername()).isNull();
        verify(smartBoxRepository).save(b);
    }

    @Test
    void revokeIsNoOpWhenBoxHasNoBrokerLogin() {
        SmartBox b = box("AA:BB:CC:DD:EE:FF"); // mqttUsername == null
        service().revokeCredentials(b);
        verifyNoInteractions(dynsecClient);
        verify(smartBoxRepository, never()).save(any());
    }

    @Test
    void revokeRethrowsAndKeepsUsernameWhenBrokerDeleteFails() {
        SmartBox b = box("AA:BB:CC:DD:EE:FF");
        b.setMqttUsername("AA:BB:CC:DD:EE:FF");
        doThrow(new MqttDynsecException("Broker weg")).when(dynsecClient).deleteSmartboxClient(any());

        assertThatThrownBy(() -> service().revokeCredentials(b))
            .isInstanceOf(MqttDynsecException.class);
        // Kein stiller Fallback: Username bleibt gesetzt, damit DB/Broker nicht heimlich divergieren.
        assertThat(b.getMqttUsername()).isEqualTo("AA:BB:CC:DD:EE:FF");
        verify(smartBoxRepository, never()).save(any());
    }
}
