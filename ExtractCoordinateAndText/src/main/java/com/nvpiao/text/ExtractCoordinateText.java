package com.nvpiao.text;

import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;

public class ExtractCoordinateText {
    /**
     * This will print the documents data.
     *
     * @param args The command line arguments.
     * @throws IOException If there is an error parsing the document.
     */
    public static void main(String[] args) throws IOException {
        File pdfFile;
        File bmFile;
        if (args.length != 2) {
            pdfFile = new File("resources/34676-M57-0302_Iss7.pdf");
            bmFile = new File("resources/34676-M57-0302_Iss7-benchmark.csv");
        } else {
            pdfFile = new File(args[0]);
            bmFile = new File(args[1]);
        }

        try (PDDocument document = PDDocument.load(pdfFile)) {
            PDFTextLocations stripper = new PDFTextLocations();
            stripper.setSortByPosition(true);
            stripper.setStartPage(0);
            stripper.setEndPage(document.getNumberOfPages());

            Writer dummy = new OutputStreamWriter(new ByteArrayOutputStream());
            stripper.writeText(document, dummy);

            // tide up text and coordinate
            stripper.tide();

            // extract relation between texts
            stripper.extractRelation(PDFTextLocations.EUCLIDEAN_FUNC);

            // store to file
            stripper.store(pdfFile);

            double accuracy = stripper.getAccuracy(bmFile);
            System.out.println(String.format("Accuracy (Sensors are correctly related to the correct room): %.2f%%", accuracy * 100));
        }
    }
}
