package de.tudresden.gis.algorithm;


import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;

import net.sf.geographiclib.Geodesic;
import net.sf.geographiclib.PolygonArea;
import net.sf.geographiclib.PolygonResult;

import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.geometry.jts.Geometries;
import org.geotools.geometry.jts.JTS;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.GeometryDescriptor;
import org.opengis.geometry.MismatchedDimensionException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.crs.GeodeticCRS;
import org.opengis.referencing.crs.GeographicCRS;
import org.opengis.referencing.crs.ProjectedCRS;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.MultiLineString;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Polygon;

import de.tudresden.gis.algorithm.CRSUtils.CRSType;


/**
 * Planimetry functions. Computes area and perimeter of geographic shapes on an ellipsoid.
 * 
 * @author Matthias Mueller
 */
public class Planimeter {

	public static final String PERIM_FIELD_NAME = "length_m";
	public static final String AREA_FIELD_NAME = "area_m2";
	
	private static final int PERI_POS = 0;
	private static final int AREA_POS = 1;
	
	public static final int MAX_ATTRIB_SIZE = 1024 * 1024 * 50;

	public static void computePlanimetry(String shapeFileName, String outFileName) throws IOException {
		Geometries geometryType;
		MathTransform transform = null;
		Geodesic earth;

		// load shapefile
		File shapeFile = new File(shapeFileName);

		System.out.println("Reading shapefile: " + shapeFile.getAbsolutePath());
		Map<String, URL> map = new HashMap<String, URL>();
		map.put("url", shapeFile.toURI().toURL());

		DataStore dataStore = DataStoreFinder.getDataStore(map);

		// SimpleFeatureSource featureSource = dataStore.getFeatureSource(shpName); this works too
		SimpleFeatureSource featureSource = dataStore.getFeatureSource(dataStore.getTypeNames()[0]);        

		geometryType = findOutShapefileGeometryType(featureSource);
		System.out.println("Shapefile's geometry type is: " + geometryType.getName());

		// check CRS properties
		CoordinateReferenceSystem srcCrs = featureSource.getSchema().getCoordinateReferenceSystem();

		CRSType crsType = CRSUtils.getCRSType(srcCrs);
		switch (crsType) {
		case ProjectedCRS:
			transform = CRSUtils.getTransformation((ProjectedCRS) srcCrs);
			// if we haven't found a transform, we are stuck and cannot get on
			if (transform == null){
				throw new IllegalArgumentException("Unsupported coordinate reference system type - cannot transform to geographic coordinates.");
			}
			earth = CRSUtils.getEarth((ProjectedCRS) srcCrs);
			break;
		case GeographicCRS:
			// no transformation required
			earth = CRSUtils.getEarth((GeographicCRS) srcCrs);
			break;
		case GeodeticCRS:
			// no transformation required
			earth = CRSUtils.getEarth((GeodeticCRS) srcCrs);
			break;
		default:
			// return an error; other CRSes are not supported
			throw new IllegalArgumentException("Unsupported coordinate reference system type");
		}

		SimpleFeatureCollection collection = featureSource.getFeatures();
		reportExtent(collection);

		// create new Shapefile DS for writing
		SimpleFeatureType destType = createDestType(featureSource.getSchema(), geometryType);
		ShapefileDataStore dstDS = createShapefile(outFileName, destType);
		ShapefileWriter shpWriter = new ShapefileWriter(dstDS, destType, MAX_ATTRIB_SIZE);
		
		// initialize variable for reading / writing in the loop
//		DefaultFeatureCollection dstCollection = new DefaultFeatureCollection("internal", destType);
		SimpleFeatureBuilder sfb = new SimpleFeatureBuilder(destType);
		SimpleFeatureIterator features = collection.features();
		SimpleFeature feature;
		while (features.hasNext()){
			feature = features.next();
			SimpleFeature newFeature = processFeature(feature, sfb, geometryType, earth, transform);
//			dstCollection.add(newFeature);
			shpWriter.addFeature(newFeature);
		}

		// commit transaction
//		Transaction transaction = new DefaultTransaction();
//		SimpleFeatureStore featureStore = (SimpleFeatureStore) dstDS.getFeatureSource();
//		featureStore.setTransaction(transaction);
//		try {
//            featureStore.addFeatures(dstCollection);
//            transaction.commit();
//        } catch (Exception problem) {
//            problem.printStackTrace();
//            transaction.rollback();
//        } finally {
//            transaction.close();
//        }

		dstDS.dispose();
		features.close();
		dataStore.dispose();
	}

	private static SimpleFeature processFeature(SimpleFeature feature, SimpleFeatureBuilder sfb, Geometries geometryType, Geodesic earth, MathTransform transform){
		
		// transform feature geometry if necessary
		Geometry geom;
		if (transform != null){
			try {
				geom = JTS.transform((Geometry)feature.getDefaultGeometry(), transform);
			} catch (MismatchedDimensionException | TransformException e) {
				throw new IllegalArgumentException("Cannot transform geometries - aborting feature processing.");
			}
		} else {
			geom = (Geometry)feature.getDefaultGeometry();
		}
		
		// copy known attributes to dest feature
		sfb.addAll(feature.getAttributes());
		
		// process the feature
		switch (geometryType) {
		case MULTILINESTRING:
			double perim = computeMultiLinePlanimetry((MultiLineString)geom, earth);
			// write perim field
			sfb.set(PERIM_FIELD_NAME, perim);
			break;
		case MULTIPOLYGON:
			double[] planimet = computeMultiPolygonPlanimetry((MultiPolygon)geom, earth);
			// write perim and area fields
			sfb.set(PERIM_FIELD_NAME, planimet[PERI_POS]);
			sfb.set(AREA_FIELD_NAME, planimet[AREA_POS]);
			break;
		default:
			// do nothing; only with POINT we might arrive here
			break;
		}

		// build feature and return
		SimpleFeature retval = sfb.buildFeature(null);
		return retval;
	}


	/**
	 * Retrieve information about the feature geometry
	 */
	private static Geometries findOutShapefileGeometryType(SimpleFeatureSource featureSource){

		GeometryDescriptor geomDesc = featureSource.getSchema().getGeometryDescriptor();

		Class<?> clazz = geomDesc.getType().getBinding();

		if (Polygon.class.isAssignableFrom(clazz) || MultiPolygon.class.isAssignableFrom(clazz)) {
			return Geometries.MULTIPOLYGON;

		} else if (LineString.class.isAssignableFrom(clazz) || MultiLineString.class.isAssignableFrom(clazz)) {
			return Geometries.MULTILINESTRING;

		} else {
			return Geometries.POINT;
		}
	}


	/**
	 * Creates a Shapefile datastore
	 * 
	 * @param fileName
	 * @param featureType
	 * @return
	 * @throws IOException
	 */
	private static ShapefileDataStore createShapefile(String fileName, SimpleFeatureType featureType) throws IOException{
		File file = new File(fileName);
		ShapefileDataStore store = new ShapefileDataStore(file.toURI().toURL());
		store.createSchema(featureType);

		/**
		 * further attributes documented here:
		 * http://docs.geotools.org/stable/userguide/library/data/shape.html
		 */
		//		store.setMemoryMapped(true);
		//		store.setCharset(Charset.forName("UTF-8"));
		//		store.setTimeZone(TimeZone.getTimeZone("GMT"));
		//		store.setNamespaceURI("theNamespace");

		return store;
	}


	private static SimpleFeatureType createDestType(SimpleFeatureType sourceType, Geometries geometryType){
		//Create the new type using the former as a template
		SimpleFeatureTypeBuilder stb = new SimpleFeatureTypeBuilder();
		stb.init(sourceType);
		//stb.setName("newFeatureType");

		//Add the new attributes
		switch (geometryType) {
		case MULTILINESTRING:
			// add perim field
			stb.add(PERIM_FIELD_NAME, Double.class);
			break;
		case MULTIPOLYGON:
			stb.add(PERIM_FIELD_NAME, Double.class);
			stb.add(AREA_FIELD_NAME, Double.class);
			break;
		default:
			// do nothing; only with POINT we should get here
			break;
		}
		SimpleFeatureType newFeatureType = stb.buildFeatureType(); 

		return newFeatureType;
	}
	
	private static final double[] computePolygonPlanimetry(final Polygon polygon, final Geodesic earth){
		double perimeter = 0.0;
		double area = 0.0;
		
		int geomCnt = polygon.getNumGeometries();
		PolygonArea pArea = new PolygonArea(earth, false);
		// compute outer ring
		for (Coordinate c : polygon.getGeometryN(0).getCoordinates()){
			// p.AddPoint(lat, lon);
			pArea.AddPoint(c.y, c.x);
		}
		
		PolygonResult r = pArea.Compute();
		perimeter += Math.abs(r.perimeter);
		area += Math.abs(r.area);
		
		// handle holes
		if (geomCnt > 1){
			for (int i=1; i<geomCnt; i++){
				pArea = new PolygonArea(earth, false);
				for (Coordinate c : polygon.getGeometryN(i).getCoordinates()){
					// p.AddPoint(lat, lon);
					pArea.AddPoint(c.y, c.x);
				}
				r = pArea.Compute();
				perimeter += Math.abs(r.perimeter);
				area -= Math.abs(r.area);
			}
		}
		
		// assemble result array
		double[] retval = new double[2];
		retval[PERI_POS] = perimeter;
		retval[AREA_POS] = area;
		return retval;
	}
	
	private static final double[] computeMultiPolygonPlanimetry(final MultiPolygon multiPoly, final Geodesic earth){
		
		double perimeter = 0.0;
		double area = 0.0;
		
		
		// extract the polygons and compute their planimetry
		for (int i=0; i<multiPoly.getNumGeometries(); i++){
			double[] planimet = computePolygonPlanimetry((Polygon) multiPoly.getGeometryN(i), earth);
			perimeter += planimet[PERI_POS];
			area += planimet[AREA_POS];
		}
		
		// assemble result array
		double[] retval = new double[2];
		retval[PERI_POS] = perimeter;
		retval[AREA_POS] = area;
		return retval;
	}
	
	private static final double computeLinePlanimetry(final LineString line, final Geodesic earth){
		
		PolygonArea pArea = new PolygonArea(earth, true);
		// compute outer ring
		for (Coordinate c : line.getGeometryN(0).getCoordinates()){
			// p.AddPoint(lat, lon);
			pArea.AddPoint(c.y, c.x);
		}
		
		PolygonResult r = pArea.Compute();
		
		return Math.abs(r.perimeter);
	}
	
	private static final double computeMultiLinePlanimetry(final MultiLineString multiLine, final Geodesic earth){
		
		double perimeter = 0.0;
		
		// extract the line strings and compute their planimetry
		for (int i=0; i<multiLine.getNumGeometries(); i++){
			perimeter += computeLinePlanimetry((LineString)multiLine.getGeometryN(i), earth);
		}
		
		return perimeter;
	}
	
	
	/**
	 * BBOx reporting function
	 * 
	 * @param collection
	 */
	private static void reportExtent(FeatureCollection<SimpleFeatureType, SimpleFeature> collection){
		ReferencedEnvelope env = collection.getBounds();
		double left = env.getMinX();
		double right = env.getMaxX();
		double top = env.getMaxY();
		double bottom = env.getMinY();

		System.out.println("Extent: " + left + " .. " + right + " ;  " + top + " .. " + bottom);
	}

	public static void main(String[] args) throws IOException, InterruptedException{
		String syntaxHint = "planimeter <source.shp> <dest.shp>";
		String syntaxError = "Wrong syntax!";
		
		List<String> argsList = new ArrayList<String>();
		
		if(args.length != 2){
			System.out.println(syntaxError +"\n" + syntaxHint);
		}
		
		for (String arg : args){
			argsList.add(arg);
		}
		
//		String shapeFileName ="data/VG250_Gemeinden.shp";
//		String shapeFileName ="data/roads.shp";
//		String outFileName ="data/out.shp";
		try {
			computePlanimetry(argsList.get(0), argsList.get(1));
		} catch (Exception e){
			System.out.println(e.getMessage());
			System.exit(1);
		}
		
	}



}