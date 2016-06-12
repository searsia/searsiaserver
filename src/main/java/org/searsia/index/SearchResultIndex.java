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
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

import org.searsia.Hit;
import org.searsia.SearchResult;

/**
 *  Lucene index for search results. The index operates on results of type SearchResult,
 *  but indexes individual hits (type Hit). Results of type SearchResult can be
 *  searched, offered (queued for updates), flushed (to disk), checked (whether the
 *  queue is full), and dumped (to standard out).
 *  
 *  @author Djoerd Hiemstra and Dolf Trieschnigg
 */
public class SearchResultIndex {

    public static final Hit SEARSIA_HIT = 
  	    new Hit("Searsia", "Search for noobs", "http://searsia.org", "http://searsia.org/images/searsia.png");
    private final static Logger LOGGER = Logger.getLogger(SearchResultIndex.class.getName());
    private final static Version version = Version.LUCENE_4_10_4;

    private ArrayBlockingQueue<SearchResult> queue;
    private int           limit;
    
	private File          hitsDirectory;
    private IndexReader   hitsReader;
    private IndexSearcher hitsSearcher;
    private IndexWriter   hitsWriter;

    public SearchResultIndex(String path, String indexName, int cacheSize) throws IOException {
    	this.queue    = new ArrayBlockingQueue<SearchResult>(cacheSize);
        this.limit    = ((queue.remainingCapacity() + queue.size()) / 2) - 1;  // half of the capacity
        openIndex(path, indexName);
    }
    
    private void openIndex(String path, String indexName) throws IOException {
        File searsiaDir = new File(path);
        if (!searsiaDir.exists()) {  
            searsiaDir.mkdir();
        }
        this.hitsDirectory    = new File(searsiaDir, indexName + "_hits");
        if (!this.hitsDirectory.exists()) {
            this.hitsDirectory.mkdir();
        }    	
        openWriter();
        openReader();
    }
    
    private void openWriter() throws IOException { 
    	StandardAnalyzer indexAnalyzer   = new StandardAnalyzer();
    	IndexWriterConfig indexConfig    = new IndexWriterConfig(version, indexAnalyzer);
        indexConfig.setOpenMode(OpenMode.CREATE_OR_APPEND);
        this.hitsWriter = new IndexWriter(FSDirectory.open(this.hitsDirectory), indexConfig);
        storeSearchResult(new SearchResult(SEARSIA_HIT));
        this.hitsWriter.commit();
    }

    private void openReader() throws IOException {
        this.hitsReader   = DirectoryReader.open(FSDirectory.open(this.hitsDirectory));
        this.hitsSearcher = new IndexSearcher(this.hitsReader);
        //searcher.setSimilarity(new BM25Similarity(1.2f, 0.75f)); // k1, b
        //searcher.setSimilarity(new LMDirichletSimilarity(200f)); // mu
        //searcher.setSimilarity(new LMJelinekMercerSimilarity(0.5f)); // lambda
    }
    
    private void closeWriter() throws IOException {
        hitsWriter.close();
        hitsWriter = null;
    }
    
    private void closeReader() throws IOException {
    	hitsReader.close();
    	hitsSearcher = null;
    }
    
    public void close() throws IOException {
    	closeWriter();
    	closeReader();
    }
    
    @Override
    protected void finalize() throws Throwable {
        close();
    	super.finalize();
    }
    
    private void storeSearchResult(SearchResult result)  throws IOException { 
        for (Hit hit: result.getHits()) {
            String id = hit.getId();
            String terms = hit.toIndexVersion();
            Document doc = new Document();
            if (id != null && hit.getTitle() != null) { // must have a title
                doc.add(new StringField("id", id, Field.Store.YES)); // unique identifier
                doc.add(new TextField("terms", terms, Field.Store.NO));
                doc.add(new StoredField("result", hit.toJson().toString()));
                this.hitsWriter.updateDocument(new Term("id", id), doc);
            }
        }
    }
    
    public void offer(SearchResult result) {
    	this.queue.offer(result);
    }
    
    public SearchResult search (String queryString) throws IOException {
        return search(queryString, 40);
    }
    
    public SearchResult search (String queryString, int hitsPerPage) throws IOException  {
    	SearchResult result = new SearchResult();
    	result.setQuery(queryString);
        TopScoreDocCollector collector;
        ScoreDoc[] docs;
        Query query;
        try { // new Query parser, because it is not thread safe
            query = new QueryParser("terms", new StandardAnalyzer()).parse(QueryParser.escape(queryString));
        } catch (ParseException e) {
            throw new IOException(e);
        }
        collector = TopScoreDocCollector.create(hitsPerPage, true);
    	if (hitsSearcher == null) openReader(); // reopen index to see updates.
        hitsSearcher.search(query, collector);
        docs = collector.topDocs().scoreDocs;
        for(ScoreDoc doc: docs) {
    	    int docId = doc.doc;
            Document d = hitsSearcher.doc(docId);
            Hit hit = new Hit(d.get("result"));
            hit.put("score", doc.score);
            result.addHit(hit);
        }
        return result;
    }
    
    
    protected Hit getHit(String hitId) throws IOException {
    	Term term = new Term("id", hitId);
    	Query query = new TermQuery(term);
    	TopScoreDocCollector collector = TopScoreDocCollector.create(1, true);
    	if (hitsSearcher == null) openReader();
    	hitsSearcher.search(query, collector);
    	if (collector.getTotalHits() > 0) {
        	ScoreDoc[] docs = collector.topDocs().scoreDocs;
        	Document doc = hitsSearcher.doc(docs[0].doc);
        	Hit hit = new Hit(doc.get("result"));
        	return hit;
    	} else {
    		return null;
    	}
    }


    /**
     * Flushes the queue with updates to disk
     * @throws IOException
     */
    public void flush() throws IOException {
        while (queue.size() > 0) {
            SearchResult result = queue.poll();
            storeSearchResult(result);
        } 
        this.hitsWriter.commit();
        closeReader();
        LOGGER.info("{\"message\":\"Flushed cache to index.\"}");
    }
    
    /**
     * Checks if the queue is full (its size is larger than 'limit')
     * If so, it flushes the updates to disk.
     * @return true is queue was flushed.
     * @throws IOException
     */
    public boolean check() throws IOException {
    	boolean full = queue.size() > limit;
        if (full) {
            flush();
        } 
        return full;
    }

    /**
     * Dump the index to standard out
     * @throws IOException
     */
    public void dump() throws IOException {
        TopScoreDocCollector collector;
        ScoreDoc[] docs;
        collector = TopScoreDocCollector.create(999999, true);
    	if (hitsSearcher == null) openReader();
        hitsSearcher.search(new MatchAllDocsQuery(), collector);
        docs = collector.topDocs().scoreDocs;
        for(ScoreDoc doc: docs) {
            Document d = hitsSearcher.doc(doc.doc);
            System.out.println(d.get("result"));
        }
    	
    }
	
}
