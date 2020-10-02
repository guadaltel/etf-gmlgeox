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

import java.io.ByteArrayInputStream;
import java.util.*;

import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import nl.vrom.roo.validator.core.ValidatorContext;
import nl.vrom.roo.validator.core.ValidatorMessageBundle;
import nl.vrom.roo.validator.core.dom4j.Dom4JHelper;
import nl.vrom.roo.validator.core.dom4j.handlers.ValidationUtil;
import nl.vrom.roo.validator.core.errorlocation.IdErrorLocation;

import org.deegree.commons.xml.XMLParsingException;
import org.deegree.cs.coordinatesystems.ICRS;
import org.deegree.cs.exceptions.UnknownCRSException;
import org.deegree.cs.persistence.CRSManager;
import org.deegree.geometry.Geometry;
import org.deegree.geometry.composite.CompositeGeometry;
import org.deegree.geometry.composite.CompositeSolid;
import org.deegree.geometry.multi.MultiGeometry;
import org.deegree.geometry.multi.MultiSolid;
import org.deegree.geometry.points.Points;
import org.deegree.geometry.primitive.*;
import org.deegree.geometry.primitive.patches.PolygonPatch;
import org.deegree.geometry.primitive.patches.SurfacePatch;
import org.deegree.geometry.primitive.segments.*;
import org.deegree.geometry.standard.points.PointsList;
import org.deegree.gml.GMLInputFactory;
import org.deegree.gml.GMLStreamReader;
import org.deegree.gml.GMLVersion;
import org.dom4j.Element;
import org.dom4j.ElementHandler;
import org.dom4j.ElementPath;
import org.dom4j.Namespace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NOTE: Implementation is based on Geonovum's GeometryElementHandler.
 *
 * @author Johannes Echterhoff (echterhoff at interactive-instruments dot de)
 *
 */
public class SecondaryGeometryElementValidationHandler implements ElementHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(SecondaryGeometryElementValidationHandler.class);

    protected GmlGeoXUtils geoutils;

    private final ValidatorContext validatorContext;

    private final Set<String> gmlGeometries = new HashSet<String>();

    private final Map<String, Integer> currentGmlGeometryCounters = new HashMap<String, Integer>();

    private Element currentElement;

    private boolean isGMLVersionReported;

    private boolean isTestRepetitionInCurveSegments;
    private boolean isTestPolygonPatchConnectivity;

    private boolean noRepetitionInCurveSegments = true;
    private boolean polygonPatchesAreConnected = true;

    private String defaultSrsName;

    /**
     * @param isTestPolygonPatchConnectivity
     *            - <code>true</code> if polygon patch connectivity shall be tested
     * @param isTestRepetitionInCurveSegments
     *            - <code>true</code> if repetition of consecutive points in curve segments shall be checked
     * @param validatorContext
     * @param srsName
     *            the name of the default SRS to use when validating a geometry (especially important in case of geometries with 3D coordinates, and srsName is not defined on the geometry element itself)
     * @param gmlGeoX
     *            Reference to GmlGeoX QueryModule, in case that retrieval of certain information (example: srsName) requires execution of xqueries (by the QueryModule).
     */
    public SecondaryGeometryElementValidationHandler(boolean isTestPolygonPatchConnectivity,
            boolean isTestRepetitionInCurveSegments, ValidatorContext validatorContext, String srsName,
            GmlGeoX gmlGeoX) {

        this.isTestPolygonPatchConnectivity = isTestPolygonPatchConnectivity;
        this.isTestRepetitionInCurveSegments = isTestRepetitionInCurveSegments;

        this.validatorContext = validatorContext;

        this.defaultSrsName = srsName;

        this.geoutils = new GmlGeoXUtils(gmlGeoX);

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
    }

    /**
     * {@inheritDoc}
     * <p>
     * When this is called on a new feature member, the geometry counters are reset.
     * </p>
     */
    public void onStart(ElementPath elementPath) {
        resetGmlGeometryCounters();
    }

    /** {@inheritDoc} */
    public void onEnd(ElementPath elementPath) {

        String nodeName = Dom4JHelper.getNodeFromPath(elementPath.getPath());

        if (gmlGeometries.contains(nodeName)) {

            // Check if this element is a main geometry
            if (isMainGeometry(elementPath)) {

                raiseGmlGeometryCounter(nodeName);

                currentElement = elementPath.getCurrent();

                try {
                    validate(validatorContext, currentElement);
                } catch (XMLParsingException e) {
                    LOGGER.error("Unexpected error detected while validating geometry", e);
                } catch (UnknownCRSException e) {
                    LOGGER.error("Unexpected error detected while validating geometry", e);
                }
            } else {
                LOGGER.trace("Element {} is part of another geometry", nodeName);
            }
        }
    }

    private void validate(ValidatorContext validatorContext, Element element)
            throws XMLParsingException, UnknownCRSException {

        Namespace namespace = element.getNamespace();
        String namespaceURI = namespace == null ? null : namespace.getURI();

        if (namespace == null
                || (!geoutils.isGML32Namespace(namespaceURI) && !geoutils.isGML31Namespace(namespaceURI))) {

            LOGGER.error("Unable to determine GML version. Namespace= {}", namespaceURI);

            String errMessage = ValidatorMessageBundle.getMessage("validator.core.validation.geometry.no-gml",
                    namespaceURI);

            validatorContext.addError(errMessage);
            return;
        }

        if (!isGMLVersionReported) {
            if (geoutils.isGML32Namespace(namespaceURI)) {
                validatorContext.addNotice(
                        ValidatorMessageBundle.getMessage("validator.core.validation.geometry.gmlversion", "3.2"));
            } else if (geoutils.isGML31Namespace(namespaceURI)) {
                validatorContext.addNotice(
                        ValidatorMessageBundle.getMessage("validator.core.validation.geometry.gmlversion", "3.1"));
            }
            isGMLVersionReported = true;
        }

        try {
            ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(element.asXML().getBytes());

            XMLStreamReader xmlStream = XMLInputFactory.newInstance().createXMLStreamReader(byteArrayInputStream);

            GMLVersion gmlVersion = null;
            if (geoutils.isGML32Namespace(namespaceURI)) {
                // GML 3.2
                gmlVersion = GMLVersion.GML_32;
            } else if (geoutils.isGML31Namespace(namespaceURI)) {
                gmlVersion = GMLVersion.GML_31;
            } else {
                throw new Exception("Cannot determine GML version");
            }

            GMLStreamReader gmlStream = GMLInputFactory.createGMLStreamReader(gmlVersion, xmlStream);

            ICRS defaultCRS = null;
            if (defaultSrsName != null) {
                defaultCRS = CRSManager.getCRSRef(defaultSrsName);
            }

            gmlStream.setDefaultCRS(defaultCRS);

            Geometry geom = gmlStream.readGeometry();

            // ================
            // Test: polygon patches of a surface are connected
            if (isTestPolygonPatchConnectivity) {
                boolean isValid = checkConnectivityOfPolygonPatches(geom);
                if (!isValid) {
                    polygonPatchesAreConnected = false;
                }
            }

            // ================
            // Test: point repetition in curve segment
            if (isTestRepetitionInCurveSegments) {
                boolean isValid = checkNoRepetitionInCurveSegment(geom);
                if (!isValid) {
                    noRepetitionInCurveSegments = false;
                }
            }

        } catch (XMLStreamException e) {

            String currentGmlId = Dom4JHelper.findGmlId(element);

            String message = getLocationDescription(element, currentGmlId) + ": " + e.getMessage();

            validatorContext.addError(message, new IdErrorLocation(currentGmlId));

            LOGGER.error(e.getMessage(), e);

        } catch (FactoryConfigurationError e) {

            LOGGER.error(e.getMessage(), e);
            validatorContext.addError(
                    ValidatorMessageBundle.getMessage("validator.core.validation.geometry.unknown-exception"));

        } catch (Exception e) {
            String currentGmlId = Dom4JHelper.findGmlId(element);

            String message = getLocationDescription(element, currentGmlId) + ": " + e.getMessage();

            validatorContext.addError(message, new IdErrorLocation(currentGmlId));

            LOGGER.error(e.getMessage(), e);
        }
    }

    private String getLocationDescription(Element element, String gmlId) {

        return ValidatorMessageBundle.getMessage("validator.core.validation.geometry.coordinates-position",
                new Object[]{element.getName(), currentGmlGeometryCounters.get(element.getName()),
                        currentElement.getName(), gmlId});
    }

    /**
     * Check if this is a main geometry by making sure that the parent element is <code>null</code> or in a non-GML namespace.
     *
     * @param elementPath
     * @return true is this is a main geometry
     */
    private boolean isMainGeometry(ElementPath elementPath) {
        // if(elementPath.getCurrent().getParent()==null) {
        // return false;
        // }
        // String namespaceURI =
        // elementPath.getCurrent().getParent().getNamespaceURI();
        // return (!isGML32Namespace(namespaceURI) &&
        // !isGML31Namespace(namespaceURI));

        /* 2015-12-08 Johannes Echterhoff: the previous logic is not suitable for us, in case that a single geometry (node) is validated by a (x)query. Because then the geometry has no parent, which would cause this method to evaluate to false - which is not what we want. */

        if (elementPath.getCurrent().getParent() == null) {

            // the geometry is the only XML that is validated

            String namespaceURI = elementPath.getCurrent().getNamespaceURI();
            return (geoutils.isGML32Namespace(namespaceURI) || geoutils.isGML31Namespace(namespaceURI));

        } else {

            // if the parent namespace is not a GML one, we have a main geometry
            String namespaceURI = elementPath.getCurrent().getParent().getNamespaceURI();
            return (!geoutils.isGML32Namespace(namespaceURI) && !geoutils.isGML31Namespace(namespaceURI));
        }
    }

    public void registerGmlGeometry(String nodeName) {
        gmlGeometries.add(nodeName);
        currentGmlGeometryCounters.put(nodeName, 0);
    }

    public void unregisterGmlGeometry(String nodeName) {
        gmlGeometries.remove(nodeName);
        currentGmlGeometryCounters.remove(nodeName);
    }

    public void unregisterAllGmlGeometries() {
        gmlGeometries.clear();
        currentGmlGeometryCounters.clear();
    }

    private void raiseGmlGeometryCounter(String nodeName) {
        Integer newCounter = currentGmlGeometryCounters.get(nodeName) + 1;
        currentGmlGeometryCounters.put(nodeName, newCounter);
    }

    private void resetGmlGeometryCounters() {
        for (String key : currentGmlGeometryCounters.keySet()) {
            currentGmlGeometryCounters.put(key, 0);
        }
        LOGGER.trace("Counters reset");
    }

    /**
     * Checks that multiple polygon patches within a surface are connected.
     * <p>
     * The test is implemented as follows: Each polygon patch is converted into a JTS Polygon. Then the union of all polygons is created. If the union geometry is a JTS Polygon then the surface is connected - otherwise it is not.
     * <p>
     * Checks:
     * <ul>
     * <li>Surface (including PolyhedralSurface, CompositeSurface, and OrientableSurface)</li>
     * <li>Only PolygonPatch is allowed as surface patch - all surfaces that contain a different type of surface patch are ignored.</li>
     * <li>The elements of multi and composite geometries (except Multi- and CompositeSolids).</li>
     * </ul>
     * Does NOT check the surfaces within solids!
     *
     * @param geom
     * @return <code>true</code> if the given geometry is a connected surface, a point, a curve, multi- or composite geometry that only consists of these geometry types, else <code>false</code>. Thus, <code>false</code> will be returned whenever a solid is encountered and if a surface is not connected.
     * @throws Exception
     */
    protected boolean checkConnectivityOfPolygonPatches(Geometry geom) throws Exception {

        if (geom instanceof Surface) {

            Surface s = (Surface) geom;

            List<? extends SurfacePatch> sps = s.getPatches();

            if (sps.size() <= 1) {

                // not multiple patches -> nothing to check
                return true;

            } else {

                /* Compute JTS geometry from the surface with multiple patches. An exception will be thrown if the surface does not consist of polygon patches. If the surface is connected then the resulting geometry is a JTS Polygon (because a union of all patches has been created). Otherwise the surface is not connected. */
                org.locationtech.jts.geom.Geometry g = geoutils.toJTSGeometry(geom);

                if (g instanceof org.locationtech.jts.geom.Polygon) {

                    return true;

                } else {

                    String gmlid = Dom4JHelper.findGmlId(currentElement);
                    if (geom.getId() != null) {
                        gmlid = geom.getId();
                    }
                    if (gmlid == null) {
                        gmlid = "null";
                    }

                    validatorContext.addError(ValidatorMessageBundle
                            .getMessage("validator.core.validation.geometry.surfacepatchesnotconnected", gmlid));
                    return false;
                }
            }

        } else if (geom instanceof MultiSolid || geom instanceof CompositeSolid || geom instanceof Solid) {

            return false;

        } else if (geom instanceof MultiGeometry || geom instanceof CompositeGeometry) {

            // MultiSurface extends MultiGeometry
            // CompositeSurface extends CompositeGeometry

            @SuppressWarnings("rawtypes")
            List l = (List) geom;

            for (Object o : l) {
                Geometry g = (Geometry) o;
                if (!checkConnectivityOfPolygonPatches(g)) {
                    return false;
                }
            }

            return true;

        } else {

            return true;
        }

    }

    /**
     * Checks that two consecutive points in a posList - within a CurveSegment - are not equal.
     * <p>
     * Checks:
     * <ul>
     * <li>Curve (including CompositeCurve, LineString, OrientableCurve, Ring)</li>
     * <li>The following CurveSegment types: Arc, ArcString, CubicSpline, GeodesicString, LineStringSegment</li>
     * <li>The exterior and interior rings of polygon patches (contained within Surface, Polygon, PolyhedralSurface, TriangulatedSurface, Tin, CompositeSurface, or OrientableSurface) - NOTE: all other types of surface patches are currently ignored!</li>
     * <li>The elements of multi and composite geometries</li>
     * </ul>
     * Does NOT check curve segments within solids!
     *
     * @param geom
     *            the geometry that shall be tested
     * @return <code>true</code> if no repetition was detected (or if the geometry is a point, a solid, or consists of solids), else <code>false</code>
     */
    protected boolean checkNoRepetitionInCurveSegment(Geometry geom) {

        if (geom instanceof Curve) {

            /* includes CompositeCurve, LineString, OrientableCurve, Ring */

            Curve curve = (Curve) geom;

            List<CurveSegment> segments = curve.getCurveSegments();

            for (int segmentIdx = 0; segmentIdx < segments.size(); segmentIdx++) {

                CurveSegment segment = segments.get(segmentIdx);

                Points points = null;

                if (segment instanceof ArcString) {

                    ArcString as = (ArcString) segment;
                    points = as.getControlPoints();

                } else if (segment instanceof Arc) {

                    Arc arc = (Arc) segment;
                    List<Point> lp = new ArrayList<Point>();

                    lp.add(arc.getPoint1());
                    lp.add(arc.getPoint2());
                    lp.add(arc.getPoint3());

                    points = new PointsList(lp);

                } else if (segment instanceof CubicSpline) {

                    CubicSpline cs = (CubicSpline) segment;
                    points = cs.getControlPoints();

                } else if (segment instanceof GeodesicString) {

                    GeodesicString gs = (GeodesicString) segment;
                    points = gs.getControlPoints();

                } else if (segment instanceof LineStringSegment) {

                    LineStringSegment lss = (LineStringSegment) segment;
                    points = lss.getControlPoints();
                }

                Point lastPoint = null;
                for (Point point : points) {
                    if (lastPoint != null) {
                        if (point.equals(lastPoint)) {

                            String s0 = Dom4JHelper.findGmlId(currentElement);
                            if (geom.getId() != null) {
                                s0 = geom.getId();
                            }
                            if (s0 == null) {
                                s0 = "null";
                            }

                            String s1 = ValidationUtil.getAffectedCoordinates(curve.getCurveSegments().get(segmentIdx),
                                    null);
                            String s2 = ValidationUtil.getProblemLocation(point);
                            validatorContext.addError(ValidatorMessageBundle.getMessage(
                                    "validator.core.validation.geometry.repetitionincurvesegment", s0, s1, s2));
                            return false;
                        }
                    }
                    lastPoint = point;
                }
            }

            return true;

        } else if (geom instanceof Surface) {

            Surface s = (Surface) geom;

            List<? extends SurfacePatch> patches = s.getPatches();

            for (SurfacePatch sp : patches) {

                if (sp instanceof PolygonPatch) {

                    PolygonPatch pp = (PolygonPatch) sp;

                    Ring exterior = pp.getExteriorRing();

                    if (!checkNoRepetitionInCurveSegment(exterior)) {
                        return false;
                    }

                    List<Ring> interiorRings = pp.getInteriorRings();
                    for (Ring interiorRing : interiorRings) {
                        if (!checkNoRepetitionInCurveSegment(interiorRing)) {
                            return false;
                        }
                    }

                } else {
                    // TODO is another type of surface patch relevant?
                }
            }

            return true;

        } else if (geom instanceof MultiGeometry || geom instanceof CompositeGeometry) {

            @SuppressWarnings("rawtypes")
            List l = (List) geom;

            for (Object o : l) {
                Geometry g = (Geometry) o;
                if (!checkNoRepetitionInCurveSegment(g)) {
                    return false;
                }
            }

            return true;

        } else {

            return true;
        }
    }

    /**
     * @return <code>true</code> if there is no repetition in curve segments
     */
    public boolean isNoRepetitionInCurveSegments() {
        return noRepetitionInCurveSegments;
    }

    /**
     * @return <code>true</code> if the polygon patches are connected
     */
    public boolean arePolygonPatchesConnected() {
        return polygonPatchesAreConnected;
    }
}
