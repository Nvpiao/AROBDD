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
