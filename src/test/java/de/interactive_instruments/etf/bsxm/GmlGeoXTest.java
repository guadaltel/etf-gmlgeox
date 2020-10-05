/**
 * Copyright 2010-2019 interactive instruments GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.interactive_instruments.etf.bsxm;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;

import org.apache.commons.io.input.XmlStreamReader;
import org.basex.query.QueryTest;

/**
 * @author Johannes Echterhoff (echterhoff <at> interactive-instruments <dot> de)
 *
 */
public final class GmlGeoXTest extends QueryTest {

    private static String xmlFile = "src/test/resources/xml/geometryRelationship/GeometryRelationshipTest.xml";

    private static String COMMON_QUERY_PART = "import module namespace ggeo = 'de.interactive_instruments.etf.bsxm.GmlGeoX';\n"
            + "declare namespace gml = 'http://www.opengis.net/gml/3.2';\n"
            + "\n" + "let $points := //gml:Point\n"
            + "let $point1 := $points[@gml:id = 'p1']\n"
            + "let $point2 := $points[@gml:id = 'p2']\n"
            + "let $point3 := $points[@gml:id = 'p3']\n"
            + "let $point4 := $points[@gml:id = 'p4']\n"
            + "let $point5 := $points[@gml:id = 'p5']\n" + "\n"
            + "let $curves := //gml:Curve\n"
            + "let $curve1 := $curves[@gml:id = 'c1']\n"
            + "let $curve2 := $curves[@gml:id = 'c2']\n"
            + "let $curve3 := $curves[@gml:id = 'c3']\n"
            + "let $curve4 := $curves[@gml:id = 'c4']\n"
            + "let $curve5 := $curves[@gml:id = 'c5']\n"
            + "let $curve6 := $curves[@gml:id = 'c6']\n"
            + "let $curve7 := $curves[@gml:id = 'c7']\n"
            + "let $curve8 := $curves[@gml:id = 'c8']\n"
            + "let $curve9 := $curves[@gml:id = 'c9']\n"
            + "let $curve10 := $curves[@gml:id = 'c10']\n"
            + "let $curve11 := $curves[@gml:id = 'c11']\n"
            + "let $curve12 := $curves[@gml:id = 'c12']\n"
            + "let $curve13 := $curves[@gml:id = 'c13']\n"
            + "let $curve14 := $curves[@gml:id = 'c14']\n"
            + "let $curve15 := $curves[@gml:id = 'c15']\n" + "\n"
            + "let $surfaces := //gml:Surface\n"
            + "let $surface1 := $surfaces[@gml:id = 's1']\n"
            + "let $surface2 := $surfaces[@gml:id = 's2']\n"
            + "let $surface3 := $surfaces[@gml:id = 's3']\n"
            + "let $surface4 := $surfaces[@gml:id = 's4']\n"
            + "let $surface5 := $surfaces[@gml:id = 's5']\n"
            + "let $surface6 := $surfaces[@gml:id = 's6']\n"
            + "let $surface7 := $surfaces[@gml:id = 's7']\n"
            + "let $surface8 := $surfaces[@gml:id = 's8']\n"
            + "let $surface9 := $surfaces[@gml:id = 's9']\n"
            + "let $surface10 := $surfaces[@gml:id = 's10']\n"
            + "let $surface11 := $surfaces[@gml:id = 's11']\n"
            + "let $surface12 := $surfaces[@gml:id = 's12']\n"
            + "let $surface13 := $surfaces[@gml:id = 's13']\n"
            + "let $surface14 := $surfaces[@gml:id = 's14']\n"
            + "let $surface15 := $surfaces[@gml:id = 's15']\n"
            + "let $surface16 := $surfaces[@gml:id = 's16']\n"
            + "let $surface17 := $surfaces[@gml:id = 's17']\n"
            + "let $surface18 := $surfaces[@gml:id = 's18']\n";

    /** Constructor. */
    static {

        try (BufferedReader bufReader = new BufferedReader(
                new XmlStreamReader(new File(xmlFile)))) {

            StringBuilder sb = new StringBuilder();
            String line;

            while ((line = bufReader.readLine()) != null) {
                sb.append(line);
            }

            String result = sb.toString();

            result = result.replaceAll("\"", "'");

            create(result);

        } catch (IOException e) {
            // TODO this test should be refactored to resemble the
            // XQuerySchematronTest, where queries are executed against
            // individually created databases
        }

        queries = new Object[][]{{"Point/Point",
                booleans(true, false, false, false, true, false, true, false, false,
                        true, true, false, false, false, false, false),
                COMMON_QUERY_PART + "return (\n"
                        + "ggeo:contains($point1,$point2), (: true :)\n"
                        + "  ggeo:contains($point1,$point3), (: false :)\n"
                        + "  ggeo:crosses($point1,$point2), (: false :)\n"
                        + "  ggeo:crosses($point1,$point3), (: false :)\n"
                        + "  ggeo:equals($point1,$point2), (: true :)\n"
                        + "  ggeo:equals($point1,$point3), (: false :)\n"
                        + "  ggeo:intersects($point1,$point2), (: true :)\n"
                        + "  ggeo:intersects($point1,$point3), (: false :)\n"
                        + "  ggeo:isDisjoint($point1,$point2), (: false :)\n"
                        + "  ggeo:isDisjoint($point1,$point3), (: true :)\n"
                        + "  ggeo:isWithin($point1,$point2), (: true :)\n"
                        + "  ggeo:isWithin($point1,$point3), (: false :)\n"
                        + "  ggeo:overlaps($point1,$point2), (: false :)\n"
                        + "  ggeo:overlaps($point1,$point3), (: false :)\n"
                        + "  ggeo:touches($point1,$point2), (: false :)\n"
                        + "  ggeo:touches($point1,$point3) (: false :)" + ")"},

                {"Point/Curve",
                        booleans(false, false, true, false, false, false, false,
                                false, false, false, false, true, true, true,
                                true, true, true, false, false, false, false,
                                false, false, true, false, false, false, false,
                                false, false, false, false, false, false, false,
                                false, false, true, true),
                        COMMON_QUERY_PART + "return (\n"
                                + "  ggeo:contains($point1,$curve1), (: false :)\n"
                                + "  ggeo:contains($curve1,$point1), (: false :)\n"
                                + "  ggeo:contains($curve1,$point4), (: true :)\n"
                                + "  ggeo:contains($curve1,$point3), (: false :)\n"
                                + "  ggeo:crosses($point1,$curve1), (: false :)\n"
                                + "  ggeo:crosses($curve1,$point1), (: false :)\n"
                                + "  ggeo:crosses($point4,$curve1), (: false :)\n"
                                + "  ggeo:crosses($curve1,$point4), (: false :)\n"
                                + "  ggeo:equals($point1,$curve1), (: false :)\n"
                                + "  ggeo:intersects($point3,$curve1), (: false :)\n"
                                + "  ggeo:intersects($curve1,$point3), (: false :)\n"
                                + "  ggeo:intersects($point4,$curve1), (: true :)\n"
                                + "  ggeo:intersects($curve1,$point4), (: true :)\n"
                                + "  ggeo:intersects($point1,$curve1), (: true :)\n"
                                + "  ggeo:intersects($curve1,$point1), (: true :)\n"
                                + "  ggeo:isDisjoint($point3,$curve1), (: true :)\n"
                                + "  ggeo:isDisjoint($curve1,$point3), (: true :)\n"
                                + "  ggeo:isDisjoint($point4,$curve1), (: false :)\n"
                                + "  ggeo:isDisjoint($curve1,$point4), (: false :)\n"
                                + "  ggeo:isDisjoint($point1,$curve1), (: false :)\n"
                                + "  ggeo:isDisjoint($curve1,$point1), (: false :)\n"
                                + "  ggeo:isWithin($point3,$curve1), (: false :)\n"
                                + "  ggeo:isWithin($curve1,$point3), (: false :)\n"
                                + "  ggeo:isWithin($point4,$curve1), (: true :)\n"
                                + "  ggeo:isWithin($curve1,$point4), (: false :)\n"
                                + "  ggeo:isWithin($point1,$curve1), (: false :)\n"
                                + "  ggeo:isWithin($curve1,$point1), (: false :)\n"
                                + "  ggeo:overlaps($point3,$curve1), (: false :)\n"
                                + "  ggeo:overlaps($curve1,$point3), (: false :)\n"
                                + "  ggeo:overlaps($point4,$curve1), (: false :)\n"
                                + "  ggeo:overlaps($curve1,$point4), (: false :)\n"
                                + "  ggeo:overlaps($point1,$curve1), (: false :)\n"
                                + "  ggeo:overlaps($curve1,$point1), (: false :)\n"
                                + "  ggeo:touches($point3,$curve1), (: false :)\n"
                                + "  ggeo:touches($curve1,$point3), (: false :)\n"
                                + "  ggeo:touches($point4,$curve1), (: false :)\n"
                                + "  ggeo:touches($curve1,$point4), (: false :)\n"
                                + "  ggeo:touches($point1,$curve1), (: true :)\n"
                                + "  ggeo:touches($curve1,$point1) (: true :)"
                                + ")"},

                {"Point/Surface",
                        booleans(false, false, false, true, false, false, false,
                                false, false, false, false, true, true, true,
                                true, true, true, false, false, false, false,
                                false, false, true, false, false, false, false,
                                false, false, false, false, false, false, false,
                                false, false, true, true),
                        COMMON_QUERY_PART + "return (\n"
                                + "  ggeo:contains($point1,$surface1), (: false :)\n"
                                + "  ggeo:contains($surface1,$point1), (: false :)\n"
                                + "  ggeo:contains($surface1,$point4), (: false :)\n"
                                + "  ggeo:contains($surface1,$point5), (: true :)\n"
                                + "  ggeo:crosses($point1,$surface1), (: false :)\n"
                                + "  ggeo:crosses($surface1,$point1), (: false :)\n"
                                + "  ggeo:crosses($point5,$surface1), (: false :)\n"
                                + "  ggeo:crosses($surface1,$point5), (: false :)\n"
                                + "  ggeo:equals($point1,$surface1), (: false :)\n"
                                + "  ggeo:intersects($point4,$surface1), (: false :)\n"
                                + "  ggeo:intersects($surface1,$point4), (: false :)\n"
                                + "  ggeo:intersects($point5,$surface1), (: true :)\n"
                                + "  ggeo:intersects($surface1,$point5), (: true :)\n"
                                + "  ggeo:intersects($point1,$surface1), (: true :)\n"
                                + "  ggeo:intersects($surface1,$point1), (: true :)\n"
                                + "  ggeo:isDisjoint($point4,$surface1), (: true :)\n"
                                + "  ggeo:isDisjoint($surface1,$point4), (: true :)\n"
                                + "  ggeo:isDisjoint($point5,$surface1), (: false :)\n"
                                + "  ggeo:isDisjoint($surface1,$point5), (: false :)\n"
                                + "  ggeo:isDisjoint($point1,$surface1), (: false :)\n"
                                + "  ggeo:isDisjoint($surface1,$point1), (: false :)\n"
                                + "  ggeo:isWithin($point3,$surface1), (: false :)\n"
                                + "  ggeo:isWithin($surface1,$point3), (: false :)\n"
                                + "  ggeo:isWithin($point5,$surface1), (: true :)\n"
                                + "  ggeo:isWithin($surface1,$point5), (: false :)\n"
                                + "  ggeo:isWithin($point4,$surface1), (: false :)\n"
                                + "  ggeo:isWithin($surface1,$point4), (: false :)\n"
                                + "  ggeo:overlaps($point4,$surface1), (: false :)\n"
                                + "  ggeo:overlaps($surface1,$point4), (: false :)\n"
                                + "  ggeo:overlaps($point5,$surface1), (: false :)\n"
                                + "  ggeo:overlaps($surface1,$point5), (: false :)\n"
                                + "  ggeo:overlaps($point1,$surface1), (: false :)\n"
                                + "  ggeo:overlaps($surface1,$point1), (: false :)\n"
                                + "  ggeo:touches($point4,$surface1), (: false :)\n"
                                + "  ggeo:touches($surface1,$point4), (: false :)\n"
                                + "  ggeo:touches($point5,$surface1), (: false :)\n"
                                + "  ggeo:touches($surface1,$point5), (: false :)\n"
                                + "  ggeo:touches($point1,$surface1), (: true :)\n"
                                + "  ggeo:touches($surface1,$point1) (: true :)"
                                + ")"},

                {"Curve/Curve", booleans(true, true, true, false, false, false,
                        false, false, true, false, true, false, false, true,
                        true, true, true, true, false, false, false, false,
                        false, false, true, true, false, false, false, false,
                        true, false, false, false, false, false, true),
                        COMMON_QUERY_PART + "return (\n"
                                + "  ggeo:contains($curve1,$curve1), (: true :)\n"
                                + "  ggeo:contains($curve2,$curve3), (: true :)\n"
                                + "  ggeo:contains($curve2,$curve7), (: true :)\n"
                                + "  ggeo:contains($curve2,$curve6), (: false :)\n"
                                + "  ggeo:crosses($curve1,$curve2), (: false :)\n"
                                + "  ggeo:crosses($curve2,$curve4), (: false :)\n"
                                + "  ggeo:crosses($curve2,$curve7), (: false :)\n"
                                + "  ggeo:crosses($curve2,$curve5), (: false :)\n"
                                + "  ggeo:crosses($curve4,$curve6), (: true :)\n"
                                + "  ggeo:equals($curve1,$curve2), (: false :)\n"
                                + "  ggeo:equals($curve2,$curve3), (: true :)\n"
                                + "  ggeo:equals($curve2,$curve5), (: false :)\n"
                                + "  ggeo:intersects($curve1,$curve2), (: false :)\n"
                                + "  ggeo:intersects($curve2,$curve7), (: true :)\n"
                                + "  ggeo:intersects($curve4,$curve6), (: true :)\n"
                                + "  ggeo:intersects($curve2,$curve5), (: true :)\n"
                                + "  ggeo:intersects($curve2,$curve4), (: true :)\n"
                                + "  ggeo:isDisjoint($curve1,$curve2), (: true :)\n"
                                + "  ggeo:isDisjoint($curve2,$curve7), (: false :)\n"
                                + "  ggeo:isDisjoint($curve4,$curve6), (: false :)\n"
                                + "  ggeo:isDisjoint($curve2,$curve5), (: false :)\n"
                                + "  ggeo:isDisjoint($curve2,$curve4), (: false :)\n"
                                + "  ggeo:isWithin($curve2,$curve1), (: false :)\n"
                                + "  ggeo:isWithin($curve4,$curve6), (: false :)\n"
                                + "  ggeo:isWithin($curve7,$curve2), (: true :)\n"
                                + "  ggeo:isWithin($curve2,$curve3), (: true :)\n"
                                + "  ggeo:isWithin($curve2,$curve5), (: false :)\n"
                                + "  ggeo:overlaps($curve2,$curve1), (: false :)\n"
                                + "  ggeo:overlaps($curve2,$curve4), (: false :)\n"
                                + "  ggeo:overlaps($curve2,$curve7), (: false :)\n"
                                + "  ggeo:overlaps($curve2,$curve5), (: true :)\n"
                                + "  ggeo:overlaps($curve6,$curve4), (: false :)\n"
                                + "  ggeo:touches($curve2,$curve1), (: false :)\n"
                                + "  ggeo:touches($curve2,$curve7), (: false :)\n"
                                + "  ggeo:touches($curve2,$curve5), (: false :)\n"
                                + "  ggeo:touches($curve4,$curve6), (: false :)\n"
                                + "  ggeo:touches($curve2,$curve4) (: true :)"
                                + ")"},

                {"Curve/Surface",
                        booleans(false, false, true, false, false, false, true,
                                true, false, false, false, true, true, true,
                                true, true, false, false, false, false, false,
                                false, false, false, true, false, false, false,
                                false, false, false, false, false, false, false,
                                false, true, true, true, true),
                        COMMON_QUERY_PART + "return (\n"
                                + "  ggeo:contains($curve1,$surface2), (: false :)\n"
                                + "  ggeo:contains($surface2,$curve1), (: false :)\n"
                                + "  ggeo:contains($surface2,$curve12), (: true :)\n"
                                + "  ggeo:contains($surface2,$curve11), (: false :)\n"
                                + "  ggeo:crosses($curve8,$surface2), (: false :)\n"
                                + "  ggeo:crosses($surface2,$curve8), (: false :)\n"
                                + "  ggeo:crosses($curve9,$surface2), (: true :)\n"
                                + "  ggeo:crosses($curve13,$surface2), (: true :)\n"
                                + "  ggeo:crosses($curve10,$surface2), (: false :)\n"
                                + "  ggeo:equals($curve1,$surface1), (: false :)\n"
                                + "  ggeo:intersects($curve1,$surface2), (: false :)\n"
                                + "  ggeo:intersects($surface2,$curve8), (: true :)\n"
                                + "  ggeo:intersects($curve10,$surface2), (: true :)\n"
                                + "  ggeo:intersects($surface2,$curve9), (: true :)\n"
                                + "  ggeo:intersects($curve12,$surface2), (: true :)\n"
                                + "  ggeo:isDisjoint($curve1,$surface2), (: true :)\n"
                                + "  ggeo:isDisjoint($surface2,$curve8), (: false :)\n"
                                + "  ggeo:isDisjoint($curve10,$surface2), (: false :)\n"
                                + "  ggeo:isDisjoint($surface2,$curve9), (: false :)\n"
                                + "  ggeo:isDisjoint($curve12,$surface2), (: false :)\n"
                                + "  ggeo:isWithin($curve1,$surface2), (: false :)\n"
                                + "  ggeo:isWithin($surface2,$curve1), (: false :)\n"
                                + "  ggeo:isWithin($curve9,$surface2), (: false :)\n"
                                + "  ggeo:isWithin($surface2,$curve9), (: false :)\n"
                                + "  ggeo:isWithin($curve12,$surface2), (: true :)\n"
                                + "  ggeo:isWithin($surface2,$curve12), (: false :)\n"
                                + "  ggeo:isWithin($curve11,$surface2), (: false :)\n"
                                + "  ggeo:isWithin($surface2,$curve11), (: false :)\n"
                                + "  ggeo:overlaps($curve1,$surface2), (: false :)\n"
                                + "  ggeo:overlaps($surface2,$curve1), (: false :)\n"
                                + "  ggeo:overlaps($curve9,$surface2), (: false :)\n"
                                + "  ggeo:overlaps($surface2,$curve9), (: false :)\n"
                                + "  ggeo:overlaps($curve12,$surface2), (: false :)\n"
                                + "  ggeo:overlaps($surface2,$curve12), (: false :)\n"
                                + "  ggeo:touches($curve1,$surface2), (: false :)\n"
                                + "  ggeo:touches($surface2,$curve1), (: false :)\n"
                                + "  ggeo:touches($curve8,$surface2), (: true :)\n"
                                + "  ggeo:touches($surface2,$curve8), (: true :)\n"
                                + "  ggeo:touches($curve10,$surface2), (: true :)\n"
                                + "  ggeo:touches($surface2,$curve10) (: true :)"
                                + ")"},

                {"Surface/Surface",
                        booleans(false, false, false, true, false, true, false,
                                false, false, false, false, false, false, false,
                                true, false, true, true, true, false, false,
                                false, false, true, false, false, false, true,
                                false, true, false),
                        COMMON_QUERY_PART + "return (\n"
                                + "  ggeo:contains($surface1,$surface2), (: false :)\n"
                                + "  ggeo:contains($surface2,$surface1), (: false :)\n"
                                + "  ggeo:contains($surface3,$surface4), (: false :)\n"
                                + "  ggeo:contains($surface4,$surface3), (: true :)\n"
                                + "  ggeo:contains($surface7,$surface4), (: false :)\n"
                                + "  ggeo:contains($surface4,$surface7), (: true :)\n"
                                + "  ggeo:contains($surface5,$surface4), (: false :)\n"
                                + "  ggeo:contains($surface4,$surface5), (: false :)\n"
                                + "  ggeo:crosses($surface1,$surface2), (: false :)\n"
                                + "  ggeo:crosses($surface2,$surface1), (: false :)\n"
                                + "  ggeo:crosses($surface7,$surface4), (: false :)\n"
                                + "  ggeo:crosses($surface4,$surface7), (: false :)\n"
                                + "  ggeo:crosses($surface5,$surface4), (: false :)\n"
                                + "  ggeo:equals($surface4,$surface5), (: false :)\n"
                                + "  ggeo:equals($surface5,$surface6), (: true :)\n"
                                + "  ggeo:intersects($surface1,$surface2), (: false :)\n"
                                + "  ggeo:intersects($surface7,$surface3), (: true :)\n"
                                + "  ggeo:intersects($surface5,$surface4), (: true :)\n"
                                + "  ggeo:isDisjoint($surface1,$surface2), (: true :)\n"
                                + "  ggeo:isDisjoint($surface7,$surface3), (: false :)\n"
                                + "  ggeo:isDisjoint($surface5,$surface4), (: false :)\n"
                                + "  ggeo:isWithin($surface1,$surface2), (: false :)\n"
                                + "  ggeo:isWithin($surface4,$surface3), (: false :)\n"
                                + "  ggeo:isWithin($surface3,$surface4), (: true :)\n"
                                + "  ggeo:overlaps($surface1,$surface2), (: false :)\n"
                                + "  ggeo:overlaps($surface3,$surface7), (: false :)\n"
                                + "  ggeo:overlaps($surface5,$surface6), (: false :)\n"
                                + "  ggeo:overlaps($surface4,$surface5), (: true :)\n"
                                + "  ggeo:touches($surface1,$surface2), (: false :)\n"
                                + "  ggeo:touches($surface3,$surface7), (: true :)\n"
                                + "  ggeo:touches($surface5,$surface4) (: false :)"
                                + ")"},

                {"Point / multiple individual points", booleans(false, true),
                        COMMON_QUERY_PART
                                + "let $multiplePoints := ($point1,$point2,$point3)\n"
                                + "return (\n"
                                + "  ggeo:intersects($point1,$multiplePoints,true()), (: false :)"
                                + "  ggeo:intersects($point1,$multiplePoints,false()) (: true :)"
                                + ")"},

                {"Point / multiple individual points", booleans(false, true),
                        COMMON_QUERY_PART
                                + "let $multiplePoints := ($point3,$point2,$point1)\n"
                                + "return (\n"
                                + "  ggeo:intersects($point1,$multiplePoints,true()), (: false :)"
                                + "  ggeo:intersects($point1,$multiplePoints,false()) (: true :)"
                                + ")"},

                {"Validation - all tests", strings("VVV", "VVV", "VVV"),
                        COMMON_QUERY_PART + "\n" + "return (\n"
                                + "  ggeo:validate($surface4), (: VVV :)\n"
                                + "  ggeo:validate($curve1), (: VVV :)\n"
                                + "  ggeo:validate($point1) (: VVV :)" + ")"},

                {"Validation - ring orientation", strings("FSS", "FSS"),
                        COMMON_QUERY_PART + "\n" + "return (\n"
                                + " ggeo:validate($surface8,'100'), (: FSS - exterior ring is oriented clockwise :) \n"
                                + " ggeo:validate($surface9,'100') (: FSS - interior ring is oriented counter-clockwise :)"
                                + ")"},

                {"Validation - repetition ", strings("VVF", "VVF", "VVV"),
                        COMMON_QUERY_PART + "\n" + "return (\n"
                                + "  ggeo:validate($surface10), (: VVF - doppelte position :) \n"
                                + "  ggeo:validate($curve14), (: VVF - doppelte position :) \n"
                                + "  ggeo:validate($curve15) (: VVV :)" + ")"},

                {"Validation - connectedness ", strings("VFV"),
                        COMMON_QUERY_PART + "\n" + "return (\n"
                                + "  ggeo:validate($surface11) (: VFV - fourth patch is not connected :) \n"
                                + ")"},

                {"Validation - connectedness ", strings("VFV"),
                        COMMON_QUERY_PART + "\n" + "return (\n"
                                + "  ggeo:validate($surface12) (: VFV - the two patches only touch in a single point and are therefore not connected :) \n"
                                + ")"},

                {"Validation - connectedness ", strings("VVV"),
                        COMMON_QUERY_PART + "\n" + "return (\n"
                                + "  ggeo:validate($surface13) (: VVV - same surface as s11 but only using the first three patches, which are connected :) \n"
                                + ")"},

                {"Validation - connectedness ", strings("VFV"),
                        COMMON_QUERY_PART + "\n" + "return (\n"
                                + "  ggeo:validate($surface14) (: VFV - patch 1 is connected to patch 2, patch 3 is connected to patch 4, but patches 1/2 are not connected to patches 3/4 :) \n"
                                + ")"},

                {"Validation - crs orientation", strings("VSS", "FSS", "VSS", "FSS"),
                        COMMON_QUERY_PART + "\n" + "return (\n"
                                + " ggeo:validate($surface15,'100'), (: VSS - exterior ring is oriented counter-clockwise with right handed CRS:)"
                                + " ggeo:validate($surface16,'100'), (: FSS - exterior ring is oriented clockwise with right handed CRS:)"
                                + " ggeo:validate($surface17,'100'), (: VSS - exterior ring is oriented clockwise with left handed CRS:)"
                                + " ggeo:validate($surface18,'100') (: FSS - exterior ring is oriented counter-clockwise with left handed CRS:)"
                                + ")"},

                {"Map use ", strings("MULTIPOINT ((0 0), (1 1))"),
                        COMMON_QUERY_PART + "\n"
                                + "let $multiplePoints := ($point3,$point2,$point1)\r\n"
                                + "let $geometryMap := map:merge(\r\n"
                                + "  for $x in $multiplePoints\r\n"
                                + "  return map { $x/@gml:id : ggeo:parseGeometry($x) }\r\n"
                                + ")\r\n" + "\r\n"
                                + "let $tnunion := ggeo:union(for-each($multiplePoints/@gml:id,$geometryMap))\r\n"
                                + "return string($tnunion)"},

                {"Basic test",
                        booleans(true, true, true, true, true),
                        "import module namespace ggeo = 'de.interactive_instruments.etf.bsxm.GmlGeoX';\n"
                                + "declare namespace gml = 'http://www.opengis.net/gml/3.2';\n"
                                + "\n"
                                + "let $geom := /*/*/*\n"
                                + "let $dummy := for $g in $geom\n"
                                + " return ggeo:index($g,$g/@gml:id,$g)\n"
                                + "let $geoms := for $g in $geom return ggeo:getGeometry($g/@gml:id,$g)\n"
                                + "return (\n"
                                + "  count(ggeo:search(4,2.4,8,8.5))=12,\n"
                                + "  count(ggeo:search(0,0,1,1))=15,\n"
                                + "  contains(ggeo:search(0,0,1,1)[@gml:id='p1']/gml:pos[1],'1 1'),\n"
                                + "  ggeo:isWithin(ggeo:getGeometry('c1',$geom[@gml:id='c1']),$geoms,false()),\n"
                                + "  number(ggeo:envelope($geom[1])[1])=1\n"
                                + ")"}
        };
    }

}
