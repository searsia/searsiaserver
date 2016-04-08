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

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.store.FSDirectory;

import org.searsia.Hit;
import org.searsia.SearchResult;

/**
 *  Searcher for Searsia index for local search results.
 * 
 *  @author Djoerd Hiemstra and Dolf Trieschnigg
 */
public class HitsSearcher  {

	private File hitsDir;
    private IndexReader reader;
    private IndexSearcher searcher;
    private int requests;
    
    /**
     *  Opens the local index "indexName" at "path".
     *  The path directory can be /home/djoerd/.cache/searsia in Ubuntu,
     *  or \Documents and Settings\djoerd\Application Data\Searsia\ in Windows...
     *  There may be multiple indexes.
     *  @param path The Searsia cache directory;
     *  @param indexName  The name of the index.
     */
    public HitsSearcher(String path, String indexName) throws IOException {  	    	
        hitsDir  = new File(path, indexName + "_hits");
        open();
    }
    
    public void open() throws IOException {
        reader   = DirectoryReader.open(FSDirectory.open(hitsDir));
        searcher = new IndexSearcher(reader);
        //searcher.setSimilarity(new BM25Similarity(1.2f, 0.75f)); // k1, b
        //searcher.setSimilarity(new LMDirichletSimilarity(200f)); // mu
        //searcher.setSimilarity(new LMJelinekMercerSimilarity(0.5f)); // lambda
        requests = 0;
    }
    
    public void close() throws IOException {
    	reader.close();
    	searcher = null;
    }
    
    @Override
    protected void finalize() throws Throwable {
        close();
    	super.finalize();
    }
    
    public SearchResult search (String queryString) throws IOException {
        return search(queryString, 40);
    }
    
    public SearchResult search (String queryString, int hitsPerPage) throws IOException  {
    	SearchResult result = new SearchResult();
        TopScoreDocCollector collector;
        ScoreDoc[] docs;
        Query query;
        try { // new Query parser, because it is not thread safe
            query = new QueryParser("terms", new StandardAnalyzer()).parse(QueryParser.escape(queryString));
        } catch (ParseException e) {
            throw new IOException(e);
        }
        collector = TopScoreDocCollector.create(hitsPerPage, true);
    	if (searcher == null) open(); // reopen index to see updates.
        searcher.search(query, collector);
        docs = collector.topDocs().scoreDocs;
        for(ScoreDoc doc: docs) {
    	    int docId = doc.doc;
            Document d = searcher.doc(docId);
            Hit hit = new Hit(d.get("result"));
            hit.put("score", doc.score);
            result.addHit(hit);
        }
        if (requests++ > 10) close(); // close index every 10 searches, to see updates
        return result;
    }
    
    
    public Hit getDocument(String hitId) throws IOException {
    	Term term = new Term("id", hitId);
    	Query query = new TermQuery(term);
    	TopScoreDocCollector collector = TopScoreDocCollector.create(1, true);
    	if (searcher == null) open();
    	searcher.search(query, collector);
    	if (collector.getTotalHits() > 0) {
        	ScoreDoc[] docs = collector.topDocs().scoreDocs;
        	Document doc = searcher.doc(docs[0].doc);
        	Hit hit = new Hit(doc.get("result"));
        	return hit;
    	} else {
    		return null;
    	}
    }
    
    /**
     * Dump the index to standard out
     * @throws IOException
     */
    public void dump() throws IOException {
        TopScoreDocCollector collector;
        ScoreDoc[] docs;
        collector = TopScoreDocCollector.create(999999, true);
    	if (searcher == null) open();
        searcher.search(new MatchAllDocsQuery(), collector);
        docs = collector.topDocs().scoreDocs;
        for(ScoreDoc doc: docs) {
            Document d = searcher.doc(doc.doc);
            System.out.println(d.get("result"));
        }
    	
    }
    
}
