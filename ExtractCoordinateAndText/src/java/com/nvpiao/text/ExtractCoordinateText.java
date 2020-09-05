/*  Copyright (c) 2020 Ming Liu <Mliu54@sheffield.ac.uk>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.nvpiao.text;

import com.nvpiao.utils.DrawPrintTextLocations;
import com.nvpiao.utils.ParseConfigFileUtil;
import com.nvpiao.utils.RemoveAllText;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Extractor main class
 *
 * @author Ming Liu
 */
public class ExtractCoordinateText {
    /**
     * This will print the documents data.
     *
     * @param args The command line arguments.
     * @throws IOException If there is an error parsing the document.
     */
    public static void main(String[] args) throws IOException {
        String configFile = "config";

        if (args.length == 1) {
            configFile = args[0];
        }

        Properties config = ParseConfigFileUtil.parse(configFile);

        File pdfFile = new File(config.getProperty("input_pdf_file"));
        File bmFile = new File(config.getProperty("benchmark_result_file"));


        // create output directory
        if (!Files.exists(Paths.get("output"))) {
            Files.createDirectory(Paths.get("output"));
        }

        if ("true".equalsIgnoreCase(config.getProperty("show_draw_text_result"))) {
            // draw text in pdf file
            DrawPrintTextLocations.main(new String[]{config.getProperty("input_pdf_file")});
        }

        if ("true".equalsIgnoreCase(config.getProperty("remove_all_text_from_pdf"))) {
            // remove all text from pdf
            String output = pdfFile.getName().substring(0, pdfFile.getName().lastIndexOf('.'));
            output = "output" + File.separator + output + "-text_removed.pdf";
            RemoveAllText.main(new String[]{config.getProperty("input_pdf_file"), output});
        }

        try (PDDocument document = PDDocument.load(pdfFile)) {
            PDFTextLocations stripper = new PDFTextLocations(bmFile);
            stripper.setSortByPosition(true);
            stripper.setStartPage(0);
            stripper.setEndPage(document.getNumberOfPages());

            Writer dummy = new OutputStreamWriter(new ByteArrayOutputStream());
            stripper.writeText(document, dummy);

            // tide up text and coordinate
            stripper.tide();

            // extract relation between texts
            if ("euclidean".equalsIgnoreCase(config.getProperty("distance_algorithm"))) {
                stripper.extractRelation(PDFTextLocations.EUCLIDEAN_FUNC);
            } else if ("manhattan".equalsIgnoreCase(config.getProperty("distance_algorithm"))) {
                stripper.extractRelation(PDFTextLocations.MANHATTAN_FUNC);
            } else if ("cosine".equalsIgnoreCase(config.getProperty("distance_algorithm"))) {
                stripper.extractRelation(PDFTextLocations.COSINE_FUNC);
            }

            // integrate all data and store to file
            stripper.store(pdfFile);

            // accuracy
            double accuracy = stripper.getAccuracy();

            // purity
            double purity = stripper.getPurity();

            // rand index
            double randIndex = stripper.getRandIndex();

            // F-measure
            double fMeasure = stripper.getFMeasure();

            if ("true".equalsIgnoreCase(config.getProperty("result_format"))) {
                System.out.println("+------------------------------------------------------------------------------------------------+");
                System.out.println("|                                        Extraction Result                                       |");
                System.out.println("+----------------------+-------------------------------------------------------------------------+");
                System.out.println(String.format("| Extracted PDF File:  |    %s                                              |", pdfFile.getName()));
                System.out.println("+----------------------+------------------------+------------------------+-----------------------+");
                System.out.println(String.format("| Distance Metric:     |    %s distance                                                   |", config.getProperty("distance_algorithm")));
                System.out.println("+----------------------+------------------------+------------------------+-----------------------+");
                System.out.println("|       Accuracy       |         Purity         |       Rand Index       |       F-measure       |");
                System.out.println("+----------------------+------------------------+------------------------+-----------------------+");
                System.out.println(String.format("|        %.2f%%        |         %.4f         |         %.4f         |         %.4f        |", accuracy * 100, purity, randIndex, fMeasure));
                System.out.println("+----------------------+------------------------+------------------------+-----------------------+");
            } else {
                System.out.println(String.format("Extracted PDF file: %s", pdfFile.getName()));
                System.out.println(String.format("Distance Metric:    %s distance", config.getProperty("distance_algorithm")));
                System.out.println(String.format("Accuracy:     %.2f%%", accuracy * 100));
                System.out.println(String.format("Purity:       %.4f", purity));
                System.out.println(String.format("Rand Index:   %.4f", randIndex));
                System.out.println(String.format("F-measure:    %.4f", fMeasure));
            }
        }
    }
}
