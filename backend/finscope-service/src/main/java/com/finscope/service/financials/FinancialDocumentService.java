package com.finscope.service.financials;

import com.finscope.common.exception.BusinessException;
import com.finscope.common.exception.ErrorCode;
import com.finscope.common.exception.ResourceNotFoundException;
import com.finscope.dao.financials.FinancialDocumentRepository;
import com.finscope.domain.financials.FinancialDocument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.finscope.common.exception.BizErrorCode;

@Service
public class FinancialDocumentService {
    private static final long MAX_FILE_SIZE = 30L * 1024L * 1024L;
    private static final Pattern PAGE_PATTERN = Pattern.compile("/Type\\s*/Page(?!s)");
    private static final Pattern TEXT_PATTERN = Pattern.compile(
            "\\(((?:\\\\.|[^\\\\)])*)\\)\\s*Tj", Pattern.DOTALL);
    private final FinancialDocumentRepository repository;
    private final Path root;

    @Autowired
    public FinancialDocumentService(
            FinancialDocumentRepository repository,
            @Value("${finscope.data-root:../data}") String dataRoot) {
        this(repository, java.nio.file.Paths.get(dataRoot).resolve("financials/reports"));
    }

    FinancialDocumentService(FinancialDocumentRepository repository, Path root) {
        this.repository = repository;
        this.root = root.toAbsolutePath().normalize();
    }

    public FinancialDocument store(Long instrumentId, Long reportId, String originalFileName,
                                   InputStream input, long declaredSize) throws IOException {
        if (declaredSize <= 0 || declaredSize > MAX_FILE_SIZE) {
            throw new BusinessException(BizErrorCode.PDF_SIZE_LIMIT);
        }
        byte[] content = readBounded(input);
        if (content.length < 5 || content[0] != '%' || content[1] != 'P'
                || content[2] != 'D' || content[3] != 'F' || content[4] != '-') {
            throw new BusinessException(BizErrorCode.UPLOAD_NOT_PDF);
        }
        String hash = sha256(content);
        Optional<FinancialDocument> existing = repository.findByHash(hash);
        if (existing.isPresent()) {
            return existing.get();
        }
        String safeName = hash + ".pdf";
        Path directory = root.resolve(String.valueOf(instrumentId)).normalize();
        if (!directory.startsWith(root)) {
            throw new BusinessException(BizErrorCode.FILE_PATH_INVALID);
        }
        Files.createDirectories(directory);
        Path target = directory.resolve(safeName);
        Files.write(target, content, StandardOpenOption.CREATE_NEW);

        FinancialDocument document = new FinancialDocument();
        document.setInstrumentId(instrumentId);
        document.setReportId(reportId);
        document.setOriginalFileName(safeOriginalName(originalFileName));
        document.setRelativePath(root.relativize(target).toString());
        document.setMimeType("application/pdf");
        document.setFileSize((long) content.length);
        document.setFileHash(hash);
        parse(content, document);
        return repository.save(document);
    }

    public FinancialDocument get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("财报 PDF 不存在：" + id));
    }

    public List<FinancialDocument> listByReport(Long reportId) {
        return repository.findByReport(reportId);
    }

    public Path contentPath(Long id) {
        FinancialDocument document = get(id);
        Path value = root.resolve(document.getRelativePath()).normalize();
        if (!value.startsWith(root)) {
            throw new BusinessException(BizErrorCode.FILE_PATH_INVALID);
        }
        return value;
    }

    private void parse(byte[] content, FinancialDocument document) {
        try {
            String raw = new String(content, StandardCharsets.ISO_8859_1);
            Matcher pages = PAGE_PATTERN.matcher(raw);
            int pageCount = 0;
            while (pages.find()) {
                pageCount++;
            }
            document.setPageCount(pageCount == 0 ? null : pageCount);
            Matcher texts = TEXT_PATTERN.matcher(raw);
            StringBuilder extracted = new StringBuilder();
            while (texts.find()) {
                String value = unescapePdfString(texts.group(1)).trim();
                if (!value.isEmpty()) {
                    if (extracted.length() > 0) {
                        extracted.append('\n');
                    }
                    extracted.append(value);
                }
            }
            document.setExtractedText(extracted.toString());
            document.setParseStatus(extracted.length() == 0 ? "OCR_REQUIRED" : "PARSED");
        } catch (Exception error) {
            document.setParseStatus("PARSE_FAILED");
            document.setErrorMessage(error.getMessage());
        }
    }

    private String unescapePdfString(String value) {
        return value.replace("\\(", "(")
                .replace("\\)", ")")
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t");
    }

    private byte[] readBounded(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int count;
        while ((count = input.read(buffer)) >= 0) {
            total += count;
            if (total > MAX_FILE_SIZE) {
                throw new BusinessException(BizErrorCode.PDF_SIZE_LIMIT);
            }
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private String sha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] value = digest.digest(content);
            StringBuilder result = new StringBuilder();
            for (byte item : value) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    private String safeOriginalName(String value) {
        String name = value == null || value.trim().isEmpty() ? "financial-report.pdf" : value;
        return name.replace("\\", "_").replace("/", "_");
    }
}
