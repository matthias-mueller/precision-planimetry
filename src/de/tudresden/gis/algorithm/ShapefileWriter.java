package de.tudresden.gis.algorithm;

import java.io.IOException;

import org.geotools.data.DefaultTransaction;
import org.geotools.data.Transaction;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.feature.DefaultFeatureCollection;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

/**
 * Convenience class for writing shapefiles.
 * Takes care of transactions and controls memory use between transactions.
 * 
 * @author matthias
 *
 */
public class ShapefileWriter {

	private final ShapefileDataStore dataStore;
	private final long maxAttribCount;
	DefaultFeatureCollection fCollection;
	private long attribSizeCount = 0;
	
	/**
	 * Constructor.
	 * 
	 * @param shpDS
	 * @param destType
	 * @param maxAttribSize - maximum attribute size (in Bytes)
	 * @throws IOException
	 */
	public ShapefileWriter(ShapefileDataStore shpDS, SimpleFeatureType destType, int maxAttribSize) throws IOException{
		dataStore = shpDS;
		this.maxAttribCount = maxAttribSize;
		
		fCollection = new DefaultFeatureCollection("internal", destType);
	}

	/**
	 * Thread-safe add method; adds new features to the internal collection and initiates a commit
	 * once the threshold maxAttribCount is reached
	 * 
	 * @param feature
	 * @throws IOException
	 */
	public synchronized void addFeature(SimpleFeature feature) throws IOException{
		// measure size of new Feature
		
//		vCount += ((Geometry)feature.getDefaultGeometry()).getNumPoints();
		attribSizeCount += feature.toString().length();
//		System.out.println(feature.toString());
		
		// add to FC
		fCollection.add(feature);

		// execute commit on demand
		if (attribSizeCount >= maxAttribCount){
			commit();
		}
	}
	
	/**
	 * Commit method; initiates the transaction and resets internal data objects
	 * 
	 * @throws IOException
	 */
	private void commit() throws IOException{
		if(fCollection.isEmpty()){
			return;
		}
		System.out.println("Writing");
		doTransaction();
		// reset data objects
		fCollection.clear();
		attribSizeCount = 0;
		System.gc();
	}
	
	/**
	 * The transaction method; writes features and clears the feature collection
	 * 
	 * @throws IOException
	 */
	private void doTransaction() throws IOException{
		// commit transaction
		Transaction transaction = new DefaultTransaction();
		SimpleFeatureStore featureStore = (SimpleFeatureStore) dataStore.getFeatureSource();
		featureStore.setTransaction(transaction);
		try {
			featureStore.addFeatures(fCollection);
			transaction.commit();
		} catch (Exception problem) {
			problem.printStackTrace();
			transaction.rollback();
		} finally {
			transaction.close();
		}
	}
	
	/**
	 * Destructor calls flush() to make sure the rest of the remaining features are written to the shapefile
	 */
	@Override
	protected void finalize() throws Throwable {
		commit();
		super.finalize();
	}
}
