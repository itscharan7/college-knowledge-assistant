package com.collegeassistant.document;

import com.collegeassistant.document.DocumentDtos.DocumentDto;
import com.collegeassistant.document.DocumentDtos.StatusDto;
import com.collegeassistant.document.DocumentDtos.UpdateDocumentRequest;
import com.collegeassistant.security.AppUserDetails;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/documents")
@PreAuthorize("hasRole('ADMIN')")
public class AdminDocumentController {

    private final DocumentService service;

    public AdminDocumentController(DocumentService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentDto> upload(@RequestParam("file") MultipartFile file,
                                              @RequestParam(required = false) String title,
                                              @RequestParam String category,
                                              @RequestParam Audience audience,
                                              @RequestParam(required = false) String description,
                                              @AuthenticationPrincipal AppUserDetails me) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(service.upload(file, title, category, audience, description, me.getId()));
    }

    @GetMapping
    public List<DocumentDto> list(@RequestParam(required = false) String q,
                                  @RequestParam(required = false) DocStatus status) {
        return service.list(q, status);
    }

    @GetMapping("/{id}")
    public DocumentDto details(@PathVariable UUID id) {
        return service.details(id);
    }

    @GetMapping("/{id}/status")
    public StatusDto status(@PathVariable UUID id) {
        return service.status(id);
    }

    @PutMapping("/{id}")
    public DocumentDto update(@PathVariable UUID id, @Valid @RequestBody UpdateDocumentRequest req) {
        return service.updateMetadata(id, req);
    }

    @PostMapping(value = "/{id}/replace", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentDto> replace(@PathVariable UUID id, @RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.replace(id, file));
    }

    @PostMapping("/{id}/reindex")
    public ResponseEntity<DocumentDto> reindex(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(service.reindex(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
