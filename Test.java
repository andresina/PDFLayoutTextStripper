import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.apache.pdfbox.pdfparser.PDFParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.util.PDFTextStripper;

public class Test {

	public static void main(String[] args) throws FileNotFoundException, IOException {
		FileInputStream in = new FileInputStream("sample.pdf");
		PDFParser pdfParser = new PDFParser(in);
		pdfParser.parse();
		PDDocument pdDocument = new PDDocument(pdfParser.getDocument());
		PDFTextStripper pdfTextStripper = new PDFLayoutTextStripper();
		System.out.println(pdfTextStripper.getText(pdDocument));
		pdDocument.close();
		in.close();
	}

}