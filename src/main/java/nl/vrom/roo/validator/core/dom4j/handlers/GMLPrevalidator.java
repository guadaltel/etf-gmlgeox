package nl.vrom.roo.validator.core.dom4j.handlers;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.deegree.commons.xml.XMLParsingException;
import org.deegree.cs.coordinatesystems.ICRS;
import org.deegree.cs.exceptions.UnknownCRSException;
import org.deegree.cs.persistence.CRSManager;
import java.net.URL;

class GMLPrevalidator
{
	public enum PrevalidationResult {
        PARSE_ERROR,
        BAD_TAG_FOR_SRSDIMENSION,
        WRONG_SRS_DIMENSION,
        PREVALIDATION_OK
    }

	public PrevalidationResult prevalidate( XMLStreamReader xmlStream, ICRS defaultCRS )
                            throws XMLParsingException, XMLStreamException, UnknownCRSException {

        PrevalidationResult result = PrevalidationResult.PREVALIDATION_OK;

        if (xmlStream.getEventType() != XMLStreamReader.START_ELEMENT) {
            while (xmlStream.hasNext() && xmlStream.getEventType() != XMLStreamReader.START_ELEMENT) {
                xmlStream.next();
            }
            if (xmlStream.getEventType() != XMLStreamReader.START_ELEMENT) {
        	    return PrevalidationResult.PARSE_ERROR;
            }
        }

        ICRS crs = getCRS( xmlStream, defaultCRS );
        if (hasNumDimensions(xmlStream)) {
        	if (!(xmlStream.getLocalName().equals("pos") || xmlStream.getLocalName().equals("posList"))) {
        		result = PrevalidationResult.BAD_TAG_FOR_SRSDIMENSION;
        	} else {
        		if (crs != null && crs.getDimension() != getNumDimensions(xmlStream, crs)) {
		        	result = PrevalidationResult.WRONG_SRS_DIMENSION;
		        }
        	}
        }

        while (xmlStream.hasNext() && result == PrevalidationResult.PREVALIDATION_OK) {
        	int eventType = xmlStream.next();
        	if (eventType == XMLStreamReader.START_ELEMENT) {
        		result = prevalidate(xmlStream, crs);
        	} else if (eventType == XMLStreamReader.END_ELEMENT) {
        		break;
        	}
        }

        return result;
    }

    private ICRS getCRS ( XMLStreamReader xmlStream, ICRS defaultCRS ) {
    	ICRS activeCRS = defaultCRS;
        String srsName = xmlStream.getAttributeValue(null, "srsName");
        if (!(srsName == null || srsName.length() == 0)) {
            if (defaultCRS == null || !srsName.equals(defaultCRS.getAlias())) {
                activeCRS = CRSManager.getCRSRef(srsName);
            }
        }
        return activeCRS;
    }

    private boolean hasNumDimensions ( XMLStreamReader xmlStream ) {
    	String numDim = xmlStream.getAttributeValue(null, "srsDimension");
    	return (!(numDim == null || numDim.length() == 0));
    }

    private int getNumDimensions ( XMLStreamReader xmlStream, ICRS defaultCRS ) {
    	int numActiveDim = defaultCRS.getDimension();
    	String numDim = xmlStream.getAttributeValue(null, "srsDimension");
    	if (!(numDim == null || numDim.length() == 0)) {
    		numActiveDim = Integer.parseInt(numDim);
    	}
    	return numActiveDim;
    }
}
