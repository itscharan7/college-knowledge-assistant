package com.collegeassistant.document;

import com.collegeassistant.document.DocumentDtos.DocumentSummary;
import com.collegeassistant.security.AppUserDetails;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Document access for students/faculty/admins. Every read is checked against the caller's role. */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService service;

    public DocumentController(DocumentService service) {
        this.service = service;
    }

    @GetMapping
    public List<DocumentSummary> list(@AuthenticationPrincipal AppUserDetails me) {
        return service.visibleTo(me.getRole());
    }

    /** Streams the PDF inline so the browser viewer can open it at "#page=N". Unauthorised => 404. */
    @GetMapping("/{id}/file")
    public ResponseEntity<Resource> file(@PathVariable UUID id, @AuthenticationPrincipal AppUserDetails me) {
        CollegeDocument d = service.getAccessible(id, me.getRole());
        Resource body = new InputStreamResource(service.openFile(d));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(d.getOriginalFilename()).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(body);
    }
}
