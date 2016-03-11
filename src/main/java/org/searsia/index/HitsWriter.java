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

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;

import org.apache.log4j.Logger;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

import org.searsia.Hit;
import org.searsia.SearchResult;

/**
 *  Writer for Searsia index for local search results.
 * 
 *  @author Djoerd Hiemstra and Dolf Trieschnigg
 */
public class HitsWriter implements Runnable {

    public static final Hit SEARSIA_HIT = 
      new Hit("Searsia", "Search for noobs", "http://searsia.org", "http://searsia.org/images/searsia.png");
    private final static Logger LOGGER = Logger.getLogger(HitsWriter.class.getName());

    private ArrayBlockingQueue<SearchResult> queue;
    private int limit;
    private int interval;

    private final Version     version = Version.LUCENE_4_10_4;
    private Directory         indexDir;
    private StandardAnalyzer  indexAnalyzer;
    private IndexWriterConfig indexConfig;
    private IndexWriter       indexWriter;

    public HitsWriter(String path, String indexName, ArrayBlockingQueue<SearchResult> queue) throws IOException {
    	this(path, indexName, queue, 6);
    }
    
    public HitsWriter(String path, String indexName, ArrayBlockingQueue<SearchResult> queue, int interval) throws IOException {
    	this.initIndex(path, indexName);
        this.queue    = queue;
        this.limit    = ((queue.remainingCapacity() + queue.size()) / 2) - 1;  // half of the capacity
        this.interval = interval;
    }

    private void initIndex(String path, String indexName) throws IOException { 
        File cacheDir = new File(path);
        if (!cacheDir.exists()) {  
            cacheDir.mkdir();
        }
        File hitsDir    = new File(cacheDir, indexName + "_hits");
        if (!hitsDir.exists()) {
            hitsDir.mkdir();
        }
        this.indexDir = FSDirectory.open(hitsDir);
        this.indexAnalyzer   = new StandardAnalyzer();
        this.indexConfig     = new IndexWriterConfig(version, indexAnalyzer);
        this.indexConfig.setOpenMode(OpenMode.CREATE_OR_APPEND);
        this.indexWriter     = new IndexWriter(this.indexDir, this.indexConfig);
        addSearchResult(new SearchResult(SEARSIA_HIT));
        this.indexWriter.commit();
    }

    private void addSearchResult(SearchResult result)  throws IOException { 
        for (Hit hit: result.getHits()) {
            String id = hit.getId();
            String terms = hit.toIndexVersion();
            Document doc = new Document();
            if (id != null) {
                doc.add(new StringField("id", id, Field.Store.YES)); // unique identifier
                doc.add(new TextField("terms", terms, Field.Store.NO));
                doc.add(new StoredField("result", hit.toJson().toString()));
                this.indexWriter.updateDocument(new Term("id", id), doc);
            }
        }
    }

    public void flush() throws IOException {
        while (queue.size() > 0) {
            SearchResult result = queue.poll();
            addSearchResult(result);
        } 
        this.indexWriter.commit();
        LOGGER.info("{\"message\":\"Flushed cache to index.\"}");
    }
    
    public boolean check() throws IOException {
    	boolean full = queue.size() > limit;
        if (full) {
            flush();
        } 
        return full;
    }

    @Override  // so, it can be spawned as a thread, not used currently...
    public void run() {
        try {
            while(true) {
                check();
                Thread.sleep(interval * 1000);
            }
        } catch (InterruptedException e) {
        } catch (IOException e) {
        }
    }
		
}
