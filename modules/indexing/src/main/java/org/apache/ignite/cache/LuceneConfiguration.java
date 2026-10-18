package org.apache.ignite.cache;

import org.apache.ignite.plugin.PluginConfiguration;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.VectorEncoding;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.h2.util.Utils;
import java.util.HashMap;
import java.util.Map;

/**
 * lucene index config for doc type. 
 * one indexes per type. multiple type per cache will gen multiple indexes.
 * 
 * @author Hunteron-cp
 *
 */
public class LuceneConfiguration implements PluginConfiguration {
	boolean offHeapStore = false; // 是否使用offheap存储索引

	boolean storeValue = false; // 存储_val
	/**
	 * Whether the text content should be stored in the Lucene index.
	 */
	boolean storeTextFieldValue = false; // 存储索引字段
	
	boolean persistenceEnabled = true; // cache是否开启了持久化

	// default index analyzer
	private Analyzer indexAnalyzer;

	// default query analyzer
	private Analyzer queryAnalyzer;

	// default Vector Similarity Function
	private VectorSimilarityFunction vectorSimilarityFunction = VectorSimilarityFunction.COSINE;

	private VectorEncoding vectorEncoding = VectorEncoding.FLOAT32;

	private int dimensions = 1024;
	
	private Map<String, Analyzer> fieldAnalyzerMap = new HashMap<>();

	public LuceneConfiguration() {
		this.storeTextFieldValue = Utils.getProperty("h2.storeDocumentTextInIndex", false);
		this.storeValue = Utils.getProperty("h2.storeValueInIndex", false);		
	}

	public LuceneConfiguration(LuceneConfiguration copy) {
		this.offHeapStore = copy.offHeapStore;
		this.storeTextFieldValue = copy.storeTextFieldValue;
		this.storeValue = copy.storeValue;
		this.persistenceEnabled = copy.persistenceEnabled;
		this.fieldAnalyzerMap = new HashMap<>(copy.fieldAnalyzerMap);
		this.indexAnalyzer = copy.indexAnalyzer;
		this.queryAnalyzer = copy.queryAnalyzer;
		this.dimensions = copy.dimensions;
		this.vectorEncoding = copy.vectorEncoding;
		this.vectorSimilarityFunction = copy.vectorSimilarityFunction;
	}
	
	public boolean isPersistenceEnabled() {
		return persistenceEnabled;
	}

	public void setPersistenceEnabled(boolean persistenceEnabled) {
		this.persistenceEnabled = persistenceEnabled;
	}
	
	public Analyzer getIndexAnalyzer() {
		return indexAnalyzer;
	}

	public void setIndexAnalyzer(Analyzer indexAnalyzer) {		
		this.indexAnalyzer = indexAnalyzer;
	}

	public Analyzer getQueryAnalyzer() {
		return queryAnalyzer;
	}

	public void setQueryAnalyzer(Analyzer queryAnalyzer) {
		this.queryAnalyzer = queryAnalyzer;
	}	

	public boolean isStoreTextFieldValue() {
		return storeTextFieldValue;
	}

	public void setStoreTextFieldValue(boolean storeTextFieldValue) {
		this.storeTextFieldValue = storeTextFieldValue;
	}

	public boolean isOffHeapStore() {
		return offHeapStore;
	}

	public void setOffHeapStore(boolean memeryStore) {
		this.offHeapStore = memeryStore;
	}

	public boolean isStoreValue() {
		return storeValue;
	}

	public void setStoreValue(boolean storeValue) {
		this.storeValue = storeValue;
	}

	public Map<String, Analyzer> getFieldAnalyzerMap() {
		return fieldAnalyzerMap;
	}

	public void setFieldAnalyzerMap(Map<String, Analyzer> fieldAnalyzerMap) {
		this.fieldAnalyzerMap = fieldAnalyzerMap;
	}

	public VectorSimilarityFunction getVectorSimilarityFunction() {
		return vectorSimilarityFunction;
	}

	public void setVectorSimilarityFunction(VectorSimilarityFunction similarityFunction) {
		this.vectorSimilarityFunction = similarityFunction;
	}

	public int getDimensions() {
		return dimensions;
	}

	public void setDimensions(int dimensions) {
		this.dimensions = dimensions;
	}


	public VectorEncoding getVectorEncoding() {
		return vectorEncoding;
	}

	public void setVectorEncoding(VectorEncoding vectorEncoding) {
		this.vectorEncoding = vectorEncoding;
	}
}
