/*
 * Copyright 2016 Searsia
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.searsia.index;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

import org.apache.log4j.Logger;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;
import org.json.JSONException;
import org.json.JSONObject;
import org.searsia.engine.Resource;

/**
 * Stores resources in a Lucene index
 *
 * @author Djoerd Hiemstra
 */
public class ResourceIndex {

    private final static Logger LOGGER = Logger.getLogger(ResourceIndex.class.getName());

    private final static Version version      = Version.LUCENE_4_10_4;
    private final static int MAX_SOURCE_CACHE = 10000; // TODO: breaks if we discover more than 10000 sources
    
	private Map<String,Resource> engines = new LinkedHashMap<String,Resource>();
	private Random random   = new Random();
	private Resource mother = null;
	private Resource me     = null;
	private Path meFile     = null;
	private Path indexDir   = null;
	private IndexWriter writer = null;
	private String lastFlushed = null;

	/**
	 * Reads resources from index (if they exist)
	 * @param path path where the Searsia index resides
	 * @param filename index file name
	 * @throws IOException
	 * @throws JSONException 
	 */
	public ResourceIndex(String path, String filename) throws IOException, JSONException {
		this.meFile   = Paths.get(path, filename + ".json");
		this.indexDir = Paths.get(path, filename + "_sources");
		if (meFile.toFile().exists()) {
    		this.me = readMyselfFile(meFile);
		}
		if (this.indexDir.toFile().exists()) {
			readResourceIndex();			
		} else {
			this.indexDir.toFile().mkdir();
		}
		initResourceIndex();
	}


	private void writeMyselfFile(Resource engine) throws IOException {
	    JSONObject me = engine.toJson();
	    if (this.mother != null) {
    	    me.put("motherTemplate", this.mother.getAPITemplate());
	    }
	    Files.write(this.meFile, engine.toJson().toString().getBytes());
	}


	private Resource readMyselfFile(Path meFile) throws IOException, JSONException {
		String content = new String(Files.readAllBytes(meFile));
		Resource me = null;
		try {
			JSONObject json = new JSONObject(content);
			me = new Resource(json);
		} catch (javax.xml.xpath.XPathExpressionException e) {
			throw new IOException(e);
		}
		return me;
	}


	private void readResourceIndex() throws IOException {
        IndexReader reader = null;
        Directory dir = FSDirectory.open(this.indexDir.toFile());
        try {
            reader = DirectoryReader.open(dir); 
        }
        catch (org.apache.lucene.index.IndexNotFoundException e) { 
            LOGGER.warn("No resources in index.");
            return;
        }
        try {
            IndexSearcher searcher = new IndexSearcher(reader);
            ScoreDoc[] hits = searcher.search(new MatchAllDocsQuery(), MAX_SOURCE_CACHE).scoreDocs;
            for (ScoreDoc hit: hits) {
                Document doc = searcher.doc(hit.doc);
                JSONObject json = new JSONObject(doc.get("json"));
                Resource engine = new Resource((JSONObject) json.get("resource"));
                if (json.has("health")) {
                    engine.updateHealth((JSONObject) json.get("health"));
                    String lastUpdated = engine.getLastUpdatedString();
                    if (this.lastFlushed == null || this.lastFlushed.compareTo(lastUpdated) < 0) {
                        this.lastFlushed = lastUpdated;
                    }
                }
                this.engines.put(engine.getId(), engine);
            }
        } catch (Exception e) {
        	throw new IOException(e.getMessage());
        }
        finally {
            reader.close(); 
        }
	}

	
	private void initResourceIndex() throws IOException {
        Directory dir = FSDirectory.open(indexDir.toFile());
        StandardAnalyzer indexAnalyzer   = new StandardAnalyzer();
        IndexWriterConfig indexConfig    = new IndexWriterConfig(version, indexAnalyzer);
        indexConfig.setOpenMode(OpenMode.CREATE_OR_APPEND);
        this.writer = new IndexWriter(dir, indexConfig);
	}
	
	
	/**
	 * Gets the mother engine (the trusted engine in the network)
	 * @return mother engine
	 */
	public Resource getMother() {
		return this.mother;
	}
	
	/**
	 * Get information about this Searsia engine	
	 * @return engine
	 */
	public Resource getMyself() {
        return this.me;
	}
	

	public void delete(String id) throws IOException {
		Resource engine = get(id);
		if (engine == null) {
			throw new IOException("Resouce '" + id + "' not found");
		}
		this.engines.remove(id);
		this.writer.deleteDocuments(new Term("id", id));
        this.writer.commit();
	}
	
		
	public void put(Resource engine) {
		if (this.mother != null && engine.getId().equals(this.mother.getId())) {
			throw new RuntimeException("Mother id conflict: " + engine.getId());
		}
		if (this.me != null && engine.getId().equals(this.me.getId())) {
			throw new RuntimeException("Local id conflict: " + engine.getId());
		}
		Resource old = get(engine.getId());
		if (old == null) { 
            this.engines.put(engine.getId(), engine);
		} else {
		    old.updateWith(engine);
		}
	}
	
	public boolean containsKey(String id) {
		return this.engines.containsKey(id);
	}
	
	
	public Resource get(String id) {
   		return this.engines.get(id);
	}
	
	public Resource getRandom() {
	    Object[] keys = this.engines.keySet().toArray();
	    if (keys.length > 0) {
            int nr = random.nextInt(keys.length);
	        return this.engines.get(keys[nr]);
	    } else {
	    	return getMother();
	    }
	}
	
	// Efficiency can be gained here?
	public Map<String, Float> topValues(String queryString, int max) {
        Float[] topScores = new Float[max];
		Resource[] topEngines = new Resource[max];
		int size = 0;
		float lastScore = -99.0f;
		for (Resource engine: this.engines.values()) {
		    float score = engine.score(queryString) + engine.getPrior();
	        if (size < max || score > lastScore) {
	            if (size < max) size++;
	            int index = size - 1;
	            while(index > 0 && topScores[index - 1] < score) {
	            	topScores[index]  = topScores[index - 1];
	                topEngines[index] = topEngines[index - 1];
	                index -= 1;
	            }
	            topScores[index] = score;
	            topEngines[index] = engine;
	            lastScore = topScores[size - 1];
	        }
		}
		Map<String, Float> result = new LinkedHashMap<String, Float>();
		for (int i=0; i < size; i += 1) {
			result.put(topEngines[i].getId(), topScores[i]);
		}
		return result; 
	}
	
	public void putMother(Resource mother) {
        mother.setLastUpdatedToNow();
        if (this.mother == null) {
            this.mother = mother;
        } else {
            this.mother.updateWith(mother);
        }
	}
	
	public void putMyself(Resource me) {
		if (get(me.getId()) != null) {
			throw new RuntimeException("The server id '" + me.getId() + "' already exists.");
		}
        me.setLastUpdatedToNow();
		try {
			writeMyselfFile(me);
		} catch (IOException e) {
			LOGGER.error("Could not write resource index file");
		}
		this.me = me;
	}
	
	public float maxPrior() {
		float max = 0.0f;
		for (Resource e: this.engines.values()) {
		    if (e.getPrior() > max) {
		        max = e.getPrior();	
		    }
		}
        return max;
	}
	
	/**
	 *  Dumps the resource index to standard output.
	 */
	public void dump() {
        for (Resource engine: this.engines.values()) {
            System.out.println(engine.toJson());	
        }
	}
	
	private Document luceneDocument(Resource engine) {
        Document doc = new Document();
        String id = engine.getId();
        JSONObject json = new JSONObject();
        JSONObject resourceJson = engine.toJsonEngine();
        resourceJson.put("privateparameters", engine.getJsonPrivateParameters());  // we need to remember those
        JSONObject healthJson = engine.toJsonHealth();
        json.put("resource", resourceJson);
        json.put("health", healthJson);
        json.put("searsia", "v1");
        doc.add(new StringField("id", id, Field.Store.YES)); // unique identifier
        doc.add(new StoredField("json", json.toString()));
        return doc;	    
	}
	
	/**
	 * Flush the index updates to disk
	 */
	public void flush() {
	    try {
	        String lastDate = this.lastFlushed;
	        for (Resource engine: this.engines.values()) {
	            String lastUpdated = engine.getLastUpdatedString();
	            if (this.lastFlushed == null || this.lastFlushed.compareTo(lastUpdated) < 0) {
	                if (lastDate == null || lastDate.compareTo(lastUpdated) < 0) {
	                    lastDate = lastUpdated;
	                }
                    this.writer.updateDocument(new Term("id", engine.getId()), luceneDocument(engine));
	            }
	        }
	        if (this.lastFlushed == null || this.lastFlushed.compareTo(lastDate) < 0) {
        	    this.writer.commit();
        	    this.lastFlushed = lastDate;
        	    LOGGER.info("Flushed resources to disk.");
	        }
	    } catch (Exception e) {
	        LOGGER.warn("Flushing resource index failed: " + e);
	    }
	}
	
	/**
	 * Close the index
	 * @throws IOException
	 */
	public void close() throws IOException {
		this.writer.close();
		this.mother = null;
		this.me = null;
	}
	
	
	public JSONObject toJsonHealth() {
	    String lastMessage = null;
	    int countOk = 0,
	        countError = 0;
	    if (this.mother.isHealthy()) {
	        countOk += 1;
	    } else {
	        countError += 1;
	        lastMessage = this.mother.getId() + " (mother): " + this.mother.getLastError();
	    }
	    for (Resource engine: this.engines.values()) {
	        String error = engine.getLastError();
	        if (engine.isHealthy()) {
	            countOk += 1;
	        } else {
                countError += 1;
	            if (lastMessage == null) {
                   lastMessage = engine.getId() + ": " + error;
	            }
	        }
	        if (countError == 0 && lastMessage == null && error != null) {
                lastMessage = engine.getId() + ": " + error; // last error of any engine
	        }
	    }
	    JSONObject stats = new JSONObject();
	    stats.put("enginesok", countOk);
        stats.put("engineserr", countError);
        if (lastMessage != null) {
            stats.put("lastmessage", lastMessage);
        }
	    return stats;
	}

}
