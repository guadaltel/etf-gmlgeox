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
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.Result;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.basex.api.dom.BXElem;
import org.basex.api.dom.BXNode;
import org.basex.query.QueryException;
import org.basex.query.QueryIOException;
import org.basex.query.value.Value;
import org.basex.query.value.item.Item;
import org.basex.query.value.item.Jav;
import org.basex.query.value.node.ANode;
import org.deegree.cs.coordinatesystems.ICRS;
import org.deegree.cs.persistence.CRSManager;
import org.deegree.geometry.Geometry;
import org.deegree.geometry.composite.CompositeCurve;
import org.deegree.geometry.composite.CompositeGeometry;
import org.deegree.geometry.composite.CompositeSolid;
import org.deegree.geometry.composite.CompositeSurface;
import org.deegree.geometry.multi.MultiGeometry;
import org.deegree.geometry.multi.MultiSolid;
import org.deegree.geometry.primitive.*;
import org.deegree.geometry.primitive.patches.PolygonPatch;
import org.deegree.geometry.primitive.patches.SurfacePatch;
import org.deegree.geometry.standard.AbstractDefaultGeometry;
import org.deegree.gml.GMLInputFactory;
import org.deegree.gml.GMLStreamReader;
import org.deegree.gml.GMLVersion;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.operation.union.CascadedPolygonUnion;
import org.w3c.dom.Node;

/**
 *
 *
 * @author Johannes Echterhoff (echterhoff at interactive-instruments dot de)
 *
 */
public class GmlGeoXUtils {

    protected XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();
    protected GmlGeoX gmlGeoX;

    /**
     * Used to built JTS geometries.
     */
    protected org.locationtech.jts.geom.GeometryFactory jtsFactory = new org.locationtech.jts.geom.GeometryFactory();

    /**
     * @param gmlGeoX
     *            Reference to GmlGeoX QueryModule, in case that retrieval of certain information (example: srsName) requires execution of xqueries (by the QueryModule).
     */
    public GmlGeoXUtils(GmlGeoX gmlGeoX) {
        this.gmlGeoX = gmlGeoX;
    }

    /**
     * Creates a JTS Polygon from the given deegree PolygonPatch.
     *
     * @param patch
     * @return
     */
    public Polygon toJTSPolygon(PolygonPatch patch) {

        Ring exteriorRing = patch.getExteriorRing();
        List<Ring> interiorRings = patch.getInteriorRings();

        org.locationtech.jts.geom.LinearRing shell = (org.locationtech.jts.geom.LinearRing) ((AbstractDefaultGeometry) exteriorRing)
                .getJTSGeometry();
        org.locationtech.jts.geom.LinearRing[] holes = null;

        if (interiorRings != null) {

            holes = new org.locationtech.jts.geom.LinearRing[interiorRings
                    .size()];

            int i = 0;
            for (Ring ring : interiorRings) {
                holes[i++] = (org.locationtech.jts.geom.LinearRing) ((AbstractDefaultGeometry) ring)
                        .getJTSGeometry();
            }
        }

        return jtsFactory.createPolygon(shell, holes);
    }

    /**
     * Creates a JTS Polygon from the given JTS LineString.
     *
     * @param exterior
     * @return
     */
    public Polygon toJTSPolygon(
            org.locationtech.jts.geom.LineString exterior) {

        org.locationtech.jts.geom.LinearRing exteriorRing = jtsFactory
                .createLinearRing(exterior.getCoordinates());

        return jtsFactory.createPolygon(exteriorRing, null);
    }

    /**
     * Creates a JTS GeometryCollection from the list of JTS geometry objects. If the geometries are homogeneous, ie only points, line strings, or polygons, create the specific geometric aggregate
     *
     * @param gList
     * @param forceGeometryCollection
     * @return a JTS GeometryCollection (empty if the given list is <code>null</code> or empty)
     */
    public GeometryCollection toJTSGeometryCollection(
            List<org.locationtech.jts.geom.Geometry> gList,
            boolean forceGeometryCollection) {

        if (gList == null || gList.isEmpty()) {

            return jtsFactory.createGeometryCollection(null);

        }

        /* Before processing the list of geometries, first remove all empty geometry collections from the list */
        List<org.locationtech.jts.geom.Geometry> gListClean = new ArrayList<org.locationtech.jts.geom.Geometry>();
        for (org.locationtech.jts.geom.Geometry g : gList) {

            if (!g.isEmpty())
                gListClean.add(g);

        }

        GeometryCollection gc = null;
        if (gListClean.isEmpty()) {

            gc = jtsFactory.createGeometryCollection(null);

        } else if (forceGeometryCollection) {

            gc = jtsFactory.createGeometryCollection(gListClean
                    .toArray(new org.locationtech.jts.geom.Geometry[gListClean
                            .size()]));

        } else {

            boolean point = true;
            boolean linestring = true;
            boolean polygon = true;
            for (org.locationtech.jts.geom.Geometry g : gListClean) {
                if (!(g instanceof org.locationtech.jts.geom.Polygon))
                    polygon = false;
                if (!(g instanceof org.locationtech.jts.geom.LineString))
                    linestring = false;
                if (!(g instanceof org.locationtech.jts.geom.Point))
                    point = false;
                if (!polygon && !linestring && !point)
                    break;
            }

            if (point) {
                gc = jtsFactory.createMultiPoint(gListClean.toArray(
                        new org.locationtech.jts.geom.Point[gListClean
                                .size()]));
            } else if (linestring) {
                gc = jtsFactory.createMultiLineString(gListClean.toArray(
                        new org.locationtech.jts.geom.LineString[gListClean
                                .size()]));
            } else if (polygon) {
                gc = jtsFactory.createMultiPolygon(gListClean.toArray(
                        new org.locationtech.jts.geom.Polygon[gListClean
                                .size()]));
            } else {
                if (gListClean.size() == 1) {
                    org.locationtech.jts.geom.Geometry g = gListClean.get(0);
                    if (g instanceof GeometryCollection)
                        gc = (GeometryCollection) g;
                }

                if (gc == null) {
                    gc = jtsFactory.createGeometryCollection(gListClean.toArray(
                            new org.locationtech.jts.geom.Geometry[gListClean
                                    .size()]));
                }
            }
        }
        return gc;
    }

    /**
     * Computes a JTS geometry from the given deegree geometry.
     *
     * Supported geometry types:
     * <ul>
     * <li>GM_Point</li>
     * <li>Curve types:
     * <ul>
     * <li>GM_Curve</li>
     * <li>GM_Ring</li>
     * <li>GM_LinearRing</li>
     * <li>GM_LineString</li>
     * <li>GM_OrientedCurve (orientation is ignored when computing the JTS geometry)</li>
     * </ul>
     * </li>
     * <li>Curve segment types (linearization will often be used):
     * <ul>
     * <li>GM_Arc (will be linearized)</li>
     * <li>GM_Circle (will be linearized)</li>
     * <li>GM_LineString</li>
     * <li>GM_CubicSpline (will be linearized)</li>
     * <li>GM_ArcString (will be linearized)</li>
     * <li>GM_GeodesicString (apparently no linearization - with code from deegree 3.4-pre22-SNAPSHOT)</li>
     * </ul>
     * </li>
     * <li>Surface types:
     * <ul>
     * <li>GM_Surface</li>
     * <li>GM_PolyhedralSurface</li>
     * <li>GM_OrientableSurface (orientation is ignored when computing the JTS geometry)</li>
     * </ul>
     * </li>
     * <li>Surface patch types (also if a surface has more than one patch):
     * <ul>
     * <li>GM_Polygon</li>
     * </ul>
     * </li>
     * <li>Composite types:
     * <ul>
     * <li>GM_Composite (implemented as deegree CompositeGeometry)</li>
     * <li>GM_CompositePoint</li>
     * <li>GM_CompositeCurve</li>
     * <li>GM_CompositeSurface</li>
     * </ul>
     * </li>
     * <li>Multi geometry types:
     * <ul>
     * <li>GM_Aggregate (implemented as deegree MultiGeometry)</li>
     * <li>GM_MultiPoint</li>
     * <li>GM_MultiCurve</li>
     * <li>GM_MultiSurface</li>
     * </ul>
     * </li>
     * </ul>
     * <p>
     * Geometry types that are NOT supported:
     * <ul>
     * <li>GM_Solid</li>
     * <li>Curve segment types:
     * <ul>
     * <li>GM_ArcByBulge</li>
     * <li>ArcByCenterPoint</li>
     * <li>GM_ArcStringByBulge</li>
     * <li>GM_Bezier</li>
     * <li>GM_BSplineCurve</li>
     * <li>CircleByCenterPoint</li>
     * <li>GM_Clothoid</li>
     * <li>GM_Geodesic</li>
     * <li>GM_OffsetCurve</li>
     * <li>GM_Conic</li>
     * </ul>
     * </li>
     * <li>Surface types:
     * <ul>
     * <li>GM_TriangulatedSurface</li>
     * <li>GM_Tin</li>
     * <li>GM_ParametricCurveSurface</li>
     * <li>GM_GriddedSurface</li>
     * <li>GM_BilinearGrid</li>
     * <li>GM_BicubicGrid</li>
     * <li>GM_Cone</li>
     * <li>GM_Cylinder</li>
     * <li>GM_Sphere</li>
     * <li>GM_BSplineSurface</li>
     * </ul>
     * </li>
     * <li>Composite types:
     * <ul>
     * <li>GM_CompositeSolid</li>
     * </ul>
     * </li>
     * <li>Multi geometry types:
     * <ul>
     * <li>GM_MultiSolid</li>
     * </ul>
     * </li>
     * </ul>
     *
     * @param geom
     * @return
     * @throws Exception
     */
    public org.locationtech.jts.geom.Geometry toJTSGeometry(Geometry geom)
            throws Exception {

        if (geom instanceof Surface) {

            /* Deegree does not support spatial operations for surfaces with more than one patch - or rather: it ignores all patches except the first one. So we need to detect and handle this case ourselves.
             *
             * In fact, it is DefaultSurface that does not support multiple patches. So we could check that the geometry is an instance of DefaultSurface. However, it is not planned to create another Geometry-Implementation for deegree. Thus we treat each Surface as having the issue of not supporting JTS geometry creation if it has multiple patches.
             *
             * Because we compute the JTS geometry of a surface directly from its patch(es), we don't need a special treatment for the case of an OrientableSurface. Much like for OrientableCurve, the deegree framework returns null when OrientableSurface.getJTSGeometry() is called (with code from deegree 3.4-pre22-SNAPSHOT). */

            Surface s = (Surface) geom;

            List<? extends SurfacePatch> patches = s.getPatches();

            if (patches.size() > 1) {

                /* compute union - only supportd if all patches are polygon patches */
                List<org.locationtech.jts.geom.Polygon> polygons = new ArrayList<org.locationtech.jts.geom.Polygon>();

                for (SurfacePatch sp : patches) {

                    if (sp instanceof PolygonPatch) {

                        org.locationtech.jts.geom.Polygon p = toJTSPolygon(
                                (PolygonPatch) sp);

                        polygons.add(p);

                    } else {
                        throw new UnsupportedGeometryTypeException(
                                "Surface contains multiple surface patches. At least one patch is not a polygon patch. Cannot create a JTS geometry.");
                    }
                }

                // create union and return it
                return CascadedPolygonUnion.union(polygons);

            } else {

                SurfacePatch sp = patches.get(0);

                if (sp instanceof PolygonPatch) {

                    return toJTSPolygon((PolygonPatch) sp);

                } else {
                    throw new UnsupportedGeometryTypeException(
                            "Surface contains a single surface patch that is not a polygon patch. Cannot create a JTS geometry.");
                }
            }

        } else if (geom instanceof OrientableCurve) {

            /* 2015-12-14 JE: special treatment is necessary because OrientableCurve.getJTSGeometry() returns null (with code from deegree 3.4-pre22-SNAPSHOT). */

            try {
                OrientableCurve oc = (OrientableCurve) geom;
                Curve baseCurve = oc.getBaseCurve();

                return ((AbstractDefaultGeometry) baseCurve).getJTSGeometry();

            } catch (IllegalArgumentException e) {
                throw new UnsupportedGeometryTypeException(
                        "Curve (or one of its segments) is of a type for which computation of the JTS geometry is not supported.");

            }
        } else if (geom instanceof Curve) {

            try {
                return ((AbstractDefaultGeometry) geom).getJTSGeometry();

            } catch (IllegalArgumentException e) {
                throw new UnsupportedGeometryTypeException(
                        "Curve (or one of its segments) is of a type for which computation of the JTS geometry is not supported.");
            }
        } else if (geom instanceof Point) {

            try {
                return ((AbstractDefaultGeometry) geom).getJTSGeometry();

            } catch (UnsupportedOperationException e) {
                throw new UnsupportedGeometryTypeException(
                        "Computation of the JTS geometry for a point is only supported if the number of coordinates is either 2 or 3. A different number of coordinates was encountered.");
            }

        } else if (geom instanceof MultiSolid) {
            throw new UnsupportedGeometryTypeException(
                    "Computation of the JTS geometry for a MultiSolid is not supported.");

        } else if (geom instanceof MultiGeometry) {

            @SuppressWarnings("rawtypes")
            MultiGeometry mg = (MultiGeometry) geom;

            List<org.locationtech.jts.geom.Geometry> gList = new ArrayList<org.locationtech.jts.geom.Geometry>();

            for (Object o : mg) {
                Geometry geo = (Geometry) o;
                org.locationtech.jts.geom.Geometry g = toJTSGeometry(geo);
                gList.add(g);
            }

            GeometryCollection gc = toJTSGeometryCollection(gList, false);
            return gc;

        } else if (geom instanceof CompositeSolid) {
            throw new UnsupportedGeometryTypeException(
                    "Computation of the JTS geometry for a CompositeSolid is not supported.");

        } else if (geom instanceof CompositeGeometry) {

            @SuppressWarnings("rawtypes")
            CompositeGeometry cg = (CompositeGeometry) geom;

            List<org.locationtech.jts.geom.Geometry> gList = new ArrayList<org.locationtech.jts.geom.Geometry>();

            for (Object o : cg) {
                Geometry geo = (Geometry) o;
                org.locationtech.jts.geom.Geometry g = toJTSGeometry(geo);
                gList.add(g);
            }

            GeometryCollection gc = toJTSGeometryCollection(gList, true);
            return gc;

        } else if (geom instanceof CompositeCurve) {

            return ((AbstractDefaultGeometry) geom).getJTSGeometry();

        } else if (geom instanceof CompositeSurface) {

            return ((AbstractDefaultGeometry) geom).getJTSGeometry();

        } else {

            throw new QueryException(
                    "Computation of JTS geometry for deegree geometry type '"
                            + geom.getClass().getName()
                            + "' is not supported.");
        }
    }

    /**
     * Computes a JTS geometry from the given node (which must represent a GML geometry).
     * <p>
     * See {{@link #toJTSGeometry(Geometry)} for a list of supported and unsupported geometry types.
     *
     * @param node
     * @return
     * @throws Exception
     */
    public org.locationtech.jts.geom.Geometry toJTSGeometry(ANode node)
            throws Exception {

        if (node == null) {

            throw new IllegalArgumentException(
                    "Cannot compute JTS geometry because given node is null.");

        } else {

            Geometry geom = parseGeometry(node);

            return toJTSGeometry(geom);
        }
    }

    public org.locationtech.jts.geom.Geometry toJTSGeometry(Object o)
            throws Exception {

        if (o == null) {

            return emptyJTSGeometry();

        } else if (o instanceof Value) {

            Value v = (Value) o;

            if (v.size() > 1) {

                List<org.locationtech.jts.geom.Geometry> geoms = new ArrayList<org.locationtech.jts.geom.Geometry>();

                for (Item i : v) {
                    org.locationtech.jts.geom.Geometry geom = toJTSGeometry(
                            i);
                    geoms.add(geom);
                }

                return toJTSGeometryCollection(geoms, true);

            } else {

                return singleObjectToJTSGeometry(o);
            }

        } else if (o instanceof Object[]) {

            List<org.locationtech.jts.geom.Geometry> geoms = new ArrayList<org.locationtech.jts.geom.Geometry>();

            for (Object os : (Object[]) o) {
                org.locationtech.jts.geom.Geometry geom = toJTSGeometry(os);
                geoms.add(geom);
            }

            return toJTSGeometryCollection(geoms, true);

        } else {

            return singleObjectToJTSGeometry(o);
        }
    }

    public org.locationtech.jts.geom.Geometry singleObjectToJTSGeometry(
            Object o) throws Exception {

        if (o instanceof BXElem) {

            BXElem elem = (BXElem) o;
            ANode node = elem.getNode();

            return toJTSGeometry(node);

        } else if (o instanceof Value) {

            Value v = (Value) o;

            if (v.size() > 1) {
                throw new IllegalArgumentException(
                        "Single value expected where multiple were provided.");
            } else {
                if (v instanceof ANode) {

                    return toJTSGeometry((ANode) v);

                } else if (v instanceof Jav) {

                    return (org.locationtech.jts.geom.Geometry) ((Jav) v)
                            .toJava();
                } else {
                    throw new IllegalArgumentException(
                            "Conversion of BaseX Value type "
                                    + v.getClass().getCanonicalName()
                                    + " to geometry is not supported.");
                }
            }
        } else if (o instanceof org.locationtech.jts.geom.Geometry) {

            return (org.locationtech.jts.geom.Geometry) o;

        } else {
            throw new IllegalArgumentException(
                    "Argument cannot be converted to a single JTS geometry.");
        }

    }

    /**
     * Reads a geometry from the given DOM node.
     *
     * @param aNode
     *            represents a GML geometry element
     * @return the geometry represented by the node
     * @throws Exception
     */
    public Geometry parseGeometry(ANode aNode) throws Exception {

        final String srsName = gmlGeoX.determineSrsName(aNode);

        final BXNode node = aNode.toJava();

        String namespaceURI = node.getNamespaceURI();

        if (namespaceURI == null || (!isGML32Namespace(namespaceURI)
                && !isGML31Namespace(namespaceURI))) {

            throw new Exception("Cannot identify GML version from namespace '"
                    + (namespaceURI == null ? "<null>" : namespaceURI) + "'.");
        }

        final InputStream byteArrayInputStream = nodeToInputStream(node);

        final XMLStreamReader xmlStream = xmlInputFactory
                .createXMLStreamReader(byteArrayInputStream);

        final GMLVersion gmlVersion;
        if (isGML32Namespace(namespaceURI)) {
            gmlVersion = GMLVersion.GML_32;
        } else if (isGML31Namespace(namespaceURI)) {
            gmlVersion = GMLVersion.GML_31;
        } else {
            // cannot happen because we checked before
            throw new IllegalStateException();
        }

        GMLStreamReader gmlStream = GMLInputFactory
                .createGMLStreamReader(gmlVersion, xmlStream);

        final ICRS defaultCRS;
        if (srsName != null) {
            defaultCRS = CRSManager.getCRSRef(srsName);
        } else {
            defaultCRS = null;
        }

        gmlStream.setDefaultCRS(defaultCRS);

        return gmlStream.readGeometry();
    }

    /**
     * Return text representation of a node as InputStream
     *
     * @param node
     * @return
     * @throws TransformerException
     */
    public InputStream nodeToInputStream(final Node node)
            throws TransformerException {

        if (node instanceof BXNode) {
            // BXNode does not support the getPrefix(), which is required by
            // saxon.
            try {
                return new ByteArrayInputStream(
                        ((BXNode) node).getNode().serialize().toArray());
            } catch (QueryIOException e) {
                throw new TransformerException(e);
            }
        }

        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        final Result outputTarget = new StreamResult(outputStream);
        final Transformer t = TransformerFactory.newInstance().newTransformer();
        t.transform(new DOMSource(node), outputTarget);
        return new ByteArrayInputStream(outputStream.toByteArray());
    }

    public boolean isGML32Namespace(String namespaceURI) {
        return GMLVersion.GML_32.getNamespace().equals(namespaceURI);
    }

    public boolean isGML31Namespace(String namespaceURI) {
        return GMLVersion.GML_31.getNamespace().equals(namespaceURI);
    }

    public org.locationtech.jts.geom.Geometry emptyJTSGeometry() {
        return jtsFactory.createGeometryCollection(null);
    }

    /**
     * Adds a geometry to a collection. If the geometry is a GeometryCollection (but not a MultiPoint, -LineString, or -Polygon) its members are added (recursively scanning for GeometryCollections). Spatial relationship operators cannot be performed for a JTS GeometryCollection, but for (Multi)Point, (Multi)LineString, and (Multi)Polygon.
     *
     * @param geom
     * @return
     */
    public List<org.locationtech.jts.geom.Geometry> toFlattenedJTSGeometryCollection(
            org.locationtech.jts.geom.Geometry geom) {

        final List<org.locationtech.jts.geom.Geometry> geoms = new ArrayList<org.locationtech.jts.geom.Geometry>();

        if (isGeometryCollectionButNotASubtype(geom)) {
            toFlattenedJTSGeometryCollection((GeometryCollection) geom, geoms);
        } else {
            geoms.add(geom);
        }

        return geoms;
    }

    protected void toFlattenedJTSGeometryCollection(
            org.locationtech.jts.geom.GeometryCollection geomColl,
            List<org.locationtech.jts.geom.Geometry> geoms) {

        for (int i = 0; i < geomColl.getNumGeometries(); i++) {

            org.locationtech.jts.geom.Geometry geom = geomColl
                    .getGeometryN(i);

            if (isGeometryCollectionButNotASubtype(geom)) {

                toFlattenedJTSGeometryCollection((GeometryCollection) geom,
                        geoms);

            } else {

                geoms.add(geom);
            }
        }
    }

    protected boolean isGeometryCollectionButNotASubtype(
            org.locationtech.jts.geom.Geometry geom) {

        if (geom instanceof GeometryCollection
                && !(geom instanceof org.locationtech.jts.geom.MultiPoint
                        || geom instanceof org.locationtech.jts.geom.MultiLineString
                        || geom instanceof org.locationtech.jts.geom.MultiPolygon)) {
            return true;
        } else {
            return false;
        }
    }
}
