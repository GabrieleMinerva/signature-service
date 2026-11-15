package lab.signature.document;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import lab.signature.messaging.SignaturePublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DocumentController.class, properties = {
        "spring.security.user.name=demo", "spring.security.user.password=demo"
})
@Import(ApiSecurity.class)
class DocumentApiTest {
    private static final String BASE = "/api/documents";
    private static final String BASIC = "Basic " + Base64.getEncoder().encodeToString("demo:demo".getBytes(UTF_8));

    @Autowired MockMvc http;
    @MockitoBean DocumentService documents;
    @MockitoBean SignaturePublisher publisher;

    @ParameterizedTest(name = "API-01 [{index}]")
    @NullSource
    @ValueSource(strings = "wrong-password")
    void requiresAuthentication(String password) throws Exception {
        var request = get(BASE);
        if (password != null) {
            String credentials = "demo:" + password;
            request.header("Authorization", "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(UTF_8)));
        }
        http.perform(request).andExpect(status().isUnauthorized());
    }

    @Test
    void createsAndReadsDocuments() throws Exception {
        UUID id = UUID.randomUUID();
        var draft = new DocumentView(id, "Agreement", DocumentStatus.DRAFT, Instant.now());
        when(documents.create("Agreement")).thenReturn(draft);
        when(documents.get(id)).thenReturn(draft);
        when(documents.list()).thenReturn(List.of(draft));

        http.perform(post(BASE).header("Authorization", BASIC).contentType("application/json")
                        .content("{\"title\":\"Agreement\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", BASE + "/" + id))
                .andExpect(jsonPath("$.status").value("DRAFT"));
        http.perform(get(BASE + "/" + id).header("Authorization", BASIC))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Agreement"));
        http.perform(get(BASE).header("Authorization", BASIC))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id.toString()));
    }

    @Test
    void rejectsBlankTitle() throws Exception {
        http.perform(post(BASE).header("Authorization", BASIC).contentType("application/json")
                        .content("{\"title\":\" \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reportsAcceptedAndConflictForSignatureRequests() throws Exception {
        UUID id = UUID.randomUUID();
        http.perform(post(BASE + "/" + id + "/sign").header("Authorization", BASIC))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", BASE + "/" + id));
        verify(documents).requestSignature(id);

        doThrow(new ResponseStatusException(CONFLICT, "Document is no longer a draft"))
                .when(documents).requestSignature(id);
        http.perform(post(BASE + "/" + id + "/sign").header("Authorization", BASIC))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }
}
