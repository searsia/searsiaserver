package org.searsia.index;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;

import org.apache.log4j.Logger;
import org.apache.log4j.varia.NullAppender;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.searsia.Hit;
import org.searsia.SearchResult;
import org.searsia.index.HitsSearcher;
import org.searsia.index.HitsWriter;

/**
 *  Tests Searsia's hits  index
 */
public class TestHitsSearcher {

    private static final Logger LOGGER = Logger.getLogger("org.searsia");
    private static final String PATH  = "target/index-test";
    private static final String INDEX = "test";
    private static HitsWriter writer;
    private static HitsSearcher searcher;
    private static ArrayBlockingQueue<SearchResult> queue;
      
    @BeforeClass
    public static void setUp() throws Exception {
    	LOGGER.removeAllAppenders();
    	LOGGER.addAppender(new NullAppender());
    	queue = new ArrayBlockingQueue<SearchResult>(2);
    	writer = new HitsWriter(PATH, INDEX, queue);
        SearchResult result = readFile("exampleSearchResult.json");
        queue.offer(result);
        writer.flush();
        searcher = new HitsSearcher(PATH, INDEX);
    }
    
    private static SearchResult readFile(String fileString) throws IOException {
        SearchResult result = new SearchResult();       
        String s, jsonString = "";       // TODO: Does the following file name work in Windows?
        BufferedReader reader = new BufferedReader(new FileReader("src/test/resources/" + fileString)); 
        try {
            while ((s = reader.readLine()) != null) {
            	jsonString += s;
            }
        }
        finally {
            reader.close();
        }
        JSONArray hits = (new JSONObject(jsonString).getJSONArray("hits"));
        for(int i = 0; i < hits.length(); i++) {
        	result.addHit(new Hit(hits.getJSONObject(i)));
        }
        return result;
    }

    
    @Test
    public void testSearch1() throws Exception {
        SearchResult result = searcher.search("dolf");
		Assert.assertEquals(1, result.getHits().size());
    }

    @Test
    public void testSearch2() throws Exception {
        SearchResult result = readFile("exampleSearchResult.json");
        queue.offer(result);
        writer.flush();
        result = searcher.search("dolf");
		Assert.assertEquals(1, result.getHits().size());
    }

    @Test
    public void testSearch3() throws Exception {
        SearchResult result = searcher.search("retrieval");
		Assert.assertEquals(6, result.getHits().size());
    }
    
    @Test
    public void testSearch4() throws Exception {
        SearchResult result = readFile("exampleSearchResult.json");
        Hit hit1 = result.getHits().get(0);
		Hit hit2 = searcher.getDocument(hit1.getId());
		Assert.assertEquals(hit1.getTitle(), hit2.getTitle());
    }

    /** 
     *  Can also be used from the command line to test an existing index
     *  @param args query
     */
    public static void main(String[] args) throws Exception {  
        HitsSearcher searcher = new HitsSearcher(PATH, INDEX);
        String queryString = "campus";
        if (args.length > 0) {
            queryString = args[0];
        }
        SearchResult result = searcher.search(queryString);
        System.out.println(result.toJson());
    }

}
