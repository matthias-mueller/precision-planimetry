package de.tudresden.gis.algorithm;

import net.sf.geographiclib.Geodesic;

import org.geotools.referencing.CRS;
import org.opengis.referencing.crs.CompoundCRS;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.crs.DerivedCRS;
import org.opengis.referencing.crs.EngineeringCRS;
import org.opengis.referencing.crs.GeneralDerivedCRS;
import org.opengis.referencing.crs.GeocentricCRS;
import org.opengis.referencing.crs.GeodeticCRS;
import org.opengis.referencing.crs.GeographicCRS;
import org.opengis.referencing.crs.ImageCRS;
import org.opengis.referencing.crs.ProjectedCRS;
import org.opengis.referencing.crs.TemporalCRS;
import org.opengis.referencing.crs.VerticalCRS;
import org.opengis.referencing.datum.Ellipsoid;
import org.opengis.referencing.operation.MathTransform;

//import org.geotools.resources.CRSUtilities;

public class CRSUtils {
	
	public static CRSType getCRSType(CoordinateReferenceSystem crs){
		if (crs instanceof CompoundCRS){
			return CRSType.CompoundCRS;
		}
		if (crs instanceof DerivedCRS){
			return CRSType.DerivedCRS;
		}
		if (crs instanceof EngineeringCRS){
			return CRSType.EngineeringCRS;
		}
		if (crs instanceof GeneralDerivedCRS){
			return CRSType.GeneralDerivedCRS;
		}
		if (crs instanceof GeocentricCRS){
			return CRSType.GeocentricCRS;
		}
		if (crs instanceof GeodeticCRS){
			return CRSType.GeodeticCRS;
		}
		if (crs instanceof GeographicCRS){
			return CRSType.GeographicCRS;
		}
		if (crs instanceof ImageCRS){
			return CRSType.ImageCRS;
		}
		if (crs instanceof ProjectedCRS){
			return CRSType.ProjectedCRS;
		}
		if (crs instanceof TemporalCRS){
			return CRSType.TemporalCRS;
		}
		if (crs instanceof VerticalCRS){
			return CRSType.VerticalCRS;
		}
		return null;
		
		// CompoundCRS, DerivedCRS, EngineeringCRS, GeneralDerivedCRS, GeocentricCRS, GeodeticCRS,
		// GeographicCRS, ImageCRS, ProjectedCRS, TemporalCRS, VerticalCRS
	}
	
	public static final MathTransform getTransformation(ProjectedCRS crs){
		CoordinateReferenceSystem targetCRS = crs.getBaseCRS();
//		MathTransform transform = CRS.findMathTransform(sourceCRS, targetCRS, true);
		try {
			return CRS.findMathTransform(crs, targetCRS);
		} catch (Exception e) {
			// do nothing; return null
			return null;
		}
	}
	
	
	public static final Geodesic getEarth(ProjectedCRS crs){
		Ellipsoid e = crs.getBaseCRS().getDatum().getEllipsoid();
		return new Geodesic(e.getSemiMajorAxis(), 1.0d/e.getInverseFlattening());
	}
	
	public static final Geodesic getEarth(GeographicCRS crs){
		Ellipsoid e = crs.getDatum().getEllipsoid();
		return new Geodesic(e.getSemiMajorAxis(), 1.0d/e.getInverseFlattening());
	}
	
	public static final Geodesic getEarth(GeodeticCRS crs){
		Ellipsoid e = crs.getDatum().getEllipsoid();
		return new Geodesic(e.getSemiMajorAxis(), 1.0d/e.getInverseFlattening());
	}
	
	public enum CRSType{
		CompoundCRS, DerivedCRS, EngineeringCRS, GeneralDerivedCRS, GeocentricCRS, GeodeticCRS, GeographicCRS, ImageCRS, ProjectedCRS, TemporalCRS, VerticalCRS;
	}
}
