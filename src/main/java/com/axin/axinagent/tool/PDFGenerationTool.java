package com.axin.axinagent.tool;

import com.axin.axinagent.infrastructure.storage.StorageService;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Component
public class PDFGenerationTool {

    private final StorageService storageService;

    public PDFGenerationTool(StorageService storageService) {
        this.storageService = storageService;
    }

    @Tool(description = "Generate a PDF file with given content")
    public String generatePDF(
            @ToolParam(description = "Name of the file to save the generated PDF") String fileName,
            @ToolParam(description = "Content to be included in the PDF") String content) {
        String normalizedFileName = normalizePdfFilename(fileName);
        try {
            byte[] pdfBytes = buildPdfBytes(content);
            String relativePath = storageService.saveFile("pdf", normalizedFileName, pdfBytes);
            String absolutePath = storageService.getFilePath("pdf", normalizedFileName);
            return "PDF generated successfully to: " + absolutePath + " (relative: " + relativePath + ")";
        } catch (IOException e) {
            return "Error generating PDF: " + e.getMessage();
        }
    }

    private String normalizePdfFilename(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "output.pdf";
        }
        String trimmed = fileName.trim();
        return trimmed.toLowerCase().endsWith(".pdf") ? trimmed : trimmed + ".pdf";
    }

    private byte[] buildPdfBytes(String content) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (PdfWriter writer = new PdfWriter(outputStream);
             PdfDocument pdf = new PdfDocument(writer);
             Document document = new Document(pdf)) {
            PdfFont font = PdfFontFactory.createFont("STSongStd-Light", "UniGB-UCS2-H");
            document.setFont(font);
            document.add(new Paragraph(content == null ? "" : content));
        }
        return outputStream.toByteArray();
    }
}
