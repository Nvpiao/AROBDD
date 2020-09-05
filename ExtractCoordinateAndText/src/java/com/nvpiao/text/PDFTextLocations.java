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

import com.google.common.collect.Lists;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Extract coordinate and text from pdf
 *
 * @author Ming Liu
 */
public class PDFTextLocations extends PDFTextStripper {

    // manhattan distance function
    public static final BiFunction<CoordinateText.Coordinate,
            CoordinateText.Coordinate, Float>
            MANHATTAN_FUNC = (a, b) -> Math.abs(b.getX() - a.getX())
            + Math.abs(b.getY() - a.getY());

    // cosine distance function
    public static final BiFunction<CoordinateText.Coordinate,
            CoordinateText.Coordinate, Float>
            COSINE_FUNC = (a, b) -> (float) ((a.getX() * b.getX() + a.getY() * b.getY()) * 1.0
            / (Math.sqrt(a.getX() * a.getX() + a.getY() * a.getY())
            * Math.sqrt(b.getX() * b.getX() + b.getY() * b.getY())));

    // euclidean distance function
    public static BiFunction<CoordinateText.Coordinate,
            CoordinateText.Coordinate, Float>
            EUCLIDEAN_FUNC = (a, b) -> (float) Math.sqrt(
            Math.pow(b.getX() - a.getX(), 2)
                    + Math.pow(b.getY() - a.getY(), 2)
    );

    private static final String PATTERN_SENSOR = "(.+/.+)|(^[^-]+-[^-]+$)";

    private final String[] HEADERS = {"x", "y", "text", "type", "group"};

    private final List<String> IGNORES = Lists.newArrayList(
            "changes S01-S09", "T-B", "Pa/m", "m/s", "Intake/Exhast Hood",
            "M57-0301", "M57-0302", "M57-0303", "M57-0304",
            "M57-0004", "M57-0003", "M57-0002", "M57-0001");

    private final int MAX_NAME_LEN = 20;

    private float toleranceX = 10.0f;

    private float toleranceY = 100.0f;

    private boolean canExtract = true;

    private List<CoordinateText> coordinateTexts;

    private List<CoordinateText> coordinateRoomsTexts;

    private List<CoordinateText> coordinateSensorsTexts;

    private List<CoordinateText> resultCoordinateTexts;

    private List<CoordinateText> benchmarkCoordinateTexts;

    /**
     * Instantiate a new PDFTextStripper object.
     *
     * @throws IOException If there is an error loading the properties.
     */
    public PDFTextLocations() throws IOException {
        this(null);
    }

    /**
     * Instantiate a new PDFTextStripper object with benchmark file.
     *
     * @param bmFile File of benchmark coordinates
     * @throws IOException If there is an error loading the properties.
     */
    public PDFTextLocations(File bmFile) throws IOException {
        coordinateTexts = Lists.newArrayList();
        loadBenchmarkCoordinates(bmFile);
    }

    private void loadBenchmarkCoordinates(File bmFile) throws IOException {
        Iterable<CSVRecord> records = CSVFormat.DEFAULT.withHeader("text", "type", "group")
                .withFirstRecordAsHeader()
                .parse(new FileReader(bmFile));

        benchmarkCoordinateTexts = Lists.newArrayList();

        for (CSVRecord record : records) {
            String[] texts = record.get("text").split("_");
            String type = record.get("type");
            int groupId = Integer.parseInt(record.get("group"));

            CoordinateText coordinateText = new CoordinateText(Lists.newArrayList(texts),
                    Type.valueOf(type), groupId);
            benchmarkCoordinateTexts.add(coordinateText);
        }
    }

    public void tide() throws IOException {
        tideAndStore(null);
    }

    public void tideAndStore(File pdfFile) throws IOException {
        if (coordinateTexts.isEmpty()) {
            System.out.println("No text found!");
            canExtract = false;
            return;
        }

        Map<Type, List<CoordinateText>> coordinates = coordinateTexts.stream()
                .collect(
                        Collectors.groupingBy(
                                CoordinateText::getType,
                                Collectors.toList()
                        )
                );
        // split to room and other sensors
        List<CoordinateText> coordinateRoomTexts = coordinates.get(Type.ROOM);
        List<CoordinateText> coordinateSensorTexts = coordinates.get(Type.SENSOR);

        // clean up rooms
        coordinateRoomsTexts = cleanUpRooms(coordinateRoomTexts);

        // clean up sensors
        coordinateSensorsTexts = cleanUpSensors(coordinateSensorTexts);

        // store
        if (!Objects.isNull(pdfFile)) {
            store(pdfFile);
        }
    }

    private List<CoordinateText> cleanUpSensors(List<CoordinateText> coordinateSensorTexts) {
        Pattern sensorsPattern = Pattern.compile(PATTERN_SENSOR);
        return coordinateSensorTexts.stream()
                .filter(coordinateText -> {
                    List<String> texts = coordinateText.getTexts();
                    assert !texts.isEmpty();
                    String text = texts.get(0);
                    // name of sensors can not longer than 20
                    if (text.length() >= MAX_NAME_LEN) {
                        return false;
                    }
                    // ignore file
                    if (IGNORES.contains(text)) {
                        return false;
                    }

                    Matcher sensorMatcher = sensorsPattern.matcher(text);
                    return sensorMatcher.find();
                }).collect(Collectors.toList());
    }

    public void store(File pdfFile) throws IOException {
        if (!canExtract) {
            return;
        }

        String name = pdfFile.getName().substring(0, pdfFile.getName().lastIndexOf('.'));
        name = "output" + File.separator + name + "-loc_text.csv";
        FileWriter out = new FileWriter(name);
        try (CSVPrinter printer = new CSVPrinter(out, CSVFormat.DEFAULT.withHeader(HEADERS))) {
            if (Objects.isNull(coordinateRoomsTexts) && Objects.isNull(coordinateSensorsTexts)) {
                resultCoordinateTexts = Lists.newArrayList(coordinateTexts);
            } else {
                resultCoordinateTexts = Objects.isNull(coordinateRoomsTexts) ? Lists.newArrayList() : Lists.newArrayList(coordinateRoomsTexts);
                resultCoordinateTexts.addAll(coordinateSensorsTexts);
                resultCoordinateTexts.sort(
                        Comparator.comparing(CoordinateText::getGroupId)
                                .thenComparing(CoordinateText::getType)
                );
            }

            // remove all rows are not in benchmark set.
            resultCoordinateTexts = resultCoordinateTexts.stream()
                    .filter(resultCoordinateText -> benchmarkCoordinateTexts.stream()
                            .anyMatch(benchmarkCoordinateText ->
                                    benchmarkCoordinateText.toString()
                                            .equals(resultCoordinateText.toString())))
                    .collect(Collectors.toList());

            write(printer, resultCoordinateTexts);
        }
    }

    private void write(CSVPrinter printer, List<CoordinateText> coordinateRoomsTexts) {
        coordinateRoomsTexts.forEach(coordinateText -> {
            CoordinateText.Coordinate middle = coordinateText.getMiddle();
            try {
                printer.printRecord(middle.getX(), middle.getY(), coordinateText.toString(),
                        coordinateText.getType(), coordinateText.getGroupId());
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private List<CoordinateText> cleanUpRooms(List<CoordinateText> coordinateRoomTexts) {
        // sort by (x, y)
        coordinateRoomTexts.sort(
                Comparator.comparing(
                        coordinateText -> (int) ((CoordinateText) coordinateText).getMiddle().getX()
                ).thenComparing(
                        coordinateText -> ((CoordinateText) coordinateText).getMiddle().getY()
                )
        );

        List<CoordinateText> coordinateRoomTextsGrouped = Lists.newArrayList();
        List<CoordinateText> tmpRoomTextsGrouped = Lists.newArrayList();

        assert !coordinateRoomTexts.isEmpty();
        CoordinateText preCoordinateText = coordinateRoomTexts.get(0);
        tmpRoomTextsGrouped.add(preCoordinateText);

        for (int i = 1; i < coordinateRoomTexts.size(); i++) {
            CoordinateText.Coordinate preMiddle = preCoordinateText.getMiddle();

            CoordinateText tmpCoordinateText = coordinateRoomTexts.get(i);
            CoordinateText.Coordinate tmpMiddle = tmpCoordinateText.getMiddle();

            // group by tolerance
            if (Math.abs(tmpMiddle.getX() - preMiddle.getX()) < toleranceX
                    && Math.abs(tmpMiddle.getY() - preMiddle.getY()) < toleranceY) {
                tmpRoomTextsGrouped.add(tmpCoordinateText);
            } else {
                CoordinateText coordinateTextGrouped = new CoordinateText(tmpRoomTextsGrouped);
                coordinateRoomTextsGrouped.add(coordinateTextGrouped);
                // clear
                tmpRoomTextsGrouped.clear();
                tmpRoomTextsGrouped.add(tmpCoordinateText);
            }

            preCoordinateText = tmpCoordinateText;

            // last group
            if (i == coordinateRoomTexts.size() - 1) {
                CoordinateText coordinateTextGrouped = new CoordinateText(tmpRoomTextsGrouped);
                coordinateRoomTextsGrouped.add(coordinateTextGrouped);
            }
        }

        // set groupId
        for (int i = 0; i < coordinateRoomTextsGrouped.size(); i++) {
            coordinateRoomTextsGrouped.get(i).setGroupId(i);
        }

        return coordinateRoomTextsGrouped;
    }

    /**
     * Override the default functionality of PDFTextStripper.
     */
    @Override
    protected void writeString(String lineText, List<TextPosition> textPositions) {
        // anticlockwise rotation. default direction 270 (clockwise rotation)
        coordinateTexts.add(new CoordinateText(lineText, textPositions));
    }

    /**
     * Assign all sensors to its closest room.
     *
     * @param func A Distance Calculation Function
     */
    public void extractRelation(BiFunction<CoordinateText.Coordinate,
            CoordinateText.Coordinate, Float> func) {
        // check label
        if (!canExtract) {
            return;
        }

        // iterate all sensors
        coordinateSensorsTexts.forEach(coordinateSensorText -> {
            List<Float> distances = Lists.newArrayList();
            coordinateRoomsTexts.forEach(coordinateRoomText -> {
                distances.add(func.apply(coordinateSensorText.getMiddle(),
                        coordinateRoomText.getMiddle()));
            });

            // find minimum distance
            Float minDis = Collections.min(distances);
            int groupId = distances.indexOf(minDis);

            // set groupId
            coordinateSensorText.setGroupId(groupId);
        });
    }

    /**
     * Calculating the accuracy, which is defined as:
     * <p>
     * Accuracy = CC / ADP
     * where \textit{CC} means the number of rows, which is correctly classified.
     * \textit{ADP} means the number of  data rows.
     * </p>
     *
     * @return The accuracy of relationship extraction pipeline
     */
    public double getAccuracy() {
        if (!canExtract) {
            return 0.0;
        }

        AtomicInteger match = new AtomicInteger();

        resultCoordinateTexts.stream()
                .collect(Collectors.groupingBy(CoordinateText::getGroupId))
                .forEach((k, vs) -> {
                    int benchmarkGroupId = -1;
                    for (CoordinateText v : vs) {
                        String name = v.toString();
                        // ignore room
                        if (v.getType() == Type.ROOM) {
                            benchmarkGroupId = benchmarkCoordinateTexts.stream()
                                    .filter(coordinateText -> coordinateText.toString().equals(name))
                                    .findFirst()
                                    .get()
                                    .getGroupId();
                            continue;
                        }

                        int finalBenchmarkGroupId = benchmarkGroupId;
                        boolean anyMatch = benchmarkCoordinateTexts.stream()
                                // same groupId && same text
                                .anyMatch(coordinateText -> coordinateText.getGroupId() == finalBenchmarkGroupId
                                        && coordinateText.toString().equals(name));
                        if (anyMatch) {
                            match.incrementAndGet();
                        }
                    }
                });
        return match.get() * 1.0 / resultCoordinateTexts.size();
    }


    /**
     * Calculating the Purity value of relationship extraction pipeline
     *
     * @return The value of Purity
     */
    public double getPurity() {

        AtomicReference<Double> allPurity = new AtomicReference<>(0.0);
        resultCoordinateTexts.stream()
                .collect(Collectors.groupingBy(CoordinateText::getGroupId))
                .forEach((k, vs) -> {
                    int matchNum = 0;
                    int benchmarkGroupId = -1;
                    for (CoordinateText v : vs) {
                        String name = v.toString();
                        // ignore room
                        if (v.getType() == Type.ROOM) {
                            benchmarkGroupId = benchmarkCoordinateTexts.stream()
                                    .filter(coordinateText -> coordinateText.toString().equals(name))
                                    .findFirst()
                                    .get()
                                    .getGroupId();
                            continue;
                        }

                        int finalBenchmarkGroupId = benchmarkGroupId;
                        boolean anyMatch = benchmarkCoordinateTexts.stream()
                                // same groupId && same text
                                .anyMatch(coordinateText -> coordinateText.getGroupId() == finalBenchmarkGroupId
                                        && coordinateText.toString().equals(name));
                        if (anyMatch) {
                            ++matchNum;
                        }
                    }
                    double tmpPurity = matchNum * 1.0 / resultCoordinateTexts.size();
                    allPurity.set(allPurity.get() + tmpPurity);

                });
        return allPurity.get();
    }

    /**
     * Calculating the RandIndex value of relationship extraction pipeline
     *
     * @return The value of RandIndex
     */
    public double getRandIndex() {

        assert benchmarkCoordinateTexts.size() >= resultCoordinateTexts.size();

        int sames = 0;
        int coordinateSize = benchmarkCoordinateTexts.size();
        // Iteration
        for (int i = 0; i < coordinateSize - 1; ++i) {
            for (int j = i + 1; j < coordinateSize; ++j) {
                boolean xStatus = getStatus(resultCoordinateTexts.get(i),
                        resultCoordinateTexts.get(j));
                boolean yStatus = getStatus(benchmarkCoordinateTexts.get(i),
                        benchmarkCoordinateTexts.get(j));

                sames += (xStatus == yStatus) ? 1 : 0;
            }
        }
        // Rand Index Formula
        return sames * 2.0 / (coordinateSize * (coordinateSize - 1));
    }

    /**
     * Judge if id of group are the same or not.
     *
     * @param x instance of CoordinateText object
     * @param y instance of CoordinateText object
     * @return if id of groups are the same return {@code True},
     * otherwise, return {@code False}
     */
    public boolean getStatus(CoordinateText x, CoordinateText y) {
        return x.getGroupId() == y.getGroupId();
    }


    /**
     * Calculating the F-measure value of relationship extraction pipeline
     *
     * @return The value of F-measure
     */
    public double getFMeasure() {

        double fMeasure = 0.0;

        int m = (int) benchmarkCoordinateTexts.stream()
                .map(CoordinateText::getGroupId)
                .distinct()
                .count();
        int n = resultCoordinateTexts.size();

        for (int j = 0; j < m; ++j) {
            int finalJ = j;
            // find each group in benchmark set
            List<CoordinateText> bCoordinateTexts = benchmarkCoordinateTexts.stream()
                    .filter(coordinateText -> coordinateText.getGroupId() == finalJ)
                    .collect(Collectors.toList());
            long pJ = bCoordinateTexts.size();

            // find ROOM in each benchmark group
            CoordinateText roomCoordinateText = bCoordinateTexts.stream()
                    .filter(coordinateText -> coordinateText.getType() == Type.ROOM)
                    .findFirst()
                    .get();

            // find group id in result set
            int rGroupId = resultCoordinateTexts.stream()
                    .filter(coordinateText -> coordinateText.toString()
                            .equals(roomCoordinateText.toString()))
                    .findFirst()
                    .get()
                    .getGroupId();

            // find the same group in result set
            List<CoordinateText> rCoordinateTexts = resultCoordinateTexts.stream()
                    .filter(coordinateText -> coordinateText.getGroupId() == rGroupId)
                    .collect(Collectors.toList());
            int cI = rCoordinateTexts.size();

            // compare and calculate F-measure_j
            long pjci = rCoordinateTexts.stream()
                    .filter(rCoordinateText -> bCoordinateTexts.stream()
                            .anyMatch(bCoordinateText -> bCoordinateText.toString()
                                    .equals(rCoordinateText.toString())))
                    .count();

            // precision && recall
            double precision = pjci * 1.0 / cI;
            double recall = pjci * 1.0 / pJ;

            // F-measure
            double tmpFMeasure = 2 * precision * recall / (precision + recall);

            // weight
            double wJ = pJ * 1.0 / n;

            // accumulative F-measure
            fMeasure += tmpFMeasure * wJ;
        }

        return fMeasure;
    }
}
