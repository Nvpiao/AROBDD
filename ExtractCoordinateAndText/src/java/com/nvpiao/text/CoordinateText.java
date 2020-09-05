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
import org.apache.pdfbox.text.TextPosition;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A class contains coordinates and text
 *
 * @author Ming Liu
 */
public class CoordinateText {

    /**
     * list of text
     */
    private List<String> texts;

    private Type type;

    private Coordinate topLeft;

    private Coordinate bottomRight;

    private Coordinate middle;

    private int groupId;

    CoordinateText(String text, List<TextPosition> textPositions) {
        // add text
        this.texts = Lists.newArrayList(text);

        assert !textPositions.isEmpty();
        // maximum size of front
        if (textPositions.get(0).getFontSize() == 233.0) {
            this.type = Type.ROOM;
        } else {
            this.type = Type.SENSOR;
        }

        // top left
        this.topLeft = new Coordinate(textPositions.get(0).getX(),
                textPositions.get(0).getY());

        // bottom right
        this.bottomRight = new Coordinate(textPositions.get(textPositions.size() - 1).getX(),
                textPositions.get(textPositions.size() - 1).getY());

        this.middle = new Coordinate((topLeft.x + bottomRight.x) / 2.0f, (topLeft.y + bottomRight.y) / 2.0f);
    }

    public CoordinateText(List<CoordinateText> coordinateTextsGroups) {
        assert !coordinateTextsGroups.isEmpty();
        this.texts = Lists.newArrayList(coordinateTextsGroups.stream()
                .sorted(Comparator.comparing(coordinateText -> coordinateText.getMiddle().getY()))
                .map(CoordinateText::getTexts)
                .reduce(Lists.newArrayList(), (acc, item) -> {
                    acc.addAll(item);
                    return acc;
                }));
        this.type = coordinateTextsGroups.get(0).getType();

        float xMin = (float) coordinateTextsGroups.stream()
                .map(CoordinateText::getTopLeft)
                .mapToDouble(Coordinate::getX)
                .min()
                .getAsDouble();
        float yMin = (float) coordinateTextsGroups.stream()
                .map(CoordinateText::getTopLeft)
                .mapToDouble(Coordinate::getY)
                .min()
                .getAsDouble();
        this.topLeft = new Coordinate(xMin, yMin);

        float xMax = (float) coordinateTextsGroups.stream()
                .map(CoordinateText::getBottomRight)
                .mapToDouble(Coordinate::getX)
                .max()
                .getAsDouble();
        float yMax = (float) coordinateTextsGroups.stream()
                .map(CoordinateText::getBottomRight)
                .mapToDouble(Coordinate::getY)
                .max()
                .getAsDouble();
        this.bottomRight = new Coordinate(xMax, yMax);

        this.middle = new Coordinate((topLeft.x + bottomRight.x) / 2.0f, (topLeft.y + bottomRight.y) / 2.0f);
    }

    public CoordinateText() {
    }

    public CoordinateText(ArrayList<String> texts, Type type, int groupId) {
        this.texts = texts;
        this.type = type;
        this.groupId = groupId;
    }

    @Override
    public String toString() {
        return String.join("_", texts);
    }

    public List<String> getTexts() {
        return this.texts;
    }

    public Type getType() {
        return this.type;
    }

    public Coordinate getTopLeft() {
        return this.topLeft;
    }

    public Coordinate getBottomRight() {
        return this.bottomRight;
    }

    public Coordinate getMiddle() {
        return this.middle;
    }

    public int getGroupId() {
        return groupId;
    }

    public void setTexts(List<String> texts) {
        this.texts = texts;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public void setTopLeft(Coordinate topLeft) {
        this.topLeft = topLeft;
    }

    public void setBottomRight(Coordinate bottomRight) {
        this.bottomRight = bottomRight;
    }

    public void setMiddle(Coordinate middle) {
        this.middle = middle;
    }

    public void setGroupId(int groupId) {
        this.groupId = groupId;
    }

    class Coordinate {
        private float x;
        private float y;

        public Coordinate(float x, float y) {
            this.x = x;
            this.y = y;
        }

        public float getX() {
            return this.x;
        }

        public float getY() {
            return this.y;
        }

        public void setX(float x) {
            this.x = x;
        }

        public void setY(float y) {
            this.y = y;
        }
    }
}
