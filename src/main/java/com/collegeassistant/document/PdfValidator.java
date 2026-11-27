package com.collegeassistant.document;

import com.collegeassistant.common.ApiException;
import com.collegeassistant.config.AppProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/** Upload validation: extension, MIME type, size, magic bytes, and that PDFBox can actually open it. */
@Component
public class PdfValidator {

    private final AppProperties props;

    public PdfValidator(AppProperties props) {
        this.props = props;
    }

    /** Returns the page count of a valid PDF, or throws a 400 ApiException with a friendly message. */
    public int validate(MultipartFile file, byte[] bytes) {
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase().endsWith(".pdf")) {
            throw ApiException.badRequest("Only .pdf files are accepted.");
        }
        String type = file.getContentType();
        if (type == null || !type.equalsIgnoreCase("application/pdf")) {
            throw ApiException.badRequest("The file must have the content type application/pdf.");
        }
        if (bytes.length == 0) {
            throw ApiException.badRequest("The file is empty.");
        }
        if (bytes.length < 5 || !new String(bytes, 0, 5, StandardCharsets.ISO_8859_1).equals("%PDF-")) {
            throw ApiException.badRequest("This does not look like a real PDF file.");
        }
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            int pages = doc.getNumberOfPages();
            if (pages == 0) {
                throw ApiException.badRequest("The PDF has no pages.");
            }
            if (pages > props.getIngestion().getMaxPages()) {
                throw ApiException.badRequest("The PDF has too many pages (limit "
                        + props.getIngestion().getMaxPages() + ").");
            }
            return pages;
        } catch (InvalidPasswordException e) {
            throw ApiException.badRequest("Password-protected PDFs are not supported.");
        } catch (IOException e) {
            throw ApiException.badRequest("The PDF is corrupted or cannot be read.");
        }
    }
}
