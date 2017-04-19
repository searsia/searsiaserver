package org.searsia.index;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

import org.apache.log4j.Logger;
import org.apache.log4j.varia.NullAppender;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import org.searsia.Hit;
import org.searsia.SearchResult;
import org.searsia.index.SearchResultIndex;

/**
 *  Tests Searsia's hits  index
 */
public class TestSearchResultIndex {

    private static final Logger LOGGER = Logger.getLogger("org.searsia");
    private static final String PATH  = "target/index-test";
    private static final String INDEX = "test";
    private static SearchResultIndex index;
      
    @BeforeClass
    public static void setUp() throws Exception {
    	LOGGER.removeAllAppenders();
    	LOGGER.addAppender(new NullAppender());
    	index = new SearchResultIndex(PATH, INDEX, 2);
        SearchResult result = readFile("exampleSearchResult.json");
        index.offer(result);
        index.flush();
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
        JSONObject json = new JSONObject(jsonString);
        JSONArray hits = json.getJSONArray("hits");
        for(int i = 0; i < hits.length(); i++) {
        	result.addHit(new Hit(hits.getJSONObject(i)));
        }
        JSONObject resource = json.getJSONObject("resource");
        result.setResourceId(resource.getString("id"));
        return result;
    }

    
    @Test
    public void testSearch0() throws Exception {
        SearchResult result = index.search("searsia");
		Assert.assertEquals(1, result.getHits().size());
    }

    @Test
    public void testSearch1() throws Exception {
        SearchResult result = index.search("dolf");
		Assert.assertEquals(1, result.getHits().size());
    }

    @Test
    public void testSearch2() throws Exception {
        SearchResult result = readFile("exampleSearchResult.json");
        index.offer(result);
        index.flush();  // add it again
        String query = "dolf";
        result = index.search(query);
        Assert.assertEquals(query, result.getQuery());
		Assert.assertEquals(1, result.getHits().size());
    }

    @Test
    public void testSearch3() throws Exception {
        SearchResult result = index.search("retrieval");
		Assert.assertEquals(6, result.getHits().size());
    }
    
    @Test  // test hit lookup (not used currently)
    public void testSearch4() throws Exception {
        SearchResult result = readFile("exampleSearchResult.json");
        Hit hit1 = result.getHits().get(0);
		Hit hit2 = index.getHit(hit1.getId());
		Assert.assertEquals(hit1.getTitle(), hit2.getTitle());
    }

    @Test  // test the cache
    public void testSearch5() throws Exception {
        SearchResult result = readFile("exampleSearchResult.json");
        String query = "information";
        result.setQuery(query);
        String resourceId = result.getResourceId();
        index.offer(result);
        result = index.cacheSearch(query, resourceId);
        Assert.assertEquals(10, result.getHits().size());
        result = index.cacheSearch(query, "nothing");
        Assert.assertTrue(result == null);
        result = index.cacheSearch("nope", resourceId);
        Assert.assertTrue(result == null);
    }

    /** 
     *  Can also be used from the command line to test an existing index
     *  @param args query
     */
    public static void main(String[] args) throws Exception {  
        SearchResultIndex index = new SearchResultIndex(PATH, INDEX, 500);
        String queryString = "campus";
        if (args.length > 0) {
            queryString = args[0];
        }
        SearchResult result = index.search(queryString);
        System.out.println(result.toJson());
    }

}
