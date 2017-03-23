package org.searsia.web;

import java.io.IOException;

import javax.ws.rs.core.Response;

import org.apache.log4j.Logger;
import org.apache.log4j.varia.NullAppender;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import org.searsia.index.SearchResultIndex;
import org.searsia.index.ResourceIndex;
import org.searsia.web.Search;
import org.searsia.engine.Resource;

public class SearchTest {

    private static final Logger LOGGER = Logger.getLogger("org.searsia");
    private static final String PATH  = "target/index-test";
    private static final String INDEX = "test2";
    private static SearchResultIndex index;
    private static ResourceIndex engines;
    
    
    private static Resource utwente() {
    	return new Resource("https://search.utwente.nl/searsia/search.php?q={q?}&r={r?}", "utwente");
    }
 
    private static Resource wrong() {
    	return new Resource("http://searsia.com/doesnotexist?q={q}", "wrong");
    }
    
    private static Resource me() {
    	return new Resource("http://me.org?q={q}", "utwente");
    }
    
    
    @BeforeClass
    public static void setUp() throws Exception {
    	LOGGER.removeAllAppenders();
    	LOGGER.addAppender(new NullAppender()); // thou shall not log
    	index = new SearchResultIndex(PATH, INDEX, 2);
    	engines = new ResourceIndex(PATH, INDEX);
    	engines.putMother(utwente());
    	engines.put(wrong());   	
    	engines.putMyself(me());
    }

    @AfterClass
    public static void lastThing() throws IOException {
    	index.close();    	
    }
   
    @Test // returns 'my' resource description
	public void test() throws IOException {
		Search search = new Search(index, engines);
		Response response = search.query("utwente", "");
		int status = response.getStatus();
		String entity = (String) response.getEntity();
		JSONObject json = new JSONObject(entity);
		JSONObject resource  = (JSONObject) json.get("resource");
        Assert.assertEquals(200, status);
		Assert.assertEquals("utwente", resource.get("id"));
	}
    
    @Test // returns local search results for 'searsia'
	public void testQuery() throws IOException {
		Search search = new Search(index, engines);
		Response response = search.query("utwente", "searsia search for noobs");
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
		Search search = new Search(index, engines);
		Response response = search.query("wrong", "");
		int status = response.getStatus();
		String entity = (String) response.getEntity();
		JSONObject json = new JSONObject(entity);
		JSONObject resource  = (JSONObject) json.get("resource");
		Assert.assertEquals(200, status);
		Assert.assertEquals(wrong().getAPITemplate(), resource.get("apitemplate"));
	}
    
    @Test // returns resource 'youtube' (from mother)
	public void testResourceUnknown() throws IOException {
		Search search = new Search(index, engines);
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
		Search search = new Search(index, engines);
		Response response = search.query("wrong", "testquery");
		int status = response.getStatus();
		Assert.assertEquals(503, status);
	}


	
}
