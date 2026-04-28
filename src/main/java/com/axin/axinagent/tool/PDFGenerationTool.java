package com.axin.axinagent.tool;

import cn.hutool.core.io.FileUtil;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.IOException;

public class PDFGenerationTool {

	/** PDF 文件保存根目录 */
	private static final String FILE_SAVE_DIR = System.getProperty("user.dir") + "/tmp";

	@Tool(description = "Generate a PDF file with given content")
	public String generatePDF(
			@ToolParam(description = "Name of the file to save the generated PDF") String fileName,
			@ToolParam(description = "Content to be included in the PDF") String content) {
		String fileDir = FILE_SAVE_DIR + "/pdf";
		String filePath = fileDir + "/" + fileName;
		try {
			FileUtil.mkdir(fileDir);
			try (PdfWriter writer = new PdfWriter(filePath);
			     PdfDocument pdf = new PdfDocument(writer);
			     Document document = new Document(pdf)) {
				PdfFont font = PdfFontFactory.createFont("STSongStd-Light", "UniGB-UCS2-H");
				document.setFont(font);
				document.add(new Paragraph(content));
			}
			return "PDF generated successfully to: " + filePath;
		} catch (IOException e) {
			return "Error generating PDF: " + e.getMessage();
		}
	}
}

