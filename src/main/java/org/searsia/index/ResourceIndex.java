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
import java.util.logging.Logger;

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
	private Resource me = null;
	private Path meFile     = null;
	private Path indexDir = null;
	private IndexWriter writer = null;

	/**
	 * Reads resources from index (if they exist)
	 * @param path path where the Searsia index resides
	 * @param filename index file name
	 * @throws IOException
	 */
	public ResourceIndex(String path, String filename) throws IOException {
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


	private Resource readMyselfFile(Path meFile) throws IOException {
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
            LOGGER.warning("No resources in index.");
            return;
        }
        try {
            IndexSearcher searcher = new IndexSearcher(reader);
            ScoreDoc[] hits = searcher.search(new MatchAllDocsQuery(), MAX_SOURCE_CACHE).scoreDocs;
            for (ScoreDoc hit: hits) {
                Document doc = searcher.doc(hit.doc);
                JSONObject json = new JSONObject(doc.get("json"));
                Resource engine = new Resource(json);
                this.engines.put(engine.getId(), engine);
            }
        } catch (javax.xml.xpath.XPathExpressionException e) {
        	throw new IOException(e);
        } catch (JSONException e) {
        	throw new IOException(e);
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
	

	private boolean exists(Resource engine) {
		for (Resource e: this.engines.values())
		    if (e.equals(engine))
		    	return true;
		return false;
	}


	private void updateResourceIndex(String id, Resource engine) throws IOException {
        Document doc = new Document();
        if (id != null) {
        	JSONObject json = engine.toJson();
        	json.put("parameters", engine.getJsonPrivateParameters());  // we need to remember those
            doc.add(new StringField("id", id, Field.Store.YES)); // unique identifier
            doc.add(new StoredField("json", json.toString()));
            this.writer.updateDocument(new Term("id", id), doc);
        }
        this.writer.commit();
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
		if (!exists(engine)) {
			try {
				// TODO: keepPrivateParameters(engine); do not overwrite own parameters, ugh
	            updateResourceIndex(engine.getId(), engine);
			} catch (IOException e) {
				LOGGER.warning("Update of resource " + engine.getId() + " failed");
				// TODO Oh crap, what to do?
			}
		}
   		engines.put(engine.getId(), engine);
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
	    	return null;
	    }
	}
	
	// Efficiency can be gained here?
	public Map<String, Float> topValues(String queryString, int max) {
        Float[] topScores = new Float[max];
		Resource[] topEngines = new Resource[max];
		int size = 0;
		float lastScore = -99.0f;
		for (Resource engine: this.engines.values()) {
		    float score = engine.score(queryString) + engine.getPrior(); // TODO: add bias ?
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
		this.mother = mother;
	}
	
	public void putMyself(Resource engine) {
		if (get(engine.getId()) != null) {
			throw new RuntimeException("The server id '" + engine.getId() + "' already exists.");
		}
		try {
			writeMyselfFile(engine);
		} catch (IOException e) {
			LOGGER.warning("Could not write index file");
		}
		this.me = engine;
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
	
	public void close() throws IOException {
		this.writer.close();
		this.mother = null;
		this.me = null;
	}
	
}
