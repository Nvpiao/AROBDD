/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
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

    public static BiFunction<CoordinateText.Coordinate, CoordinateText.Coordinate, Float> EUCLIDEAN_FUNC = (a, b) ->
            (float) Math.sqrt(Math.pow(b.getX() - a.getX(), 2) + Math.pow(b.getY() - a.getY(), 2));

    private static final String PATTERN_FACILITY = "(.+/.+)|(^[^-]+-[^-]+$)";

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

    private List<CoordinateText> coordinateFacilitiesTexts;

    private List<CoordinateText> resultCoordinateText;

    /**
     * Instantiate a new PDFTextStripper object.
     *
     * @throws IOException If there is an error loading the properties.
     */
    public PDFTextLocations() throws IOException {
        coordinateTexts = Lists.newArrayList();
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
        // split to room and other facilities
        List<CoordinateText> coordinateRoomTexts = coordinates.get(Type.ROOM);
        List<CoordinateText> coordinateFacilityTexts = coordinates.get(Type.FACILITY);

        // clean up rooms
        coordinateRoomsTexts = cleanUpRooms(coordinateRoomTexts);

        // clean up facilities
        coordinateFacilitiesTexts = cleanUpFacilities(coordinateFacilityTexts);

        // store
        if (!Objects.isNull(pdfFile)) {
            store(pdfFile);
        }
    }

    private List<CoordinateText> cleanUpFacilities(List<CoordinateText> coordinateFacilityTexts) {
        Pattern facilityPattern = Pattern.compile(PATTERN_FACILITY);
        return coordinateFacilityTexts.stream()
                .filter(coordinateText -> {
                    List<String> texts = coordinateText.getTexts();
                    assert !texts.isEmpty();
                    String text = texts.get(0);
                    // name of facilities can not longer than 20
                    if (text.length() >= MAX_NAME_LEN) {
                        return false;
                    }
                    // ignore file
                    if (IGNORES.contains(text)) {
                        return false;
                    }

                    Matcher facilityMatcher = facilityPattern.matcher(text);
                    return facilityMatcher.find();
                }).collect(Collectors.toList());
    }

    public void store(File pdfFile) throws IOException {
        if (!canExtract) {
            return;
        }

        String name = pdfFile.getName().substring(0, pdfFile.getName().lastIndexOf('.'));
        name = pdfFile.getParentFile().getAbsolutePath() + File.separator + name + "-loc_text.csv";
        FileWriter out = new FileWriter(name);
        try (CSVPrinter printer = new CSVPrinter(out, CSVFormat.DEFAULT.withHeader(HEADERS))) {
            resultCoordinateText = Lists.newArrayList(coordinateRoomsTexts);
            resultCoordinateText.addAll(coordinateFacilitiesTexts);
            resultCoordinateText.sort(
                    Comparator.comparing(CoordinateText::getGroupId)
                            .thenComparing(CoordinateText::getType)
            );

            write(printer, resultCoordinateText);
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

    public void extractRelation(BiFunction<CoordinateText.Coordinate, CoordinateText.Coordinate, Float> func) {
        if (!canExtract) {
            return;
        }

        coordinateFacilitiesTexts.forEach(coordinateFacilityText -> {
            List<Float> distances = Lists.newArrayList();
            coordinateRoomsTexts.forEach(coordinateRoomText -> {
                distances.add(func.apply(coordinateFacilityText.getMiddle(), coordinateRoomText.getMiddle()));
            });

            // find minimum distance
            Float minDis = Collections.min(distances);
            int groupId = distances.indexOf(minDis);

            // set groupId
            coordinateFacilityText.setGroupId(groupId);
        });
    }

    public double getAccuracy(File bmFile) throws IOException {
        if (!canExtract) {
            return 0.0;
        }

        Iterable<CSVRecord> records = CSVFormat.DEFAULT.withHeader("text", "type", "group")
                .withFirstRecordAsHeader()
                .parse(new FileReader(bmFile));

        List<CoordinateText> benchmarkCoordinateTexts = Lists.newArrayList();

        for (CSVRecord record : records) {
            String[] texts = record.get("text").split("_");
            String type = record.get("type");
            int groupId = Integer.parseInt(record.get("group"));

            CoordinateText coordinateText = new CoordinateText(Lists.newArrayList(texts),
                    Type.valueOf(type), groupId);
            benchmarkCoordinateTexts.add(coordinateText);
        }

        AtomicInteger match = new AtomicInteger();
        AtomicInteger allTexts = new AtomicInteger();

        resultCoordinateText.stream()
                .collect(Collectors.groupingBy(CoordinateText::getGroupId))
                .forEach((k, vs) -> {
                    for (CoordinateText v : vs) {
                        // ignore room
                        if (v.getType() == Type.ROOM) {
                            continue;
                        }

                        String name = v.toString();
                        boolean anyMatch = benchmarkCoordinateTexts.stream()
                                // same groupId && same text
                                .anyMatch(coordinateText -> coordinateText.getGroupId() == k
                                        && coordinateText.toString().equals(name));
                        if (anyMatch) {
                            match.incrementAndGet();
                        }
                        allTexts.incrementAndGet();
                    }
                });
        return match.get() * 1.0 / allTexts.get();
    }
}
