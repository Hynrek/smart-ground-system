package ch.jp.shooting.node.hub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ch.jp.smartground.model.LoginResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class HubClientTest {

    @Test
    void loginPostsCredentialsAndReturnsToken() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://hub.local");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HubClient client = new HubClient(builder.build());

        server.expect(requestTo("http://hub.local/api/auth/login"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.username").value("node-service"))
                .andExpect(jsonPath("$.password").value("secret"))
                .andRespond(withSuccess("{\"token\":\"jwt-abc\"}", MediaType.APPLICATION_JSON));

        LoginResponse response = client.login("node-service", "secret");

        assertThat(response.getToken()).isEqualTo("jwt-abc");
        server.verify();
    }
}
