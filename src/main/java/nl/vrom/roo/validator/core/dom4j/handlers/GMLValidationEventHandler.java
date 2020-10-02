package nl.vrom.roo.validator.core.dom4j.handlers;

import nl.vrom.roo.validator.core.ValidatorContext;
import nl.vrom.roo.validator.core.ValidatorMessageBundle;
import nl.vrom.roo.validator.core.dom4j.Dom4JHelper;
import org.deegree.geometry.primitive.Curve;
import org.deegree.geometry.primitive.Point;
import org.deegree.geometry.primitive.patches.PolygonPatch;
import org.deegree.geometry.primitive.segments.CurveSegment;
import org.deegree.geometry.validation.GeometryValidationEventHandler;
import org.deegree.geometry.validation.event.*;
import org.dom4j.Element;

import java.util.List;

class GMLValidationEventHandler implements GeometryValidationEventHandler {

	
		private final ValidatorContext validatorContext;
		private final String currentGmlId;
		private final String currentOnderdeelName;
		private boolean ignoreRingRotation;

		
		public GMLValidationEventHandler(ValidatorContext validatorContext, Element element, boolean ignoreRingRotation)
		{
			this.validatorContext = validatorContext;
			this.currentGmlId = Dom4JHelper.findGmlId(element);
			this.currentOnderdeelName = Dom4JHelper.findPlanOnderdeel(element);
			this.ignoreRingRotation = false;
		}
		

	    
		@Override
		public boolean fireEvent(GeometryValidationEvent event) {
			
			if(event instanceof CurveDiscontinuity) {
				
				return curveDiscontinuity( (CurveDiscontinuity) event );
			}
			else if(event instanceof RingNotClosed) {
				
				return ringNotClosed( (RingNotClosed) event );
			}
			else if(event instanceof CurveSelfIntersection) {
				// Currently handled by separate JTS validation
				return true;
//				return curveSelfIntersection((CurveSelfIntersection) event);
			}
			else if(event instanceof DuplicatePoints) {
				// Ignore. 
				return true;
			}
			else if(event instanceof InteriorRingIntersectsExterior) {
				// Currently handled by separate JTS validation			
				return true;
//				return interiorRingIntersectsExterior((InteriorRingIntersectsExterior) event);
			}
			else if(event instanceof InteriorRingOutsideExterior) {
				// Currently handled by separate JTS validation
				return true;
//				return interiorRingOutsideExterior((InteriorRingOutsideExterior) event);
			}
			else if(event instanceof InteriorRingsIntersect) {
				
				// Currently handled by separate JTS validation			
				return true;
//				return interiorRingsIntersect((InteriorRingsIntersect) event);
			}
			else if(event instanceof InteriorRingsNested) {
				// Currently handled by separate JTS validation			
				return true;
//				return interiorRingsWithin((InteriorRingsNested) event);
			}
			else if(event instanceof InteriorRingsTouch) {
				
				// Currently handled by separate JTS validation			
				return true;
//				return interiorRingsTouch( (InteriorRingsTouch) event);
			}
			else if(event instanceof InteriorRingTouchesExterior) {
				
				// Currently handled by separate JTS validation			
				return true;
//				return interiorRingTouchesExterior( (InteriorRingTouchesExterior) event);
			}
			else if(event instanceof ExteriorRingOrientation) {
				if(ignoreRingRotation) {
					return true;
				}
				
				return exteriorRingOrientation( (ExteriorRingOrientation) event); 
			}
			else if(event instanceof InteriorRingOrientation) {
				if(ignoreRingRotation) {
					return true;
				}

				return interiorRingOrientation((InteriorRingOrientation) event);
			}
			else {
				return unknownEvent(event);
			}
		}
	    
	    boolean unknownEvent(GeometryValidationEvent event) {

	    	// An event we do not know about. 
	    	String errMessage = ValidatorMessageBundle.getMessage(
	    			"validator.core.validation.geometry.event.unexpected", 
    			this.currentGmlId,
    			this.currentOnderdeelName,
    			event.getClass().getCanonicalName());
	    	
			this.validatorContext.addWarning(errMessage);
			return true;
		}



		boolean curveDiscontinuity( CurveDiscontinuity evt )  {
	    	
			Curve curve = evt.getCurve();
			int segmentIdx = evt.getEndPointSegmentIndex();

			Point endPoint = curve.getCurveSegments().get(segmentIdx-1).getEndPoint();
			Point startPoint = curve.getCurveSegments().get(segmentIdx).getStartPoint();
			
			String een = "validator.core.validation.geometry.curvediscontinuity";
			String twee = this.currentGmlId;
			String drie = this.currentOnderdeelName;
			String vier = ValidationUtil.getAffectedCoordinates(curve.getCurveSegments().get(segmentIdx), 60);
			String vijf = ValidationUtil.getProblemLocation(startPoint);
			int zes = segmentIdx;
			String zeven = ValidationUtil.getProblemLocation(endPoint);
			
			String errMessage = ValidatorMessageBundle.getMessage(
	    			een,
	    			twee,
	    			drie,
	    			vier,
	    			vijf,
	    			zes,
	    			zeven);
			
	    	this.validatorContext.addError(errMessage); 
			
			return false;
	    }


//	    boolean curveSelfIntersection( CurveSelfIntersection evt ) {
//
//	    	Curve curve = evt.getCurve();
//			Point problem = evt.getLocation();
//
//	    	String errMessage = ValidatorMessageBundle.getMessage(
//	    			"validator.core.validation.geometry.curveselfintersection", 
//	    			this.currentGmlId,
//	    			this.currentOnderdeelName,
//	    			getAffectedCoordinates(curve, 60),
//	    			getProblemLocation(problem));
//
//	    	this.validatorContext.addError(errMessage); 
//			return false;
//	    }
	    
	    boolean exteriorRingOrientation( ExteriorRingOrientation evt ) {

	    	PolygonPatch patch = evt.getPatch();
			boolean isExterior = evt.isExterior();
		
			if(!isExterior) {
				String errMessage = ValidatorMessageBundle.getMessage(
						"validator.core.validation.geometry.exteriorRingCW",
						this.currentGmlId,
						this.currentOnderdeelName,
						ValidationUtil.getAffectedCoordinates(patch.getExteriorRing(), 60));
				this.validatorContext.addError(errMessage);
			}

			return isExterior;
	    }
	    
		boolean interiorRingOrientation( InteriorRingOrientation evt ) {

	    	PolygonPatch patch = evt.getPatch();
			boolean isInterior = evt.isInterior();

			if(!isInterior) {
				int idx = evt.getRingIdx();
				String errMessage = ValidatorMessageBundle.getMessage(
						"validator.core.validation.geometry.interiorRingCCW",
						this.currentGmlId,
						this.currentOnderdeelName,
						ValidationUtil.getAffectedCoordinates(patch.getInteriorRings().get(idx), 60),
						idx);
				this.validatorContext.addError(errMessage);
			}

			return isInterior;
		}
		
//		boolean interiorRingIntersectsExterior( InteriorRingIntersectsExterior evt) {
//			
//			
//			if(evt.isSinglePoint()) {
//				// This is permitted because it is a touching situation in only one point;
//				return true;
//			}
//			
//			int ringIdx = evt.getRingIdx();
//			Point problem = evt.getLocation();
//			
//			String errMessage = ValidatorMessageBundle.getMessage(
//	    			"validator.core.validation.geometry.interiorringintersectsexterior", 
//	    			this.currentGmlId,
//	    			this.currentOnderdeelName,
//	    			getAffectedCoordinates(evt.getPatch().getInteriorRings().get(ringIdx), 60),
//	    			ringIdx, 
//	    			getProblemLocation(problem));
//
//	    	this.validatorContext.addError(errMessage); 
//	    	
//			return false;
//		}
//		
//	    boolean interiorRingOutsideExterior( InteriorRingOutsideExterior evt ) {
//
//			PolygonPatch patch = evt.getPatch();
//			int ringIdx = evt.getRingIdx();
//			
//	    	String errMessage = ValidatorMessageBundle.getMessage(
//	    			"validator.core.validation.geometry.interiorringoutsideexterior", 
//	    			this.currentGmlId,
//	    			this.currentOnderdeelName,
//	    			getAffectedCoordinates(patch.getInteriorRings().get(ringIdx), 60),
//	    			ringIdx);
//
//	    	this.validatorContext.addError(errMessage); 
//			return false;
//	    }
//	    
//		boolean interiorRingTouchesExterior( InteriorRingTouchesExterior evt) {
//
//			// The Deegree3 validation software in method org.deegree.geometry.validation.GeometryValidator.validatePatch( PolygonPatch patch, List<Object> affectedGeometryParticles )
//			// does not produce this type of event so we don't need to check it.
//			// Besides this event does not contain enough information because it does not tell whether only one point or more than one point is touching.
////			InteriorRingTouchesExterior evt = (InteriorRingTouchesExterior) event;
////			
////			evt.getGeometryParticleHierarchy();
////			evt.getPatch();
////			evt.getRingIdx();
////			evt.getLocation();
//			return true;
//		}
//	    
//		boolean interiorRingsIntersect( InteriorRingsIntersect evt ) {
//			
//			if(evt.isSinglePoint()) {
//				// Ignore this. It is permitted to touch in only one point.
//				return true;
//			}
//			
//			PolygonPatch patch = evt.getPatch();
//			int ring1Idx = evt.getRing1Idx();
//			int ring2Idx = evt.getRing2Idx();
//			Point problem = evt.getLocation();
//			
//	    	String errMessage = ValidatorMessageBundle.getMessage(
//	    			"validator.core.validation.geometry.interiorringsintersect", 
//	    			this.currentGmlId,
//	    			this.currentOnderdeelName,
//	    			getAffectedCoordinates(patch.getInteriorRings().get(ring1Idx), 60),
//	    			getAffectedCoordinates(patch.getInteriorRings().get(ring2Idx), 60),
//	    			getProblemLocation(problem));
//
//	    	this.validatorContext.addError(errMessage); 
//	    	
//			return false;
//		}
//
//		boolean interiorRingsTouch( InteriorRingsTouch evt ) {
//			
//			// The Deegree3 validation software in method org.deegree.geometry.validation.GeometryValidator.validatePatch( PolygonPatch patch, List<Object> affectedGeometryParticles )
//			// does not produce this type of event so we don't need to check it.
//			// Besides this event does not contain enough information because it does not tell whether only one point or more than one point is touching.
//
////			InteriorRingsTouch evt = (InteriorRingsTouch) event;
////
////			evt.getGeometryParticleHierarchy();
////			evt.getPatch();
////			evt.getRing1Idx();
////			evt.getRing2Idx();
////			evt.getLocation();
//			return true;
//		}
//		
//	    boolean interiorRingsWithin( InteriorRingsNested evt ) {
//
//			PolygonPatch patch = evt.getPatch();
//			int ring1Idx = evt.getRing1Idx();
//			int ring2Idx = evt.getRing2Idx();
//	    	
//			String errMessage = ValidatorMessageBundle.getMessage(
//	    			"validator.core.validation.geometry.interiorringswithin", 
//	    			this.currentGmlId,
//	    			this.currentOnderdeelName,
//	    			getAffectedCoordinates(patch.getInteriorRings().get(ring1Idx), 60),
//	    			getAffectedCoordinates(patch.getInteriorRings().get(ring2Idx), 60),
//	    			ring1Idx, ring2Idx);
//
//	    	this.validatorContext.addError(errMessage); 
//			return false;
//	    }

	    boolean ringNotClosed( RingNotClosed evt )  {
			
			List<CurveSegment> curveSegments = evt.getRing().getCurveSegments();
			
			Point startPoint = curveSegments.get(0).getStartPoint();
			Point endPoint = curveSegments.get(curveSegments.size()-1).getEndPoint();
			
			
			String errMessage = ValidatorMessageBundle.getMessage(
					"validator.core.validation.geometry.ringnotclosed", 
	    			this.currentGmlId,
	    			this.currentOnderdeelName,
	    			ValidationUtil.getAffectedCoordinates(evt.getRing(), 60),
	    			ValidationUtil.getProblemLocation(startPoint),
	    			ValidationUtil.getProblemLocation(endPoint));

	    	this.validatorContext.addError(errMessage); 
			
			return false;
	    }
//
//		
//		private String getProblemLocationText(Point problem) {
//			
//			return ValidatorMessageBundle.getMessage(
//					"validator.core.validation.geometry.problem-location-text", 
//							formatValue(problem.get0()), formatValue(problem.get1()) );
//		}
		
		
//		private String generateCoordinatesText(Point[] points, Point problem) {
//			String coordinatesText = null;
//
//			if (points.length > 0) {
//				Point coordBegin = points[0];
//				Point coordEnd = points[points.length - 1];
//
//				if (problem == null) {
//					coordinatesText = ValidatorMessageBundle.getMessage(
//							"validator.core.validation.geometry.coordinates-text-simple", 
//									formatValue(coordBegin.get0()), formatValue(coordBegin.get1()), formatValue(coordEnd.get0()),
//									formatValue(coordEnd.get1()) );
//				} else {
//					coordinatesText = ValidatorMessageBundle.getMessage(
//							"validator.core.validation.geometry.coordinates-text", 
//									formatValue(coordBegin.get0()), formatValue(coordBegin.get1()), formatValue(coordEnd.get0()),
//									formatValue(coordEnd.get1()), formatValue(problem.get0()), formatValue(problem.get1()) );
//				}
//			}
//			return coordinatesText;
//		}
	}
