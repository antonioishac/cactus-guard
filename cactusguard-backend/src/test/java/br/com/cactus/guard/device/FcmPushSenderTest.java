package br.com.cactus.guard.device;

import br.com.cactus.guard.integration.push.FcmPushSender;
import br.com.cactus.guard.integration.push.PushMessage;
import br.com.cactus.guard.integration.push.PushResult;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FcmPushSenderTest {

    private MockRestServiceServer server;

    private FcmPushSender sender() {
        RestClient.Builder b = RestClient.builder().baseUrl("https://fcm.test");
        server = MockRestServiceServer.bindTo(b).build();
        return new FcmPushSender("proj-1", b.build(), () -> "access-token");
    }

    @Test
    void sendsOnlyTypeIdAndGenericText() {
        FcmPushSender s = sender();
        UUID id = UUID.randomUUID();
        server.expect(requestTo("https://fcm.test/v1/projects/proj-1/messages:send"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer access-token"))
                .andExpect(jsonPath("$.message.token").value("tok-123"))
                .andExpect(jsonPath("$.message.data.type").value("ALERT"))
                .andExpect(jsonPath("$.message.data.id").value(id.toString()))
                .andExpect(jsonPath("$.message.data.length()").value(2))
                .andExpect(jsonPath("$.message.notification.title").value("Novo alerta crítico para análise"))
                .andRespond(withSuccess("{\"name\":\"projects/proj-1/messages/1\"}", MediaType.APPLICATION_JSON));
        assertEquals(PushResult.SENT, s.send("tok-123", PushMessage.criticalAlert(id)));
        server.verify();
    }

    @Test
    void unregisteredTokenIsReportedInvalid() {
        FcmPushSender s = sender();
        server.expect(requestTo("https://fcm.test/v1/projects/proj-1/messages:send"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"status\":\"NOT_FOUND\",\"details\":[{\"errorCode\":\"UNREGISTERED\"}]}}"));
        assertEquals(PushResult.INVALID_TOKEN, s.send("tok-123", PushMessage.slaDeadline(UUID.randomUUID())));
    }
}
