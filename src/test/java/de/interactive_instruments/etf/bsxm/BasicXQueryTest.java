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

import static org.junit.Assert.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.basex.core.BaseXException;
import org.basex.core.Context;
import org.basex.core.cmd.CreateDB;
import org.basex.core.cmd.DropDB;
import org.basex.core.cmd.XQuery;
import org.custommonkey.xmlunit.Diff;
import org.junit.Test;

/**
 * @author Johannes Echterhoff (echterhoff <at> interactive-instruments <dot> de)
 *
 */
public class BasicXQueryTest {

    private static Context context = new Context();

    public static final String queryDir = "src/test/resources/queries/";
    public static final String xmlDir = "src/test/resources/xml/";
    public static final String resultDir = "build/test-results/test/res/";
    public static final String referenceDir = "src/test/resources/reference/";

    @Test
    public void test_validation() {
        xmlTest("test_geometry_validation.xq", "geometryRelationship/GeometryRelationshipTest.xml");
    }

    @Test
    public void test_isClosed() {
        xmlTest("test_geometry_isClosed.xq", "GeometryIsClosedTest.xml");
    }

    @Test
    public void test_3d() {
        xmlTest("test_geometry_3d.xq");
    }

    @Test
    public void test_3d_indexed() throws BaseXException {
        new DropDB("GmlGeoXUnitTestDB").execute(context);
        new CreateDB("GmlGeoXUnitTestDB", "src/test/resources/xml/3DCoodinates.xml").execute(context);
        xmlTest("test_geometry_3d_indexed.xq");
    }

    @Test
    public void test_SRS_configByGmlGeoX() {
        xmlTest("test_geometry_SRS_configByGmlGeoX.xq");
    }

    private void xmlTest(String xquery) {
        xmlTest(xquery, null);
    }

    private void xmlTest(String xquery, String doc) {

        try {
            String filename = FilenameUtils.getBaseName(xquery) + ".xml";

            String query = new String(Files.readAllBytes(Paths.get(queryDir + xquery)));

            XQuery xq = new XQuery(query);

            if (doc != null) {
                /* The XQuery must declare external variable 'docPath' that expects the path to the input XML.
                 *
                 * Example: declare variable $docPath external := '...'; */
                xq.bind("docPath", xmlDir + doc);
            }

            String queryresult = xq.execute(context);

            String result = resultDir + filename;

            FileUtils.writeStringToFile(new File(result), queryresult, "utf-8");

            String reference = referenceDir + filename;

            similar(result, reference);

        } catch (Exception e) {
            fail("Exception occurred while executing test with xquery '" + xquery + "': " + e.getMessage());
        }
    }

    private void similar(String xmlFileName, String referenceXmlFileName) {
        String myControlXML = null;
        String myTestXML = null;
        try {
            myControlXML = readFile(referenceXmlFileName);
        } catch (Exception e) {
            fail("Could not read " + referenceXmlFileName);
        }
        try {
            myTestXML = readFile(xmlFileName);
        } catch (Exception e) {
            fail("Could not read " + xmlFileName);
        }
        try {
            Diff myDiff = new Diff(myControlXML, myTestXML);
            assertTrue("XML: " + xmlFileName + " similar to " + referenceXmlFileName + " - " + myDiff.toString(),
                    myDiff.similar());
        } catch (Exception e) {
            fail("Could not compare " + xmlFileName + " and " + referenceXmlFileName);
        }
    }

    private String readFile(String fileName) throws Exception {
        InputStream stream = new FileInputStream(new File(fileName));

        Writer writer = new StringWriter();
        char[] buffer = new char[1024];
        Reader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
            int n;
            while ((n = reader.read(buffer)) != -1) {
                writer.write(buffer, 0, n);
            }
        } finally {
            if (reader != null)
                reader.close();
        }
        return writer.toString();
    }
}