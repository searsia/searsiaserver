package org.searsia.web;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;

import javax.ws.rs.core.Response;

import org.apache.log4j.Logger;
import org.apache.log4j.varia.NullAppender;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import org.searsia.SearchResult;
import org.searsia.index.HitsSearcher;
import org.searsia.index.HitsWriter;
import org.searsia.index.ResourceEngines;
import org.searsia.web.Search;
import org.searsia.xpath.SearchEngine;
import org.searsia.xpath.XpathSearchEngine;


public class SearchTest {

    private static final Logger LOGGER = Logger.getLogger("org.searsia");
    private static final String PATH  = "target/index-test";
    private static final String INDEX = "test2";
    private static HitsSearcher searcher;
    private static ArrayBlockingQueue<SearchResult> queue;
    private static ResourceEngines engines;
    
    @SuppressWarnings("unused")
    private static HitsWriter writer;
    
    private static SearchEngine utwente() {
    	return new XpathSearchEngine("http://search.utwente.nl/searsia/search.php?q={q?}&r={r?}", "utwente");
    }
 
    private static SearchEngine wrong() {
    	return new XpathSearchEngine("http://searsia.com/doesnotexist?q={q}", "wrong");
    }
    
    private static SearchEngine me() {
    	return new XpathSearchEngine("http://me.org?q={q}");
    }
    
    
    @BeforeClass
    public static void setUp() throws Exception {
    	LOGGER.removeAllAppenders();
    	LOGGER.addAppender(new NullAppender()); // thou shall not log
    	queue = new ArrayBlockingQueue<SearchResult>(2);
    	writer = new HitsWriter(PATH, INDEX, queue);
        searcher = new HitsSearcher(PATH, INDEX);
    	engines = new ResourceEngines(PATH, INDEX);
    	engines.putMother(utwente());
    	engines.put(wrong());   	
    	engines.putMyself(me());
    }

    @AfterClass
    public static void lastThing() throws IOException {
    	searcher.close();    	
    }
   
    @Test // returns 'my' resource description
	public void test() throws IOException {
		Search search = new Search(queue, searcher, engines);
		Response response = search.query("", "");
		int status = response.getStatus();
		String entity = (String) response.getEntity();
		JSONObject json = new JSONObject(entity);
		JSONObject resource  = (JSONObject) json.get("resource");
        Assert.assertEquals(200, status);
		Assert.assertEquals("708addc213e3daf4b9742883d18d0c45", resource.get("id"));
	}
    
    @Test // returns local search results for 'searsia'
	public void testQuery() throws IOException {
		Search search = new Search(queue, searcher, engines);
		Response response = search.query("", "searsia search for noobs");
		int status = response.getStatus();
		String entity = (String) response.getEntity();
		JSONObject json = new JSONObject(entity);
		JSONArray hits  = json.getJSONArray("hits");
		String url = "";
		for (int i = 0; i < hits.length(); i += 1) {
			JSONObject hit = (JSONObject) hits.get(i);
			if (hit.has("url")) {
			    url = hit.getString("url");
			    break;
			}
		}
		Assert.assertEquals(200, status);
		Assert.assertTrue(hits.length() > 0);
		Assert.assertEquals("http://searsia.org", url);
	}
    
    @Test // returns local resource 'wrong' 
	public void testResource() throws IOException {
		Search search = new Search(queue, searcher, engines);
		Response response = search.query("wrong", "");
		int status = response.getStatus();
		String entity = (String) response.getEntity();
		JSONObject json = new JSONObject(entity);
		JSONObject resource  = (JSONObject) json.get("resource");
		Assert.assertEquals(200, status);
		Assert.assertEquals(wrong().getAPIUserTemplate(), resource.get("apitemplate"));
	}
    
    @Test // returns resource 'youtube' (from mother)
	public void testResourceUnknown() throws IOException {
		Search search = new Search(queue, searcher, engines);
		Response response = search.query("youtube", "");
		int status = response.getStatus();
		String entity = (String) response.getEntity();
		JSONObject json = new JSONObject(entity);
		JSONObject resource  = (JSONObject) json.get("resource");
		Assert.assertEquals(200, status);
		Assert.assertEquals("Youtube", resource.get("name"));
	}
    
    @Test // returns results for the engine 'wrong' (which does not exist)
	public void testError() throws IOException {
		Search search = new Search(queue, searcher, engines);
		Response response = search.query("wrong", "testquery");
		int status = response.getStatus();
		Assert.assertEquals(503, status);
	}


	
}
