package com.listaai.infrastructure.adapter.output.email;

import com.listaai.application.port.output.EmailMessage;
import com.listaai.application.port.output.EmailSender;
import com.listaai.application.service.exception.EmailSendException;
import com.listaai.infrastructure.config.EmailProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "app.email.provider", havingValue = "resend", matchIfMissing = true)
public class ResendEmailSender implements EmailSender {

    private final EmailProperties props;
    private final RestClient client;

    public ResendEmailSender(EmailProperties props) {
        this.props = props;
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        this.client = RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory(httpClient))
                .baseUrl(props.resend().baseUrl())
                .build();
    }

    @Override
    public void send(EmailMessage message) {
        Map<String, Object> body = Map.of(
                "from", props.fromAddress(),
                "to", List.of(message.to()),
                "subject", message.subject(),
                "html", message.htmlBody(),
                "text", message.textBody()
        );
        try {
            client.post()
                    .uri("/emails")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + props.resend().apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            boolean retryable = status == 429 || status >= 500;
            throw new EmailSendException(
                    "Resend responded " + status + ": " + e.getResponseBodyAsString(),
                    retryable, e);
        } catch (ResourceAccessException e) {
            throw new EmailSendException("Resend unreachable: " + e.getMessage(), true, e);
        }
    }
}
