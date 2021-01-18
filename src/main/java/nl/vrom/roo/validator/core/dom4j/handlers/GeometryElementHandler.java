package nl.vrom.roo.validator.core.dom4j.handlers;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.operation.valid.IsValidOp;
import org.locationtech.jts.operation.valid.TopologyValidationError;
import nl.vrom.roo.validator.core.ValidatorContext;
import nl.vrom.roo.validator.core.ValidatorMessageBundle;
import nl.vrom.roo.validator.core.dom4j.Dom4JHelper;
import nl.vrom.roo.validator.core.errorlocation.IdErrorLocation;
import org.deegree.commons.xml.XMLParsingException;
import org.deegree.cs.coordinatesystems.ICRS;
import org.deegree.cs.exceptions.UnknownCRSException;
import org.deegree.cs.persistence.CRSManager;
import org.deegree.geometry.standard.AbstractDefaultGeometry;
import org.deegree.geometry.validation.GeometryValidator;
import org.deegree.gml.GMLInputFactory;
import org.deegree.gml.GMLStreamReader;
import org.deegree.gml.GMLVersion;
import org.dom4j.Element;
import org.dom4j.ElementHandler;
import org.dom4j.ElementPath;
import org.dom4j.Namespace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.*;

/**
 * GeometryElementHandler handles <code>Element</code> objects in the
 * gmlNamespace.
 *
 * @author rbrinkman
 * @since 1.1
 * 
 */
public class GeometryElementHandler implements ElementHandler {

	private static final Logger LOGGER = LoggerFactory.getLogger(GeometryElementHandler.class);

	private static final NumberFormat COORD_FORMAT = new DecimalFormat("0.000#######",
			new DecimalFormatSymbols(Locale.ENGLISH));

	private static final String NODE_NAME_FEATURE_MEMBER = "featureMember";

	private final ValidatorContext validatorContext;

	private final List<String> gmlGeometries = new ArrayList<String>();

	private final Map<String, Integer> currentGmlGeometryCounters = new HashMap<String, Integer>();

	private String currentFeatureMember;

	private boolean isGMLVersionReported;

	private String defaultSrsName;

	/**
	 * Instantiates a new geometry element handler.
	 *
	 * @param validatorContext
	 *            the validatorContext used during handling
	 * @param parameters
	 *            the parameters for handling
	 * @param srsName
	 *            the name of the default SRS to use when validating a geometry
	 *            (especially important in case of geometries with 3D
	 *            coordinates, and srsName is not defined on the geometry
	 *            element itself)
	 */
	public GeometryElementHandler(ValidatorContext validatorContext, Map<String, Object> parameters, String srsName) {

		this.validatorContext = validatorContext;

		this.defaultSrsName = srsName;

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
	 * When this is called on a new feature member, the geometry counters are
	 * reset.
	 * </p>
	 */
	public void onStart(ElementPath elementPath) {

		String currentPath = elementPath.getPath();

		if (NODE_NAME_FEATURE_MEMBER.equals(Dom4JHelper.getNodeFromPath(Dom4JHelper.getParentPath(currentPath)))) {
			currentFeatureMember = Dom4JHelper.getNodeFromPath(currentPath);
			resetGmlGeometryCounters();
		}
	}

	/** {@inheritDoc} */
	public void onEnd(ElementPath elementPath) {
		String nodeName = Dom4JHelper.getNodeFromPath(elementPath.getPath());
		if (gmlGeometries.contains(nodeName)) {

			// Check if this element is a main geometry
			if (isMainGeometry(elementPath)) {
				raiseGmlGeometryCounter(nodeName);

				try {
					validate(validatorContext, elementPath.getCurrent());
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

		if (namespace == null || (!isGML32Namespace(namespaceURI) && !isGML31Namespace(namespaceURI))) {

			LOGGER.error("Unable to determine GML version. Namespace= {}", namespaceURI);

			String errMessage = ValidatorMessageBundle.getMessage("validator.core.validation.geometry.no-gml",
					namespaceURI);

			validatorContext.addError(errMessage);
			return;
		}

		if (!isGMLVersionReported) {
			if (isGML32Namespace(namespaceURI)) {
				validatorContext.addNotice(
						ValidatorMessageBundle.getMessage("validator.core.validation.geometry.gmlversion", "3.2"));
			} else if (isGML31Namespace(namespaceURI)) {
				validatorContext.addNotice(
						ValidatorMessageBundle.getMessage("validator.core.validation.geometry.gmlversion", "3.1"));
			}
			isGMLVersionReported = true;
		}

		try {
			String elementXML = element.asXML();
			ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(elementXML.getBytes());

			XMLStreamReader xmlStream = XMLInputFactory.newInstance().createXMLStreamReader(byteArrayInputStream);

			GMLVersion gmlVersion = null;
			if (isGML32Namespace(namespaceURI)) {
				// GML 3.2
				gmlVersion = GMLVersion.GML_32;
			} else if (isGML31Namespace(namespaceURI)) {
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

			// GML pre-validation: check tha srsDimension is in the right tag and its value its compatible with the declared SRS
			GMLPrevalidator gmlPrevalidator = new GMLPrevalidator();
			GMLPrevalidator.PrevalidationResult prevalidationResult = gmlPrevalidator.prevalidate(
				XMLInputFactory.newInstance().createXMLStreamReader(new ByteArrayInputStream(elementXML.getBytes())),defaultCRS);
			if (prevalidationResult != GMLPrevalidator.PrevalidationResult.PREVALIDATION_OK) {
				switch (prevalidationResult) {
					case PARSE_ERROR:
						throw new Exception (ValidatorMessageBundle.getMessage("validator.core.prevalidation.parse-error"));
					case BAD_TAG_FOR_SRSDIMENSION:
						throw new Exception (ValidatorMessageBundle.getMessage("validator.core.prevalidation.bad-tag-srsdimension"));
					case WRONG_SRS_DIMENSION:
						throw new Exception (ValidatorMessageBundle.getMessage("validator.core.prevalidation.wrong-srs-dimension"));
					default:
						throw new Exception (ValidatorMessageBundle.getMessage("validator.core.validation.geometry.unknown-exception"));
				}
			}

			org.deegree.geometry.Geometry geom = gmlStream.readGeometry();

			GMLValidationEventHandler eventHandler = new GMLValidationEventHandler(validatorContext, element,
					gmlVersion == GMLVersion.GML_31);

			GeometryValidator validator = new GeometryValidator(eventHandler);

			// Deegree3 based validation
			boolean isValid = validator.validateGeometry(geom);

			if (isValid) {
				// Call JTS based validation as long as Deegree3 is still not
				// complete enough.
				handleDeegree3GMLJTSValidation(geom, element);
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

		/*
		 * 2015-12-08 Johannes Echterhoff: it is not clear to me that detaching
		 * is relevant.
		 */
		// finally {
		// // Only permitted if this is a main geometry....
		// if (isMainGeometry(element)) {
		// element.detach();
		// }
		// }
	}

	//
	// /**
	// * To be used when Deegree-2 needs to be used for GML-3.1 validation
	// * @param geom
	// * @param element
	// */
	// private void
	// handleDeegree2GML31Validation(org.deegree.model.spatialschema.Geometry
	// geom, Element element) {
	// try {
	// handleGML31JtsValidation(JTSAdapter.export(geom), element);
	// } catch (GeometryException e) {
	// String currentGmlId = Dom4JHelper.findGmlId(element);
	//
	// LOGGER.trace("{} {} within element with planId {} is invalid", new
	// Object[] { element.getName(),
	// currentGmlGeometryCounters.get(element.getName()), currentGmlId });
	//
	// String message = getLocationDescription(element, currentGmlId) + ": " +
	// e.getMessage();
	//
	// validatorContext.addError(message, new IdErrorLocation(currentGmlId));
	// }
	// }

	// /**
	// * To be used when JTS and Deegree3 needs to be used for GML-3.1
	// validation
	// * @param geom
	// * @param element
	// */
	// private void handleDeegree3GML31Validation(org.deegree.geometry.Geometry
	// geom, Element element) {
	//
	// if(geom instanceof AbstractDefaultGeometry) {
	// Geometry jtsGeometry = ((AbstractDefaultGeometry)geom).getJTSGeometry();
	// handleGML31JtsValidation(jtsGeometry, element);
	// }
	// else {
	// throw new IllegalArgumentException("Don't know how to handle geometry of
	// type " + geom.getClass());
	// }
	// }

	// /**
	// * To be used when Deegree3 geometric validation needs to be used for
	// GML-3.2 validation
	// * @param geom
	// * @param element
	// */
	// private void handleDeegree3GML32Validation(org.deegree.geometry.Geometry
	// geom, Element element) {
	//
	// GML32ValidationEventHandler eventHandler = new
	// GML32ValidationEventHandler(validatorContext, element);
	//
	// GeometryValidator validator = new GeometryValidator( eventHandler );
	//
	// validator.validateGeometry(geom);
	// }

	/**
	 * To be used when JTS needs to be used for GML validation
	 * 
	 * @param geom
	 * @param element
	 */
	private void handleDeegree3GMLJTSValidation(org.deegree.geometry.Geometry geom, Element element) {

		if (geom instanceof AbstractDefaultGeometry) {
			try {
				Geometry jtsGeometry = ((AbstractDefaultGeometry) geom).getJTSGeometry();
				handleGMLJtsValidation(jtsGeometry, element);
			} catch (IllegalArgumentException e) {
				String currentGmlId = Dom4JHelper.findGmlId(element);

				LOGGER.debug("{} {} within element with gml:id {} is not supported: {}",
						new Object[] { element.getName(), currentGmlGeometryCounters.get(element.getName()),
								currentGmlId, e.getMessage() });

				String currentOnderdeelName = element.getQualifiedName();

				String errMessage = ValidatorMessageBundle.getMessage("validator.core.validation.geometry.unsupported",
						currentGmlId, currentOnderdeelName, e.getMessage());

				this.validatorContext.addError(errMessage, new IdErrorLocation(currentGmlId));
			}

		} else {
			throw new IllegalArgumentException("Don't know how to handle geometry of type " + geom.getClass());
		}
	}

	// private void reportMessage(Element element, LinearRing jtsRing, String
	// errorCode, String currentGmlId) {
	//
	// String currentOnderdeelName = element.getQualifiedName();
	//
	// String errMessage = ValidatorMessageBundle.getMessage(
	// errorCode,
	// currentGmlId,
	// currentOnderdeelName,
	// getAffectedCoordinates(jtsRing.getCoordinates(), 60));
	//
	// String message = getLocationDescription(element, currentGmlId) + ": " +
	// errMessage;
	//
	// this.validatorContext.addError(message, new
	// IdErrorLocation(currentGmlId));
	//
	// }
	//

	//
	// private String getAffectedCoordinates(Coordinate[] coords, int
	// numberOfCharacters) {
	// StringBuffer buf = new StringBuffer();
	//
	// for(Coordinate coordinate : coords) {
	// buf.append(formatValue(coordinate.x)).append("
	// ").append(formatValue(coordinate.y)).append(" ");
	//
	// if(buf.length()>numberOfCharacters) {
	// break;
	// }
	// }
	// return buf.toString();
	// }
	//

	private void handleGMLJtsValidation(org.locationtech.jts.geom.Geometry jtsGeometry, Element element) {

		IsValidOp ivo = new IsValidOp(jtsGeometry);
		// Optimization: ivo.isValid() is the same as topError==null. Otherwise
		// the validation is performed twice in case of geometry problems.
		TopologyValidationError topError = ivo.getValidationError();

		String currentGmlId = Dom4JHelper.findGmlId(element);
		if (topError == null) {

			LOGGER.trace("{} {} within element with gml:id {} is valid", new Object[] { element.getName(),
					currentGmlGeometryCounters.get(element.getName()), currentGmlId });
		} else {
			String coordinatesText = generateCoordinatesText(jtsGeometry.getCoordinates(), topError.getCoordinate());

			LOGGER.trace("{} {} within element with gml:id {} is invalid", new Object[] { element.getName(),
					currentGmlGeometryCounters.get(element.getName()), currentGmlId });

			String message = getLocationDescription(element, currentGmlId) + ": " + topError.getMessage() + ". "
					+ coordinatesText;

			validatorContext.addError(message, new IdErrorLocation(currentGmlId));
		}
	}

	private String getLocationDescription(Element element, String gmlId) { // NOPMD
																			// -
																			// Method
																			// is
																			// not
																			// empty
		return ValidatorMessageBundle.getMessage("validator.core.validation.geometry.coordinates-position",
				new Object[] { element.getName(), currentGmlGeometryCounters.get(element.getName()),
						currentFeatureMember, gmlId });
	}

	private String generateCoordinatesText(Coordinate[] coordinates, Coordinate coordProblem) {
		String coordinatesText = null;

		if (coordinates.length > 0) {
			Coordinate coordBegin = coordinates[0];
			Coordinate coordEnd = coordinates[coordinates.length - 1];

			if (coordProblem == null) {
				coordinatesText = ValidatorMessageBundle.getMessage(
						"validator.core.validation.geometry.coordinates-text-simple",
						new Object[] { formatValue(coordBegin.x), formatValue(coordBegin.y), formatValue(coordEnd.x),
								formatValue(coordEnd.y) });
			} else {
				coordinatesText = ValidatorMessageBundle
						.getMessage("validator.core.validation.geometry.coordinates-text",
								new Object[] { formatValue(coordBegin.x), formatValue(coordBegin.y),
										formatValue(coordEnd.x), formatValue(coordEnd.y), formatValue(coordProblem.x),
										formatValue(coordProblem.y) });
			}
		}
		return coordinatesText;
	}

	private String formatValue(double value) { // NOPMD - Method is not empty
		return COORD_FORMAT.format(value);
	}

	private boolean isGML32Namespace(String namespaceURI) {
		return GMLVersion.GML_32.getNamespace().equals(namespaceURI);
	}

	private boolean isGML31Namespace(String namespaceURI) {
		return GMLVersion.GML_31.getNamespace().equals(namespaceURI);
	}

	/**
	 * Check if this is a main geometry by making sure that the parent tag is in
	 * one of the imro namespaces
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

		/*
		 * 2015-12-08 Johannes Echterhoff: the previous logic is not suitable
		 * for us, in case that a single geometry (node) is validated by a
		 * (x)query. Because then the geometry has no parent, which would cause
		 * this method to evaluate to false - which is not what we want.
		 */

		if (elementPath.getCurrent().getParent() == null) {

			// the geometry is the only XML that is validated
			return true;

		} else {

			// if the parent namespace is not a GML one, we have a main geometry
			String namespaceURI = elementPath.getCurrent().getParent().getNamespaceURI();
			return (!isGML32Namespace(namespaceURI) && !isGML31Namespace(namespaceURI));
		}
	}

	/**
	 * Check if this is a main geometry by making sure that the parent tag is in
	 * one of the imro namespaces
	 *
	 * @param elementPath
	 * @return true is this is a main geometry
	 */
	private boolean isMainGeometry(Element element) {
		String namespaceURI = element.getParent().getNamespaceURI();
		return (!isGML32Namespace(namespaceURI) && !isGML31Namespace(namespaceURI));
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

}
