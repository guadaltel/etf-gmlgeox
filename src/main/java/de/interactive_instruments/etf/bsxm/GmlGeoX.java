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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.math.BigInteger;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import nl.vrom.roo.validator.core.ValidatorContext;
import nl.vrom.roo.validator.core.ValidatorMessage;
import nl.vrom.roo.validator.core.dom4j.handlers.GeometryElementHandler;

import com.github.davidmoten.rtree.geometry.Geometries;
import com.google.common.base.Joiner;

import org.apache.commons.io.FileUtils;
import org.basex.api.dom.BXElem;
import org.basex.api.dom.BXNode;
import org.basex.data.Data;
import org.basex.query.QueryException;
import org.basex.query.QueryModule;
import org.basex.query.value.Value;
import org.basex.query.value.item.Jav;
import org.basex.query.value.node.ANode;
import org.basex.query.value.node.DBNode;
import org.basex.query.value.seq.Empty;
import org.basex.util.InputInfo;
import org.deegree.commons.xml.XMLParsingException;
import org.deegree.cs.CRSCodeType;
import org.deegree.cs.persistence.CRSManager;
import org.deegree.geometry.Geometry;
import org.dom4j.io.SAXReader;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.util.GeometryExtracter;
import org.locationtech.jts.operation.union.CascadedPolygonUnion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import de.interactive_instruments.IFile;
import de.interactive_instruments.IoUtils;
import de.interactive_instruments.properties.PropertyUtils;

/**
 * This module supports the validation of geometries as well as computing the spatial relationship between geometries.
 * <p>
 * NOTE 1: the validation and spatial relationship methods only support specific sets of geometry types - please see the documentation of the respective methods for details on which geometry types are supported.
 * </p>
 *
 * @author Johannes Echterhoff (echterhoff at interactive-instruments dot de)
 *
 */
public class GmlGeoX extends QueryModule {

    public static final String NS = "de.interactive_instruments.etf.bsxm.GmlGeoX";
    public static final String PREFIX = "ggeo";

    public static final String ETF_GMLGEOX_SRSCONFIG_DIR = "etf.gmlgeox.srsconfig.dir";

    public enum SpatialRelOp {
        CONTAINS, CROSSES, EQUALS, INTERSECTS, ISDISJOINT, ISWITHIN, OVERLAPS, TOUCHES
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(GmlGeoX.class);

    private static final Pattern INTERSECTIONPATTERN = Pattern.compile("[0-2\\*TF]{9}");

    // Byte comparisons
    private static final byte[] srsNameB = new String("srsName").getBytes();

    private static final byte[] boundedByB = new String("boundedBy").getBytes();

    private static final byte[] envelopeB = new String("Envelope").getBytes();

    protected final GmlGeoXUtils geoutils = new GmlGeoXUtils(this);

    private final Set<String> gmlGeometries = new TreeSet<String>();

    private static final boolean debug = LOGGER.isDebugEnabled();

    private GeometryManager mgr = null;

    private int count = 0;
    private int count2 = 0;

    public GmlGeoX() throws QueryException {

        logMemUsage("GmlGeoX#init");

        // default geometry types for which validation is performed
        registerGmlGeometry("Point");
        registerGmlGeometry("Polygon");
        registerGmlGeometry("Surface");
        registerGmlGeometry("Curve");
        registerGmlGeometry("LinearRing");

        registerGmlGeometry("MultiPoint");
        registerGmlGeometry("MultiPolygon");
        registerGmlGeometry("MultiGeometry");
        registerGmlGeometry("MultiSurface");
        registerGmlGeometry("MultiCurve");

        registerGmlGeometry("Ring");
        registerGmlGeometry("LineString");

        final ClassLoader cl = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
            if (CRSManager.get("default") == null || CRSManager.get("default").getCRSByCode(
                    CRSCodeType.valueOf("http://www.opengis.net/def/crs/EPSG/0/5555")) == null) {
                loadGmlGeoXSrsConfiguration();
            }
        } finally {
            Thread.currentThread().setContextClassLoader(cl);
        }
    }

    private void loadGmlGeoXSrsConfiguration() throws QueryException {
        final String srsConfigDirPath = PropertyUtils.getenvOrProperty(ETF_GMLGEOX_SRSCONFIG_DIR, null);
        final CRSManager crsMgr = new CRSManager();
        // If the configuration for EPSG 5555 can be accessed, the CRSManger is already configured by
        // the test driver.
        if (srsConfigDirPath != null) {
            final IFile srsConfigDirectory = new IFile(srsConfigDirPath, ETF_GMLGEOX_SRSCONFIG_DIR);

            try {
                srsConfigDirectory.expectDirIsWritable();
                crsMgr.init(srsConfigDirectory);
            } catch (Exception e) {
                throw new QueryException(
                        "Could not load SRS configuration files from directory referenced from GmlGeoX property '"
                                + ETF_GMLGEOX_SRSCONFIG_DIR + "'. Reference is: " + srsConfigDirPath
                                + " Exception message is: " + e.getMessage());
            }
        } else {
            try {
                /* We use the same folder each time an instance of GmlGeoX is created. The configuration files will not be deleted upon exit. That shouldn't be a problem since we always use the same folder. */
                final String tempDirPath = System.getProperty("java.io.tmpdir");
                final File tempDir = new File(tempDirPath, "gmlGeoXSrsConfig");

                if (tempDir.exists()) {
                    FileUtils.deleteQuietly(tempDir);
                }
                tempDir.mkdirs();

                IoUtils.copyResourceToFile(this, "/srsconfig/default.xml", new IFile(tempDir, "default.xml"));
                IoUtils.copyResourceToFile(this, "/srsconfig/deegree/d3/config/ntv2/beta2007.gsb",
                        new IFile(tempDir, "deegree/d3/config/ntv2/beta2007.gsb"));
                IoUtils.copyResourceToFile(this, "/srsconfig/deegree/d3/parser-files.xml",
                        new IFile(tempDir, "deegree/d3/parser-files.xml"));
                IoUtils.copyResourceToFile(this, "/srsconfig/deegree/d3/config/crs-definitions.xml",
                        new IFile(tempDir, "deegree/d3/config/crs-definitions.xml"));
                IoUtils.copyResourceToFile(this, "/srsconfig/deegree/d3/config/datum-definitions.xml",
                        new IFile(tempDir, "deegree/d3/config/datum-definitions.xml"));
                IoUtils.copyResourceToFile(this, "/srsconfig/deegree/d3/config/ellipsoid-definitions.xml",
                        new IFile(tempDir, "deegree/d3/config/ellipsoid-definitions.xml"));
                IoUtils.copyResourceToFile(this, "/srsconfig/deegree/d3/config/pm-definitions.xml",
                        new IFile(tempDir, "deegree/d3/config/pm-definitions.xml"));
                IoUtils.copyResourceToFile(this, "/srsconfig/deegree/d3/config/projection-definitions.xml",
                        new IFile(tempDir, "deegree/d3/config/projection-definitions.xml"));
                IoUtils.copyResourceToFile(this, "/srsconfig/deegree/d3/config/transformation-definitions.xml",
                        new IFile(tempDir, "deegree/d3/config/transformation-definitions.xml"));

                crsMgr.init(tempDir);
            } catch (IOException e) {
                throw new QueryException(
                        "Exception occurred while extracting the SRS configuration files provided by GmlGeoX to a temporary "
                                + "directory and loading them from there. Exception message is: "
                                + e.getMessage());
            }
        }
    }

    /**
     * Loads SRS configuration files from the given directory, to be used when looking up SRS names for creating geometry objects.
     *
     * @param configurationDirectoryPathName
     *            Path to a directory that contains SRS configuration files
     * @throws QueryException
     *             in case that the SRS configuration directory does not exist, is not a directory, cannot be read, or an exception occurred while loading the configuration files
     */
    @Requires(Permission.NONE)
    public void configureSpatialReferenceSystems(final String configurationDirectoryPathName) throws QueryException {
        final ClassLoader cl = Thread.currentThread().getContextClassLoader();
        try {
            final File configurationDirectory = new File(configurationDirectoryPathName);
            if (!configurationDirectory.exists() || !configurationDirectory.isDirectory()
                    || !configurationDirectory.canRead()) {
                throw new IllegalArgumentException(
                        "Given path name does not identify a directory that exists and that can be read.");
            } else {
                Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
                CRSManager crsMgr = new CRSManager();
                crsMgr.init(configurationDirectory);
            }

        } catch (Exception e) {
            throw new QueryException(e);
        } finally {
            Thread.currentThread().setContextClassLoader(cl);
        }
    }

    /**
     * Calls the {@link #validate(ANode, String)} method, with <code>null</code> as the bitmask, resulting in a validation with all tests enabled.
     * <p>
     * See the documentation of the {@link #validate(ANode, String)} method for a description of the supported geometry types.
     *
     * @param node
     *            Node
     * @return a mask with the test results, encoded as characters - one at each position (1-based index) of the available tests. 'V' indicates that the test passed, i.e. that the geometry is valid according to that test. 'F' indicates that the test failed. 'S' indicates that the test was skipped. Example: the string 'SVF' shows that the first test was skipped, while the second test passed and the third failed.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    public String validate(ANode node) throws QueryException {
        return this.validate(node, null);
    }

    /**
     * Validates the given (GML geometry) node.
     * <p>
     * By default validation is only performed for the following GML geometry elements: Point, Polygon, Surface, Curve, LinearRing, MultiPolygon, MultiGeometry, MultiSurface, MultiCurve, Ring, and LineString. The set of GML elements to validate can be modified via the following methods: {@link #registerGmlGeometry(String)}, {@link #unregisterGmlGeometry(String)}, and {@link #unregisterAllGmlGeometries()}. These methods are also available for XQueries.
     * <p>
     * The validation tasks to perform can be specified via the given mask. The mask is a simple string, where the character '1' at the position of a specific test (assuming a 1-based index) specifies that the test shall be performed. If the mask does not contain a character at the position of a specific test (because the mask is empty or the length is smaller than the position), then the test will be executed.
     * <p>
     * The following tests are available:
     * <p>
     * <table summary="Available tests">
     * <tr>
     * <th>Position</th>
     * <th>Test Name</th>
     * <th>Description</th>
     * </tr>
     * <tr>
     * <td>1</td>
     * <td>General Validation</td>
     * <td>This test validates the given geometry using the validation functionality of both deegree and JTS.</td>
     * </tr>
     * <tr>
     * <td>2</td>
     * <td>Polygon Patch Connectivity</td>
     * <td>Checks that multiple polygon patches within a single surface are connected.</td>
     * </tr>
     * <tr>
     * <td>3</td>
     * <td>Repetition of Position in CurveSegments</td>
     * <td>Checks that consecutive positions within a CurveSegment are not equal.</td>
     * </tr>
     * </table>
     * <p>
     * Examples:
     * <ul>
     * <li>The mask '010' indicates that only the 'Polygon Patch Connectivity' test shall be performed.</li>
     * <li>The mask '1' indicates that all tests shall be performed (because the first one is set to true/'1' and nothing is said for the other tests).</li>
     * <li>The mask '0' indicates that all except the first test shall be performed.
     * </ul>
     *
     * @param node
     *            the GML geometry to validate
     * @param testMask
     *            test mask
     * @return a mask with the test results, encoded as characters - one at each position (1-based index) of the available tests. 'V' indicates that the test passed, i.e. that the geometry is valid according to that test. 'F' indicates that the test failed. 'S' indicates that the test was skipped. Example: the string 'SVF' shows that the first test was skipped, while the second test passed and the third failed.
     * @throws QueryException
     */
    public String validate(ANode node, String testMask) throws QueryException {
        ValidationReport vr = this.executeValidate(node, testMask);
        return vr.getValidationResult();
    }

    public Element validateAndReport(ANode node) throws QueryException {
        return validateAndReport(node, null);
    }

    /**
     * @see #executeValidate(ANode, String)
     * @param node
     *            Node
     * @param testMask
     *            test mask
     * @return a DOM element like the following:
     *
     *         <pre>
     *         {@code
     *         <ggeo:ValidationResult xmlns:ggeo="de.interactive_instruments.etf.bsxm.GmlGeoX">
     *           <ggeo:isValid>false</ggeo:isValid>
     *           <ggeo:result>VFV</ggeo:result>
     *           <ggeo:message type="NOTICE">Detected GML standard version: GML3.2.</ggeo:message>
     *           <ggeo:message type="ERROR">Invalid surface (gml:id: s14). The patches of the surface are not connected.</ggeo:message>
     *         </ggeo:ValidationResult>}
     *         </pre>
     *
     *         Where:
     *         <ul>
     *         <li>ggeo:isValid - contains the boolean value indicating if the object passed all tests (defined by the testMask).</li>
     *         <li>ggeo:result - contains a string that is a mask with the test results, encoded as characters - one at each position (1-based index) of the available tests. 'V' indicates that the test passed, i.e. that the geometry is valid according to that test. 'F' indicates that the test failed. 'S' indicates that the test was skipped. Example: the string 'SVF' shows that the first test was skipped, while the second test passed and the third failed</li>
     *         <li>ggeo:message (one for each message produced during validation) contains:
     *         <ul>
     *         <li>an XML attribute 'type' that indicates the severity level of the message ('FATAL', 'ERROR', 'WARNING', or 'NOTICE')</li>
     *         <li>the actual validation message as text content</li>
     *         </ul>
     *         </ul>
     * @throws QueryException
     */
    public Element validateAndReport(ANode node, String testMask) throws QueryException {

        ValidationReport vr = this.executeValidate(node, testMask);

        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        docFactory.setNamespaceAware(true);

        DocumentBuilder docBuilder;

        try {
            docBuilder = docFactory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new QueryException(e);
        }

        // root elements
        Document doc = docBuilder.newDocument();

        Element root = doc.createElementNS(NS, PREFIX + ":ValidationResult");
        doc.appendChild(root);

        Element isValid = doc.createElementNS(NS, PREFIX + ":isValid");
        isValid.setTextContent(vr.isValid() ? "true" : "false");

        root.appendChild(isValid);

        Element result = doc.createElementNS(NS, PREFIX + ":result");
        result.setTextContent(vr.getValidationResult());

        root.appendChild(result);

        for (ValidatorMessage vm : vr.getValidatorMessages()) {

            Element msg = doc.createElementNS(NS, PREFIX + ":message");
            root.appendChild(msg);

            msg.setAttribute("type", vm.getType().toString());
            msg.setTextContent(vm.getMessage());
        }

        return root;
    }

    /**
     * Validates the given (GML geometry) node.
     * <p>
     * By default validation is only performed for the following GML geometry elements: Point, Polygon, Surface, Curve, LinearRing, MultiPolygon, MultiGeometry, MultiSurface, MultiCurve, Ring, and LineString. The set of GML elements to validate can be modified via the following methods: {@link #registerGmlGeometry(String)}, {@link #unregisterGmlGeometry(String)}, and {@link #unregisterAllGmlGeometries()}. These methods are also available for XQueries.
     * <p>
     * The validation tasks to perform can be specified via the given mask. The mask is a simple string, where the character '1' at the position of a specific test (assuming a 1-based index) specifies that the test shall be performed. If the mask does not contain a character at the position of a specific test (because the mask is empty or the length is smaller than the position), then the test will be executed.
     * <p>
     * The following tests are available:
     * <p>
     * <table>
     * <tr>
     * <th>Position</th>
     * <th>Test Name</th>
     * <th>Description</th>
     * </tr>
     * <tr>
     * <td>1</td>
     * <td>General Validation</td>
     * <td>This test validates the given geometry using the validation functionality of both deegree and JTS.</td>
     * </tr>
     * <tr>
     * <td>2</td>
     * <td>Polygon Patch Connectivity</td>
     * <td>Checks that multiple polygon patches within a single surface are connected.</td>
     * </tr>
     * <tr>
     * <td>3</td>
     * <td>Repetition of Position in CurveSegments</td>
     * <td>Checks that consecutive positions within a CurveSegment are not equal.</td>
     * </tr>
     * </table>
     * <p>
     * Examples:
     * <ul>
     * <li>The mask '010' indicates that only the 'Polygon Patch Connectivity' test shall be performed.</li>
     * <li>The mask '1' indicates that all tests shall be performed (because the first one is set to true/'1' and nothing is said for the other tests).</li>
     * <li>The mask '0' indicates that all except the first test shall be performed.
     * </ul>
     *
     * @param node
     *            the GML geometry to validate
     * @return a validation report, with the validation result and validation message (providing further details about any errors). The validation result is encoded as a sequence of characters - one at each position (1-based index) of the available tests. 'V' indicates that the test passed, i.e. that the geometry is valid according to that test. 'F' indicates that the test failed. 'S' indicates that the test was skipped. Example: the string 'SVF' shows that the first test was skipped, while the second test passed and the third failed.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    ValidationReport executeValidate(ANode node, String testMask) throws QueryException {

        try {

            // determine which tests to execute
            boolean isTestGeonovum, isTestPolygonPatchConnectivity, isTestRepetitionInCurveSegments;

            if (testMask == null) {

                isTestGeonovum = true;
                isTestPolygonPatchConnectivity = true;
                isTestRepetitionInCurveSegments = true;

            } else {
                isTestGeonovum = testMask.length() >= 1 && testMask.charAt(0) == '1';
                isTestPolygonPatchConnectivity = testMask.length() >= 2 && testMask.charAt(1) == '1';
                isTestRepetitionInCurveSegments = testMask.length() >= 3 && testMask.charAt(2) == '1';
            }

            boolean isValidGeonovum = false;
            boolean polygonPatchesAreConnected = false;
            boolean noRepetitionInCurveSegment = false;

            String srsName = determineSrsName(node);

            BXNode elem = node.toJava();

            List<ValidatorMessage> validationMessages = new ArrayList<ValidatorMessage>();
            // ================
            // Geonovum validation (deegree and JTS validation)

            if (isTestGeonovum) {

                ValidatorContext ctx = new ValidatorContext();

                GeometryElementHandler handler = new GeometryElementHandler(ctx, null, srsName);
                /* configure handler with GML geometries specified through this class */
                handler.unregisterAllGmlGeometries();
                for (String additionalGmlElementName : gmlGeometries) {
                    handler.registerGmlGeometry(additionalGmlElementName);
                }

                SAXReader saxReader = new SAXReader();
                saxReader.setDefaultHandler(handler);

                final InputStream stream = geoutils.nodeToInputStream(elem);
                saxReader.read(stream);

                isValidGeonovum = ctx.isSuccessful();

                if (!isValidGeonovum) {
                    validationMessages.addAll(ctx.getMessages());
                }
            }

            if (isTestPolygonPatchConnectivity || isTestRepetitionInCurveSegments) {

                ValidatorContext ctx = new ValidatorContext();
                SecondaryGeometryElementValidationHandler handler = new SecondaryGeometryElementValidationHandler(
                        isTestPolygonPatchConnectivity, isTestRepetitionInCurveSegments, ctx, srsName, this);

                /* configure handler with GML geometries specified through this class */
                handler.unregisterAllGmlGeometries();
                for (String additionalGmlElementName : gmlGeometries) {
                    handler.registerGmlGeometry(additionalGmlElementName);
                }

                SAXReader saxReader = new SAXReader();
                saxReader.setDefaultHandler(handler);

                final InputStream stream = geoutils.nodeToInputStream(elem);
                saxReader.read(stream);

                // ================
                // Test: polygon patches of a surface are connected
                if (isTestPolygonPatchConnectivity) {
                    polygonPatchesAreConnected = handler.arePolygonPatchesConnected();
                }

                // ================
                // Test: point repetition in curve segment
                if (isTestRepetitionInCurveSegments) {
                    noRepetitionInCurveSegment = handler.isNoRepetitionInCurveSegments();
                }

                if (!polygonPatchesAreConnected || !noRepetitionInCurveSegment) {
                    validationMessages.addAll(ctx.getMessages());
                }
            }

            // combine results
            StringBuilder sb = new StringBuilder();

            if (!isTestGeonovum) {
                sb.append("S");
            } else if (isValidGeonovum) {
                sb.append("V");
            } else {
                sb.append("F");
            }

            if (!isTestPolygonPatchConnectivity) {
                sb.append("S");
            } else if (polygonPatchesAreConnected) {
                sb.append("V");
            } else {
                sb.append("F");
            }

            if (!isTestRepetitionInCurveSegments) {
                sb.append("S");
            } else if (noRepetitionInCurveSegment) {
                sb.append("V");
            } else {
                sb.append("F");
            }

            return new ValidationReport(sb.toString(), validationMessages);

        } catch (Exception e) {
            throw new QueryException(e);
        }
    }

    /**
     * Tests if the first geometry contains the second geometry.
     * <p>
     * See {{@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
     *
     * @param arg1
     *            represents the first geometry, encoded as a GML geometry element
     * @param arg2
     *            represents the second geometry, encoded as a GML geometry element
     * @return <code>true</code> if the first geometry contains the second one, else <code>false</code>.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public boolean contains(Object arg1, Object arg2) throws QueryException {

        return performSpatialRelationshipOperation(arg1, arg2, SpatialRelOp.CONTAINS);
    }

    /**
     * Tests if one geometry contains a list of geometries. Whether a match is required for all or just one of these is controlled via parameter.
     * <p>
     * See {{@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
     *
     * @param arg1
     *            represents the first geometry, encoded as a GML geometry element
     * @param arg2
     *            represents a list of geometries, encoded as a GML geometry element
     * @param matchAll
     *            <code>true</code> if arg1 must fulfill the spatial relationship for all geometries in arg2, else <code>false</code>
     * @return <code>true</code> if the conditions are met, else <code>false</code>. <code>false</code> will also be returned if arg2 is empty.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public boolean contains(Object arg1, Object arg2, boolean matchAll) throws QueryException {

        return performSpatialRelationshipOperation(arg1, arg2, SpatialRelOp.CONTAINS, matchAll);
    }

    /**
     * Tests if the first geometry crosses the second geometry.
     * <p>
     * See {{@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
     *
     * @param arg1
     *            represents the first geometry, encoded as a GML geometry element
     * @param arg2
     *            represents the second geometry, encoded as a GML geometry element
     * @return <code>true</code> if the first geometry crosses the second one, else <code>false</code>.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public boolean crosses(Object arg1, Object arg2) throws QueryException {

        return performSpatialRelationshipOperation(arg1, arg2, SpatialRelOp.CROSSES);
    }

    /**
     * Tests if one geometry crosses a list of geometries. Whether a match is required for all or just one of these is controlled via parameter.
     * <p>
     * See {{@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
     *
     * @param arg1
     *            represents the first geometry, encoded as a GML geometry element
     * @param arg2
     *            represents a list of geometries, encoded as a GML geometry element
     * @param matchAll
     *            <code>true</code> if arg1 must fulfill the spatial relationship for all geometries in arg2, else <code>false</code>
     * @return <code>true</code> if the conditions are met, else <code>false</code>. <code>false</code> will also be returned if arg2 is empty.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public boolean crosses(Object arg1, Object arg2, boolean matchAll) throws QueryException {

        return performSpatialRelationshipOperation(arg1, arg2, SpatialRelOp.CROSSES, matchAll);
    }

    /**
     * Tests if the first geometry equals the second geometry.
     * <p>
     * See {{@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
     *
     * @param arg1
     *            represents the first geometry, encoded as a GML geometry element
     * @param arg2
     *            represents the second geometry, encoded as a GML geometry element
     * @return <code>true</code> if the first geometry equals the second one, else <code>false</code>.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public boolean equals(Object arg1, Object arg2) throws QueryException {

        return performSpatialRelationshipOperation(arg1, arg2, SpatialRelOp.EQUALS);
    }

    /**
     * Tests if one geometry equals a list of geometries. Whether a match is required for all or just one of these is controlled via parameter.
     * <p>
     * See {{@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
     *
     * @param arg1
     *            represents the first geometry, encoded as a GML geometry element
     * @param arg2
     *            represents a list of geometries, encoded as a GML geometry element
     * @param matchAll
     *            <code>true</code> if arg1 must fulfill the spatial relationship for all geometries in arg2, else <code>false</code>
     * @return <code>true</code> if the conditions are met, else <code>false</code>. <code>false</code> will also be returned if arg2 is empty.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public boolean equals(Object arg1, Object arg2, boolean matchAll) throws QueryException {

        return performSpatialRelationshipOperation(arg1, arg2, SpatialRelOp.EQUALS, matchAll);
    }

    /**
     * Tests if the first geometry intersects the second geometry.
     * <p>
     * See {{@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
     *
     * @param arg1
     *            represents the first geometry, encoded as a GML geometry element
     * @param arg2
     *            represents the second geometry, encoded as a GML geometry element
     * @return <code>true</code> if the first geometry intersects the second one, else <code>false</code>.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public boolean intersects(Object arg1, Object arg2) throws QueryException {

        return performSpatialRelationshipOperation(arg1, arg2, SpatialRelOp.INTERSECTS);
    }

    /**
     * Determine the name of the SRS that applies to the given geometry element. The SRS is either defined by the element itself, in the 'srsName', or by the nearest ancestor that either has an 'srsName' attribute or a child element with local name 'boundedBy' (like gml:boundedBy) that itself contains a child element (like gml:Envelope) that has an 'srsName' attribute. NOTE: The underlying query is independent of a specific GML namespace.
     *
     * @param geometryNode
     *            a gml geometry node
     * @return the value of the applicable 'srsName' attribute, if found, otherwise the empty sequence
     */
    @Requires(Permission.NONE)
    @Deterministic
    public String determineSrsName(final ANode geometryNode) {
        String srsName = getSrsName(geometryNode);
        if (srsName == null) {
            // Check ancestors
            for (ANode ancestor = geometryNode.ancestor().next(); ancestor != null; ancestor = ancestor.ancestor().next()) {
                srsName = getSrsName(ancestor);
                if (srsName != null) {
                    return srsName;
                }
            }
        }
        return srsName;
    }

    private String getSrsName(final ANode node) {
        // Firstly, check for the srsName attribute
        for (final ANode attribute : node.attributes()) {
            if (Arrays.equals(srsNameB, attribute.qname().local())) {
                return new String(attribute.string());
            }
        }
        // Check for a boundedBy element
        for (final ANode child : node.children()) {
            if (Arrays.equals(boundedByB, child.qname().local())) {
                for (final ANode envelope : child.children()) {
                    if (Arrays.equals(envelopeB, envelope.qname().local())) {
                        for (final ANode attribute : envelope.attributes()) {
                            if (Arrays.equals(srsNameB, attribute.qname().local())) {
                                return new String(attribute.string());
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    public org.locationtech.jts.geom.Geometry parseGeometry(Value v) throws QueryException {

        try {
            if (v instanceof ANode) {

                ANode node = (ANode) v;

                return geoutils.toJTSGeometry(node);

            } else if (v instanceof Jav && ((Jav) v).toJava() instanceof org.locationtech.jts.geom.Geometry) {

                return (org.locationtech.jts.geom.Geometry) ((Jav) v).toJava();

            } else {
                throw new IllegalArgumentException("First argument is neither a single node nor a JTS geometry.");
            }
        } catch (Exception e) {
            throw new QueryException(e);
        }
    }

    private boolean performSpatialRelationshipOperation(Object arg1, Object arg2, SpatialRelOp op)
            throws QueryException {

        try {

            org.locationtech.jts.geom.Geometry geom1, geom2;

            /* We require that no basex value with multiple items is provided, because the developer must explicitly state the desired match semantics for cases in which one or both arguments is a collection of items. */
            geom1 = geoutils.singleObjectToJTSGeometry(arg1);
            geom2 = geoutils.singleObjectToJTSGeometry(arg2);

            return applySpatialRelationshipOperator(geom1, geom2, op);

        } catch (

        Exception e) {
            throw new QueryException(e);
        }
    }

    private boolean applySpatialRelationshipOperator(org.locationtech.jts.geom.Geometry geom1,
            org.locationtech.jts.geom.Geometry geom2, SpatialRelOp op) {

        switch (op) {
        case CONTAINS:
            return geom1.contains(geom2);
        case CROSSES:
            return geom1.crosses(geom2);
        case EQUALS:
            return geom1.equals(geom2);
        case INTERSECTS:
            return geom1.intersects(geom2);
        case ISDISJOINT:
            return geom1.disjoint(geom2);
        case ISWITHIN:
            return geom1.within(geom2);
        case OVERLAPS:
            return geom1.overlaps(geom2);
        case TOUCHES:
            return geom1.touches(geom2);
        default:
            throw new IllegalArgumentException("Unknown spatial relationship operator: " + op.toString());
        }
    }

    private boolean performSpatialRelationshipOperation(Object arg1, Object arg2, SpatialRelOp op, boolean matchAll)
            throws QueryException {

        try {

            if (arg1 instanceof Empty || arg2 instanceof Empty) {

                return false;

            } else {

                org.locationtech.jts.geom.Geometry geom1, geom2;

                geom1 = geoutils.toJTSGeometry(arg1);
                geom2 = geoutils.toJTSGeometry(arg2);

                List<org.locationtech.jts.geom.Geometry> gc1, gc2;

                gc1 = geoutils.toFlattenedJTSGeometryCollection(geom1);
                gc2 = geoutils.toFlattenedJTSGeometryCollection(geom2);

                boolean allMatch = true;

                outer: for (org.locationtech.jts.geom.Geometry g1 : gc1) {
                    for (org.locationtech.jts.geom.Geometry g2 : gc2) {

                        if (matchAll) {

                            if (applySpatialRelationshipOperator(g1, g2, op)) {
                                /* check the next geometry pair to see if it also satisfies the spatial relationship */
                            } else {
                                allMatch = false;
                                break outer;
                            }

                        } else {

                            if (applySpatialRelationshipOperator(g1, g2, op)) {
                                return true;
                            } else {
                                /* check the next geometry pair to see if it satisfies the spatial relationship */
                            }
                        }
                    }
                }

                if (matchAll) {
                    return allMatch;
                } else {
                    return false;
                }
            }

        } catch (Exception e) {
            throw new QueryException(e);
        }
    }

    /**
     * Tests if one geometry intersects a list of geometries. Whether a match is required for all or just one of these is controlled via parameter.
     * <p>
     * See {{@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
     *
     * @param arg1
     *            represents the first geometry, encoded as a GML geometry element
     * @param arg2
     *            represents a list of geometries, encoded as a GML geometry element
     * @param matchAll
     *            <code>true</code> if arg1 must fulfill the spatial relationship for all geometries in arg2, else <code>false</code>
     * @return <code>true</code> if the conditions are met, else <code>false</code>. <code>false</code> will also be returned if arg2 is empty.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public boolean intersects(Object arg1, Object arg2, boolean matchAll) throws QueryException {

        return performSpatialRelationshipOperation(arg1, arg2, SpatialRelOp.INTERSECTS, matchAll);
    }

    /**
     * Tests if the first geometry is disjoint from the second geometry.
     * <p>
     * See {{@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
     *
     * @param arg1
     *            represents the first geometry, encoded as a GML geometry element
     * @param arg2
     *            represents the second geometry, encoded as a GML geometry element
     * @return <code>true</code> if the first geometry is disjoint from the second one, else <code>false</code>.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public boolean isDisjoint(Object arg1, Object arg2) throws QueryException {

        return performSpatialRelationshipOperation(arg1, arg2, SpatialRelOp.ISDISJOINT);
    }

    /**
     * Tests if one geometry is disjoint to a list of geometries. Whether a match is required for all or just one of these is controlled via parameter.
     * <p>
     * See {{@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
     *
     * @param arg1
     *            represents the first geometry, encoded as a GML geometry element
     * @param arg2
     *            represents a list of geometries, encoded as a GML geometry element
     * @param matchAll
     *            <code>true</code> if arg1 must fulfill the spatial relationship for all geometries in arg2, else <code>false</code>
     * @return <code>true</code> if the conditions are met, else <code>false</code>. <code>false</code> will also be returned if arg2 is empty.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public boolean isDisjoint(Object arg1, Object arg2, boolean matchAll) throws QueryException {

        return performSpatialRelationshipOperation(arg1, arg2, SpatialRelOp.ISDISJOINT, matchAll);
    }

    /**
     * Tests if the first geometry is within the second geometry.
     * <p>
     * See {{@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
     *
     * @param arg1
     *            represents the first geometry, encoded as a GML geometry element
     * @param arg2
     *            represents the second geometry, encoded as a GML geometry element
     * @return <code>true</code> if the first geometry is within the second one, else <code>false</code>.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public boolean isWithin(Object arg1, Object arg2) throws QueryException {

        return performSpatialRelationshipOperation(arg1, arg2, SpatialRelOp.ISWITHIN);
    }

    /**
     * Tests if one geometry is within a list of geometries. Whether a match is required for all or just one of these is controlled via parameter.
     * <p>
     * See {{@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
     *
     * @param arg1
     *            represents the first geometry, encoded as a GML geometry element
     * @param arg2
     *            represents a list of geometries, encoded as a GML geometry element
     * @param matchAll
     *            <code>true</code> if arg1 must fulfill the spatial relationship for all geometries in arg2, else <code>false</code>
     * @return <code>true</code> if the conditions are met, else <code>false</code>. <code>false</code> will also be returned if arg2 is empty.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public boolean isWithin(Object arg1, Object arg2, boolean matchAll) throws QueryException {

        return performSpatialRelationshipOperation(arg1, arg2, SpatialRelOp.ISWITHIN, matchAll);
    }

    /**
     * Tests if the first geometry overlaps the second geometry.
     * <p>
     * See {{@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
     *
     * @param arg1
     *            represents the first geometry, encoded as a GML geometry element
     * @param arg2
     *            represents the second geometry, encoded as a GML geometry element
     * @return <code>true</code> if the first geometry overlaps the second one, else <code>false</code>.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public boolean overlaps(Object arg1, Object arg2) throws QueryException {

        return performSpatialRelationshipOperation(arg1, arg2, SpatialRelOp.OVERLAPS);
    }

    /**
     * Tests if one geometry overlaps a list of geometries. Whether a match is required for all or just one of these is controlled via parameter.
     * <p>
     * See {{@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
     *
     * @param arg1
     *            represents the first geometry, encoded as a GML geometry element
     * @param arg2
     *            represents a list of geometries, encoded as a GML geometry element
     * @param matchAll
     *            <code>true</code> if arg1 must fulfill the spatial relationship for all geometries in arg2, else <code>false</code>
     * @return <code>true</code> if the conditions are met, else <code>false</code>. <code>false</code> will also be returned if arg2 is empty.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public boolean overlaps(Object arg1, Object arg2, boolean matchAll) throws QueryException {

        return performSpatialRelationshipOperation(arg1, arg2, SpatialRelOp.OVERLAPS, matchAll);
    }

    /**
     * Tests if the first geometry touches the second geometry.
     * <p>
     * See {{@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
     *
     * @param arg1
     *            represents the first geometry, encoded as a GML geometry element
     * @param arg2
     *            represents the second geometry, encoded as a GML geometry element
     * @return <code>true</code> if the first geometry touches the second one, else <code>false</code>.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public boolean touches(Object arg1, Object arg2) throws QueryException {

        return performSpatialRelationshipOperation(arg1, arg2, SpatialRelOp.TOUCHES);
    }

    /**
     * Tests if one geometry touches a list of geometries. Whether a match is required for all or just one of these is controlled via parameter.
     * <p>
     * See {{@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
     *
     * @param arg1
     *            represents the first geometry, encoded as a GML geometry element
     * @param arg2
     *            represents a list of geometries, encoded as a GML geometry element
     * @param matchAll
     *            <code>true</code> if arg1 must fulfill the spatial relationship for all geometries in arg2, else <code>false</code>
     * @return <code>true</code> if the conditions are met, else <code>false</code>. <code>false</code> will also be returned if arg2 is empty.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public boolean touches(Object arg1, Object arg2, boolean matchAll) throws QueryException {

        return performSpatialRelationshipOperation(arg1, arg2, SpatialRelOp.TOUCHES, matchAll);
    }

    /**
     * Adds the name of a GML geometry element to the set of elements for which validation is performed.
     *
     * @param nodeName
     *            name (simple, i.e. without namespace (prefix)) of a GML geometry element to validate.
     */
    @Requires(Permission.NONE)
    public void registerGmlGeometry(String nodeName) {
        gmlGeometries.add(nodeName);
    }

    /**
     * Removes the name of a GML geometry element from the set of elements for which validation is performed.
     *
     * @param nodeName
     *            name (simple, i.e. without namespace (prefix)) of a GML geometry element to remove from validation.
     */
    @Requires(Permission.NONE)
    public void unregisterGmlGeometry(String nodeName) {
        gmlGeometries.remove(nodeName);
    }

    /**
     * Removes all names of GML geometry elements that are currently registered for validation.
     */
    @Requires(Permission.NONE)
    public void unregisterAllGmlGeometries() {
        gmlGeometries.clear();
    }

    /**
     * @return the currently registered GML geometry element names (comma separated)
     */
    @Requires(Permission.NONE)
    public String registeredGmlGeometries() {

        if (gmlGeometries.isEmpty()) {
            return "";
        } else {
            Joiner joiner = Joiner.on(", ").skipNulls();
            return joiner.join(gmlGeometries);
        }
    }

    @Requires(Permission.NONE)
    @Deterministic
    public org.locationtech.jts.geom.Geometry union(Object arg1, Object arg2) throws QueryException {

        try {

            List<org.locationtech.jts.geom.Geometry> geoms = new ArrayList<org.locationtech.jts.geom.Geometry>();

            org.locationtech.jts.geom.Geometry geom1 = geoutils.toJTSGeometry(arg1);
            geoms.add(geom1);

            org.locationtech.jts.geom.Geometry geom2 = geoutils.toJTSGeometry(arg2);
            geoms.add(geom2);

            org.locationtech.jts.geom.GeometryCollection gc = geoutils.toJTSGeometryCollection(geoms, true);

            return gc.union();

        } catch (Exception e) {
            throw new QueryException(e);
        }
    }

    @Requires(Permission.NONE)
    @Deterministic
    public org.locationtech.jts.geom.Geometry union(Object arg) throws QueryException {

        try {

            List<org.locationtech.jts.geom.Geometry> geoms = new ArrayList<org.locationtech.jts.geom.Geometry>();

            org.locationtech.jts.geom.Geometry geom = geoutils.toJTSGeometry(arg);
            geoms.add(geom);

            org.locationtech.jts.geom.GeometryCollection gc = geoutils.toJTSGeometryCollection(geoms, true);

            return gc.union();

        } catch (Exception e) {
            throw new QueryException(e);
        }
    }

    @Requires(Permission.NONE)
    @Deterministic
    public boolean isEmpty(org.locationtech.jts.geom.Geometry geom) {

        if (geom == null || geom.isEmpty()) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Checks if a given object is closed. The object can be a single geometry or a collection of geometries. Only LineStrings and MultiLineStrings are checked.
     *
     * NOTE: Invokes the {@link #isClosed(Object, boolean)} method, with <code>true</code> for the second parameter.
     *
     * @see #isClosed(Object, boolean)
     * @param o
     * @return
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public boolean isClosed(Object o) throws QueryException {
        return isClosed(o, true);
    }

    /**
     * Checks if a given object is closed. The object can be a single geometry or a collection of geometries. Points and MultiPoints are closed by definition (they do not have a boundary). Polygons and MultiPolygons are never closed in 2D, and since operations in 3D are not supported, this method will always return <code>false</code> if a polygon is encountered - unless the parameter onlyCheckCurveGeometries is set to <code>true</code>. LinearRings are closed by definition. The remaining geometry types that will be checked are LineString and MultiLineString. If a (Multi)LineString is not closed, this method will return <code>false</code>.
     *
     * @param o
     *            the geometry object(s) to test, can be a JTS geometry object, collection, and BaseX nodes (that will be converted to JTS geometries)
     * @param onlyCheckCurveGeometries
     *            <code>true</code> if only curve geometries (i.e., for JTS: LineString, LinearRing, and MultiLineString) shall be tested, else <code>false</code> (in this case, the occurrence of polygons will result in the return value <code>false</code>).
     * @return <code>true</code> if the given object - a geometry or collection of geometries - is closed, else <code>false</code>
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public boolean isClosed(Object o, boolean onlyCheckCurveGeometries) throws QueryException {

        try {

            if (o instanceof Empty) {

                return true;

            } else {

                org.locationtech.jts.geom.Geometry geom = geoutils.toJTSGeometry(o);

                List<org.locationtech.jts.geom.Geometry> gc = geoutils.toFlattenedJTSGeometryCollection(geom);

                for (org.locationtech.jts.geom.Geometry g : gc) {

                    if (g instanceof org.locationtech.jts.geom.Point
                            || g instanceof org.locationtech.jts.geom.MultiPoint) {

                        /* points are closed by definition (they do not have a boundary) */

                    } else if (g instanceof org.locationtech.jts.geom.Polygon
                            || g instanceof org.locationtech.jts.geom.MultiPolygon) {

                        /* The JTS FAQ contains the following question and answer:
                         *
                         * Question: Does JTS support 3D operations?
                         *
                         * Answer: JTS does not provide support for true 3D geometry and operations. However, JTS does allow Coordinates to carry an elevation or Z value. This does not provide true 3D support, but does allow "2.5D" uses which are required in some geospatial applications.
                         *
                         * -------
                         *
                         * So, JTS does not support true 3D geometry and operations. Therefore, JTS cannot determine if a surface is closed. deegree does not seem to support this, either. In order for a surface to be closed, it must be a sphere or torus, possibly with holes. A surface in 2D can never be closed. Since we lack the ability to compute in 3D we assume that a (Multi)Polygon is not closed. If we do check geometries other than curves, then we return false. */
                        if (!onlyCheckCurveGeometries) {
                            return false;
                        }

                    } else if (g instanceof org.locationtech.jts.geom.MultiLineString) {

                        org.locationtech.jts.geom.MultiLineString mls = (org.locationtech.jts.geom.MultiLineString) g;
                        if (!mls.isClosed()) {
                            return false;
                        }

                    } else if (g instanceof org.locationtech.jts.geom.LineString) {

                        /* NOTE: LinearRing is a subclass of LineString, and closed by definition */

                        org.locationtech.jts.geom.LineString ls = (org.locationtech.jts.geom.LineString) g;
                        if (!ls.isClosed()) {
                            return false;
                        }

                    } else {
                        // should not happen
                        throw new Exception("Unexpected geometry type encountered: " + g.getClass().getName());
                    }
                }

                // all relevant geometries are closed
                return true;
            }

        } catch (Exception e) {
            throw new QueryException(e);
        }
    }

    /**
     * Identifies the holes contained in the given geometry (can be a Polygon, MultiPolygon, or any other JTS geometry) and returns them as a JTS geometry. If holes were found a union is built, to ensure that the result is a valid JTS Polygon or JTS MultiPolygon. If no holes were found an empty JTS GeometryCollection is returned.
     *
     * @param geom
     *            potentially existing holes will be extracted from this geometry
     * @return A geometry with the holes contained in the given geometry. Can be empty but not <code>null</code>;
     */
    @Requires(Permission.NONE)
    @Deterministic
    public org.locationtech.jts.geom.Geometry holes(org.locationtech.jts.geom.Geometry geom) {

        if (isEmpty(geom)) {

            return geoutils.emptyJTSGeometry();

        } else {

            List<org.locationtech.jts.geom.Polygon> extractedPolygons = new ArrayList<org.locationtech.jts.geom.Polygon>();

            GeometryExtracter.extract(geom, org.locationtech.jts.geom.Polygon.class, extractedPolygons);

            if (extractedPolygons.isEmpty()) {

                return geoutils.emptyJTSGeometry();

            } else {

                // get holes as polygons
                List<org.locationtech.jts.geom.Polygon> holes = new ArrayList<org.locationtech.jts.geom.Polygon>();

                for (org.locationtech.jts.geom.Polygon polygon : extractedPolygons) {

                    // check that polygon has holes
                    if (polygon.getNumInteriorRing() > 0) {

                        // for each hole, convert it to a polygon
                        for (int i = 0; i < polygon.getNumInteriorRing(); i++) {
                            org.locationtech.jts.geom.LineString ls = polygon.getInteriorRingN(i);
                            org.locationtech.jts.geom.Polygon holeAsPolygon = geoutils.toJTSPolygon(ls);
                            holes.add(holeAsPolygon);
                        }
                    }
                }

                if (holes.isEmpty()) {
                    return geoutils.emptyJTSGeometry();
                } else {
                    // create union of holes and return it
                    return CascadedPolygonUnion.union(holes);
                }
            }
        }
    }

    @Requires(Permission.NONE)
    public boolean isValid(ANode node) throws QueryException {

        String validationResult = validate(node);

        if (validationResult.toLowerCase().indexOf('f') > -1) {
            return false;
        } else {
            return true;
        }
    }

    /**
     * Tests if the first geometry relates to the second geometry as defined by the given intersection pattern.
     * <p>
     * See {{@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
     *
     * @param arg1
     *            represents the first geometry, encoded as a GML geometry element
     * @param arg2
     *            represents the second geometry, encoded as a GML geometry element
     * @param intersectionPattern
     *            the pattern against which to check the intersection matrix for the two geometries (IxI,IxB,IxE,BxI,BxB,BxE,ExI,ExB,ExE)
     * @return <code>true</code> if the DE-9IM intersection matrix for the two geometries matches the <code>intersectionPattern</code>, else <code>false</code>.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public boolean relate(Object arg1, Object arg2, String intersectionPattern) throws QueryException {

        checkIntersectionPattern(intersectionPattern);

        try {

            org.locationtech.jts.geom.Geometry geom1, geom2;

            /* We require that no basex value with multiple items is provided, because the developer must explicitly state the desired match semantics for cases in which one or both arguments is a collection of items. */
            geom1 = geoutils.singleObjectToJTSGeometry(arg1);
            geom2 = geoutils.singleObjectToJTSGeometry(arg2);

            return geom1.relate(geom2, intersectionPattern);

        } catch (Exception e) {
            throw new QueryException(e);
        }
    }

    /**
     * Tests if one geometry relates to a list of geometries as defined by the given intersection pattern. Whether a match is required for all or just one of these is controlled via parameter.
     * <p>
     * See {{@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
     *
     * @param arg1
     *            represents the first geometry, encoded as a GML geometry element
     * @param arg2
     *            represents a list of geometries, encoded as a GML geometry
     * @param intersectionPattern
     *            the pattern against which to check the intersection matrix for the geometries (IxI,IxB,IxE,BxI,BxB,BxE,ExI,ExB,ExE)
     * @param matchAll
     *            <code>true</code> if arg1 must fulfill the spatial relationship defined by the <code>intersectionPattern</code> for all geometries in arg2, else <code>false</code>
     * @return <code>true</code> if the conditions are met, else <code>false</code>. <code>false</code> will also be returned if arg2 is empty.
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public boolean relate(Object arg1, Object arg2, String intersectionPattern, boolean matchAll)
            throws QueryException {

        checkIntersectionPattern(intersectionPattern);

        try {

            if (arg1 instanceof Empty || arg2 instanceof Empty) {

                return false;

            } else {

                org.locationtech.jts.geom.Geometry geom1, geom2;

                geom1 = geoutils.toJTSGeometry(arg1);
                geom2 = geoutils.toJTSGeometry(arg2);

                List<org.locationtech.jts.geom.Geometry> gc1, gc2;

                gc1 = geoutils.toFlattenedJTSGeometryCollection(geom1);
                gc2 = geoutils.toFlattenedJTSGeometryCollection(geom2);

                boolean allMatch = true;

                outer: for (org.locationtech.jts.geom.Geometry g1 : gc1) {
                    for (org.locationtech.jts.geom.Geometry g2 : gc2) {

                        if (matchAll) {

                            if (g1.relate(g2, intersectionPattern)) {
                                /* check the next geometry pair to see if it also satisfies the spatial relationship */
                            } else {
                                allMatch = false;
                                break outer;
                            }

                        } else {

                            if (g1.relate(g2, intersectionPattern)) {
                                return true;
                            } else {
                                /* check the next geometry pair to see if it satisfies the spatial relationship */
                            }
                        }
                    }
                }

                if (matchAll) {
                    return allMatch;
                } else {
                    return false;
                }
            }

        } catch (Exception e) {
            throw new QueryException(e);
        }
    }

    private void checkIntersectionPattern(String intersectionPattern) throws QueryException {
        if (intersectionPattern == null) {
            throw new QueryException("intersectionPattern is null.");
        } else {
            final Matcher m = INTERSECTIONPATTERN.matcher(intersectionPattern.trim());
            if (!m.matches()) {
                throw new QueryException(
                        "intersectionPattern does not match the regular expression, which is: [0-2\\\\*TF]{9}");
            }
        }

    }

    /**
     * Computes the intersection between the first and the second geometry.
     * <p>
     * See {{@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
     *
     * @param geometry1
     *            represents the first geometry
     * @param geometry2
     *            represents the second geometry
     * @return the point-set common to the two geometries
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public org.locationtech.jts.geom.Geometry intersection(final Object geometry1, final Object geometry2)
            throws QueryException {
        try {
            if (geometry1 instanceof Empty || geometry2 instanceof Empty) {
                return geoutils.emptyJTSGeometry();
            } else {

                final org.locationtech.jts.geom.Geometry geom1, geom2;
                geom1 = geoutils.toJTSGeometry(geometry1);
                geom2 = geoutils.toJTSGeometry(geometry2);
                return geom1.intersection(geom2);
            }
        } catch (Exception e) {
            throw new QueryException(e);
        }

    }

    /**
     * Computes the envelope of a geometry.
     * <p>
     * See {@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
     *
     * @param geometry
     *            represents the geometry
     * @return The bounding box, an array { x1, y1, x2, y2 }
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public Object[] envelope(Object geometry) throws QueryException {
        try {
            final Envelope env;
            if (geometry instanceof Empty) {
                env = geoutils.emptyJTSGeometry().getEnvelopeInternal();
            } else if (geometry instanceof org.locationtech.jts.geom.Geometry) {
                env = ((org.locationtech.jts.geom.Geometry) geometry).getEnvelopeInternal();
            } else {
                env = geoutils.toJTSGeometry(geometry).getEnvelopeInternal();
            }
            final Object[] res = {env.getMinX(), env.getMinY(), env.getMaxX(), env.getMaxY()};
            return res;
        } catch (Exception e) {
            throw new QueryException(e);
        }

    }

    @Requires(Permission.NONE)
    @Deterministic
    public int pre(Object entry) {
        if (entry instanceof IndexEntry)
            return ((IndexEntry) entry).pre;

        return -1;
    }

    @Requires(Permission.NONE)
    @Deterministic
    public String dbname(Object entry) {
        if (entry instanceof IndexEntry)
            return ((IndexEntry) entry).dbname;

        return null;
    }

    /**
     * Searches the spatial r-tree index for items in the envelope.
     *
     * @param minx
     *            represents the minimum value on the first coordinate axis; a number
     * @param miny
     *            represents the minimum value on the second coordinate axis; a number
     * @param maxx
     *            represents the maximum value on the first coordinate axis; a number
     * @param maxy
     *            represents the maximum value on the second coordinate axis; a number
     * @return the node set of all items in the envelope
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public Object[] search(Object minx, Object miny, Object maxx, Object maxy) throws QueryException {

        try {
            double x1;
            double x2;
            double y1;
            double y2;
            if (minx instanceof Number)
                x1 = ((Number) minx).doubleValue();
            else
                x1 = 0.0;
            if (miny instanceof Number)
                y1 = ((Number) miny).doubleValue();
            else
                y1 = 0.0;
            if (maxx instanceof Number)
                x2 = ((Number) maxx).doubleValue();
            else
                x2 = 0.0;
            if (maxy instanceof Number)
                y2 = ((Number) maxy).doubleValue();
            else
                y2 = 0.0;

            if (mgr == null)
                mgr = new GeometryManager();
            Iterable<IndexEntry> iter = mgr.search(Geometries.rectangle(x1, y1, x2, y2));
            List<DBNode> nodelist = new ArrayList<DBNode>();
            for (IndexEntry entry : iter) {
                Data d = queryContext.resources.database(entry.dbname, new InputInfo("xpath", 0, 0));
                DBNode n = new DBNode(d, entry.pre);
                if (n != null)
                    nodelist.add(n);
            }
            if (debug && ++count % 5000 == 0) {
                logMemUsage("GmlGeoX#search " + count + ". Box: (" + x1 + ", " + y1 + ") (" + x2 + ", " + y2 + ")"
                        + ". Hits: " + nodelist.size());
            }

            return nodelist.toArray();

        } catch (Exception e) {
            throw new QueryException(e);
        }
    }

    /**
     * Returns all items in the spatial r-tree index.
     *
     * @return the node set of all items in the index
     * @throws QueryException
     */
    public Object[] search() throws QueryException {
        try {
            logMemUsage("GmlGeoX#search.start " + count + ".");
            if (mgr == null)
                mgr = new GeometryManager();
            Iterable<IndexEntry> iter = mgr.search();
            List<DBNode> nodelist = new ArrayList<DBNode>();
            for (IndexEntry entry : iter) {
                Data d = queryContext.resources.database(entry.dbname, new InputInfo("xpath", 0, 0));
                DBNode n = new DBNode(d, entry.pre);
                if (n != null)
                    nodelist.add(n);
            }
            logMemUsage("GmlGeoX#search " + count + ". Hits: " + nodelist.size());

            return nodelist.toArray();

        } catch (Exception e) {
            throw new QueryException(e);
        }
    }

    /**
     * Logs memory information if Logger is enabled for the DEBUG level
     *
     * @param progress
     *            status string
     */

    private void logMemUsage(final String progress) {
        if (debug) {
            final MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
            memory.gc();
            LOGGER.debug(progress + ". Memory: " + Math.round(memory.getHeapMemoryUsage().getUsed() / 1048576)
                    + " MB of " + Math.round(memory.getHeapMemoryUsage().getMax() / 1048576) + " MB.");
        }
    }

    /**
     * Set cache size for geometries
     *
     * @param size
     *            the size of the geometry cache; default is 100000
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    public void cacheSize(Object size) throws QueryException {
        if (size instanceof BigInteger) {
            mgr = new GeometryManager(((BigInteger) size).intValue());
        }
    }

    /**
     * Indexes a list of id nodes (gml:id attribute of features) with their GML geometries
     * <p>
     * See {@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
     * <p>
     * Both lists must have equal length.
     *
     * @param pre
     *            represents the pre value of the indexed item node (typically the gml:id of GML feature elements)
     * @param dbname
     *            represents the name of the database that contains the indexed item node (typically the gml:id of GML feature elements)
     * @param id
     *            represents the id string of the item that should be indexed, typically the gml:id of GML feature elements; must be String instances
     * @param geom
     *            represents the GML geometry to index; must be an BXElem instance
     *
     * @deprecated This method does not support 3D indexing, use method {@link GmlGeoX#index(ANode, Object, ANode)} instead. This method is removed in Version 1.3.0
     *
     * @throws QueryException
     */
    @Deprecated
    @Requires(Permission.NONE)
    public void index(Object pre, Object dbname, Object id, Object geom) throws QueryException {
        // TODO remove method GmlGeoX version 1.3.0
        if (mgr == null)
            mgr = new GeometryManager();

        if (pre instanceof BigInteger && dbname instanceof String && (id instanceof BXNode || id instanceof String)
                && (geom instanceof BXElem || geom instanceof org.locationtech.jts.geom.Geometry))
            try {
                IndexEntry entry = new IndexEntry((String) dbname, ((BigInteger) pre).intValue());
                String _id = id instanceof String ? (String) id : ((BXNode) id).getNodeValue();
                org.locationtech.jts.geom.Geometry _geom = geom instanceof BXElem
                        ? geoutils.singleObjectToJTSGeometry(geom) : ((org.locationtech.jts.geom.Geometry) geom);
                Envelope env = _geom.getEnvelopeInternal();
                if (!env.isNull()) {
                    if (env.getHeight() == 0.0 && env.getWidth() == 0.0)
                        mgr.index(entry, Geometries.point(env.getMinX(), env.getMinY()));
                    else
                        mgr.index(entry,
                                Geometries.rectangle(env.getMinX(), env.getMinY(), env.getMaxX(), env.getMaxY()));

                    // add to geometry cache
                    if (_id != null)
                        mgr.put(_id, _geom);
                }

                int size = mgr.indexSize();
                if (size % 5000 == 0)
                    logMemUsage("GmlGeoX#index progress: " + size);

            } catch (final Exception e) {
                throw new IllegalAccessError("The ggeo:index interface changed and 3D coordinate "
                        + "validation is not supported with this method call. "
                        + "Upgrade your Queries: "
                        + "ggeo:index( db:node-pre($F), db:name($F), $F/@gml:id, $geom) ---> "
                        + "ggeo:index( $F, $F/@gml:id, $geom). Error: " + e.getMessage());
            }
    }

    /**
     * Indexes a list of id nodes (gml:id attribute of features) with their GML geometries
     * <p>
     * See {@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
     * <p>
     * Both lists must have equal length.
     *
     * @param node
     *            represents the indexed item node (typically the gml:id of GML feature elements)
     * @param objId
     *            represents the id string of the item that should be indexed, typically the gml:id of GML feature elements; must be String instances
     * @param geometry
     *            represents the GML geometry to index; must be an ANode instance
     *
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    public void index(final ANode node, final Object objId, final ANode geometry) throws QueryException {
        if ((objId instanceof BXNode || objId instanceof String)) {

            if (mgr == null) {
                mgr = new GeometryManager();
            }

            try {
                final org.locationtech.jts.geom.Geometry _geom = geoutils.singleObjectToJTSGeometry(geometry);
                final Envelope env = _geom.getEnvelopeInternal();
                if (!env.isNull()) {
                    final IndexEntry entry = new IndexEntry(node);
                    if (env.getHeight() == 0.0 && env.getWidth() == 0.0) {
                        mgr.index(entry, Geometries.point(env.getMinX(), env.getMinY()));
                    } else {
                        mgr.index(entry,
                                Geometries.rectangle(env.getMinX(), env.getMinY(), env.getMaxX(), env.getMaxY()));
                    }

                    // add to geometry cache
                    final String id = objId instanceof String ? (String) objId : ((BXNode) objId).getNodeValue();
                    if (id != null) {
                        mgr.put(id, _geom);
                    }
                }

                if (debug && mgr.indexSize() % 5000 == 0) {
                    logMemUsage("GmlGeoX#index progress: " + mgr.indexSize());
                }
            } catch (final Exception e) {
                if (e instanceof XMLParsingException) {
                    // otherwise the stacktrace "<< is empty >>" is included
                    throw new QueryException(e.getMessage());
                }
                throw new QueryException(e);
            }
        }
    }

    /**
     * Retrieve the geometry of an item as a JTS geometry. First try the cache and if it is not in the cache construct it from the XML.
     * <p>
     * See {@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
     *
     * @param id
     *            the id for which the geometry should be retrieved, typically a gml:id of a GML feature element; must be a String or BXNode instance
     * @param defgeom
     *            represents the default GML geometry, if the geometry is not cached; must be a BXElem instance
     * @return the geometry of the indexed node, or null if no geometry was found
     *
     * @deprecated renamed method to {@link GmlGeoX#getOrCacheGeometry(Object, Object)}
     *
     * @throws QueryException
     */
    @Deprecated
    @Requires(Permission.NONE)
    @Deterministic
    public org.locationtech.jts.geom.Geometry getGeometry(final Object id, final Object defgeom) throws QueryException {
        return getOrCacheGeometry(id, defgeom);
    }

    /**
     * Retrieve the geometry of an item as a JTS geometry. First try the cache and if it is not in the cache construct it from the XML.
     * <p>
     * See {@link GmlGeoXUtils#toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
     *
     * @param id
     *            the id for which the geometry should be retrieved, typically a gml:id of a GML feature element; must be a String or BXNode instance
     * @param defgeom
     *            represents the default GML geometry, if the geometry is not cached; must be a BXElem instance
     * @return the geometry of the indexed node, or null if no geometry was found
     *
     * @throws QueryException
     */
    @Requires(Permission.NONE)
    @Deterministic
    public org.locationtech.jts.geom.Geometry getOrCacheGeometry(final Object id, final Object defgeom)
            throws QueryException {
        if (debug && ++count2 % 5000 == 0) {
            logMemUsage("GmlGeoX#getGeometry.start " + count2);
        }

        if (mgr == null)
            mgr = new GeometryManager();

        final String idx;
        if (id instanceof String) {
            idx = (String) id;
        } else if (id instanceof BXNode) {
            idx = ((BXNode) id).getNodeValue();
        } else
            throw new QueryException(
                    "Failure to get geometry. An id uses an incorrect type: " + id.getClass().getCanonicalName());

        org.locationtech.jts.geom.Geometry geom = mgr.get(idx);
        if (geom == null) {
            if (!(defgeom instanceof BXElem || defgeom instanceof org.locationtech.jts.geom.Geometry)) {
                throw new QueryException(
                        "Failure to parse geometry. A geometry was not found or uses an incorrect type: "
                                + defgeom.getClass().getCanonicalName());
            }
            try {
                geom = defgeom instanceof BXElem ? geoutils.singleObjectToJTSGeometry(defgeom)
                        : ((org.locationtech.jts.geom.Geometry) defgeom);
                if (geom != null)
                    mgr.put(idx, geom);
            } catch (Exception e) {
                throw new QueryException(e);
            }
            if (debug && mgr.getMissCount() % 10000 == 0) {
                LOGGER.debug("Cache misses: " + mgr.getMissCount() + " of " + mgr.getCount());
            }
        }

        if (geom == null) {
            geom = geoutils.emptyJTSGeometry();
        }

        if (debug && count2 % 5000 == 0) {
            logMemUsage("GmlGeoX#getGeometry.end " + count2);
        }

        return geom;
    }
}
