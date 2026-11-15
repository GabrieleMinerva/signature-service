package lab.signature.document;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/documents")
class DocumentController {
    private final DocumentService documents;

    DocumentController(DocumentService documents) {
        this.documents = documents;
    }

    @PostMapping
    ResponseEntity<DocumentView> create(@Valid @RequestBody CreateDocument request) {
        DocumentView document = documents.create(request.title());
        return ResponseEntity.created(URI.create("/api/documents/" + document.id())).body(document);
    }

    @GetMapping("/{id}")
    DocumentView get(@PathVariable UUID id) {
        return documents.get(id);
    }

    @GetMapping
    List<DocumentView> list() {
        return documents.list();
    }

    @PostMapping("/{id}/sign")
    ResponseEntity<Void> sign(@PathVariable UUID id) {
        documents.requestSignature(id);
        return ResponseEntity.accepted().location(URI.create("/api/documents/" + id)).build();
    }

    record CreateDocument(@NotBlank @Size(max = 200) String title) {
    }
}
