package org.searsia.web;

import java.io.IOException;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.logging.Handler;

import javax.ws.rs.core.Response;
import javax.xml.xpath.XPathExpressionException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import org.searsia.SearsiaOptions;
import org.searsia.index.SearchResultIndex;
import org.searsia.index.ResourceIndex;
import org.searsia.web.Search;
import org.searsia.engine.Resource;

public class SearchTest {
    
    private static boolean letsLog = false;

    private static final Logger LOGGER = Logger.getLogger("org.searsia");
    private static final String PATH  = "target/index-test";
    private static final String INDEX = "test2";
    private static SearchResultIndex index;
    private static ResourceIndex engines;
	private static SearsiaOptions options;


    private static Resource wiki() throws XPathExpressionException, JSONException {
    	return new Resource(new JSONObject("{\"apitemplate\":\"http://searsia.org/searsia/wiki/wiki{searchTerms}.json\", \"id\":\"wiki\"}"));
    }
 
    private static Resource wrong() throws XPathExpressionException, JSONException {
    	return new Resource(new JSONObject("{\"apitemplate\":\"http://reallyreallydoesnotexist.com/wrong?q={searchTerms}\", \"id\":\"wrong\"}"));
    }
    
    private static Resource ok() throws XPathExpressionException, JSONException {
        return new Resource(new JSONObject("{\"apitemplate\":\"http://searsia.org/searsia/wiki/wikifull1{searchTerms}.json\",\"id\":\"wikifull1\",\"type\":\"blog\"}"));
    }
    
    private static Resource redirect() throws XPathExpressionException, JSONException {
        return new Resource(new JSONObject("{\"apitemplate\":\"http://searsia.org/searsia/search.json?q={searchTerms}\",\"id\":\"search\",\"directaccess\":\"yes\"}"));
    }
    
    private static Resource okDeleted() throws XPathExpressionException, JSONException {
        return new Resource(new JSONObject("{\"deleted\":true, \"id\":\"wikifull1\"}"));
    }
    
    private static Resource me() throws XPathExpressionException, JSONException {
    	return new Resource(new JSONObject("{\"apitemplate\":\"http://me.org?q={searchTerms}\", \"id\":\"wiki\"}"));
    }

    @BeforeClass
    public static void setUp() throws Exception {
    	Handler[] handlers = LOGGER.getHandlers();
    	for(Handler handler : handlers) {
    	  LOGGER.removeHandler(handler);
    	}
    	Handler handler = new ConsoleHandler();
    	handler.setFormatter(new SimpleFormatter());
    	LOGGER.addHandler(handler);
    	if (letsLog) {
        	LOGGER.setLevel(Level.ALL);
    	} else {
        	LOGGER.setLevel(Level.SEVERE);
    	}
    	index = new SearchResultIndex(PATH, INDEX, 10);
    	engines = new ResourceIndex(PATH, INDEX);
    	options = new SearsiaOptions();
    	engines.putMother(wiki());
    	engines.put(wrong());
    	engines.put(ok());
    	engines.put(redirect());
    	engines.putMyself(me());
    }

    @AfterClass
    public static void lastThing() throws IOException, XPathExpressionException, JSONException {
        Search search = new Search(index, engines, options);
        engines.put(okDeleted());
        Response response = search.query("wikifull1.json", "informat", null, null, null);
        int status = response.getStatus();
        String entity = (String) response.getEntity();
        JSONObject json = new JSONObject(entity);
        Assert.assertEquals(410, status);
        LOGGER.finest("No result: " + json);        
        index.close();    	
    }
   
    @Test // returns 'my' resource description
	public void test() throws IOException {
		Search search = new Search(index, engines, options);
		Response response = search.query("wiki.json", "", null, null, null);
		int status = response.getStatus();
		String entity = (String) response.getEntity();
		JSONObject json = new JSONObject(entity);
		JSONObject resource  = (JSONObject) json.get("resource");
        Assert.assertEquals(200, status);
		Assert.assertEquals("wiki", resource.get("id"));
	}
    
    @Test // returns local search results for 'searsia'
	public void testQuery() throws IOException {
		Search search = new Search(index, engines, options);
		Response response = search.query("wiki.json", "searsia search for noobs", null, null, null);
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
		Assert.assertTrue(hits.length() == 4);
		Assert.assertEquals("http://searsia.org", url);
		Assert.assertNotNull(json.get("resource"));		
		LOGGER.finest("Local result: " + json.toString());
		
		response = search.query("wiki.json", "searsia", "blog", null, null);
        status = response.getStatus();
        entity = (String) response.getEntity();
        json = new JSONObject(entity);
        hits  = json.getJSONArray("hits");
        Assert.assertTrue(hits.length() == 2);
        LOGGER.finest("Local blog result: " + json.toString());
	}
    
    @Test // returns local resource 'wrong' 
	public void testResource() throws IOException, XPathExpressionException, JSONException {
		Search search = new Search(index, engines, options);
		Response response = search.query("wrong.json", "", null, null, null);
		int status = response.getStatus();
		String entity = (String) response.getEntity();
		JSONObject json = new JSONObject(entity);
		JSONObject resource  = (JSONObject) json.get("resource");
		Assert.assertEquals(200, status);
		Assert.assertTrue(json.has("health"));
		Assert.assertEquals(wrong().getAPITemplate(), resource.get("apitemplate"));
		LOGGER.finest("Wrong: " + resource.toString());
	}
    
    @Test // returns local resource 'wrong' without apitemplate and health 
	public void testResourceNoSharing() throws IOException, XPathExpressionException, JSONException {
        String[] args = {"-d", "-n", "-m=http://searsia.org/searsia/wiki/wiki{searchTerms}.json"};
    	SearsiaOptions newOptions = new SearsiaOptions(args);
		Search search = new Search(index, engines, newOptions);
		Response response = search.query("wrong.json", "", null, null, null);
		String entity = (String) response.getEntity();
		JSONObject json = new JSONObject(entity);
		JSONObject resource  = (JSONObject) json.get("resource");
		Assert.assertFalse(json.has("health"));
		Assert.assertFalse(resource.has("apitemplate"));
        LOGGER.finest("Wrong limited: " + resource.toString());
	}
    
    @Test // returns resource 'wikididyoumean' (from mother)
	public void testResourceUnknown() throws IOException {
		Search search = new Search(index, engines, options);
		Response response = search.query("wikididyoumean.json", "", null, null, null);
		int status = response.getStatus();
		String entity = (String) response.getEntity();
		JSONObject json = new JSONObject(entity);
		JSONObject resource  = (JSONObject) json.get("resource");
		Assert.assertEquals(200, status);
		Assert.assertEquals("Did you mean:", resource.get("name"));
	}
    
    @Test // returns results for the engine 'wrong' (which does not exist)
	public void testError() throws IOException {
		Search search = new Search(index, engines, options);
		Response response = search.query("wrong.json", "testquery", null, null, null);
		int status = response.getStatus();
		Assert.assertEquals(503, status);
	}

    @Test // returns results for the engine 'wikifull1'
    public void testOk() throws IOException, XPathExpressionException, JSONException {
        Search search = new Search(index, engines, options);
        Response response = search.query("wikifull1.json", "informat", null, null, null);
        int status = response.getStatus();
        String entity = (String) response.getEntity();
        JSONObject json = new JSONObject(entity);
        Assert.assertEquals(200, status);
        Assert.assertNotNull(json.get("hits"));
        Assert.assertNotNull(json.get("resource"));
        LOGGER.finest("Query result: " + json);
        
        response = search.query("wikifull1.json", "informat", null, null, null);
        status = response.getStatus();
        entity = (String) response.getEntity();
        json = new JSONObject(entity);
        Assert.assertEquals(200, status);
        Assert.assertNotNull(json.get("hits"));
        Assert.assertNotNull(json.get("resource"));
        LOGGER.finest("Cache result: " + json);
    }
    
    @Test // returns results for the engine 'wikiredirect'
    public void testRedirect() throws IOException, XPathExpressionException, JSONException {
        Search search = new Search(index, engines, options);
        Response response = search.query("search.json", null, null, null, null);
        int status = response.getStatus();
        String entity = (String) response.getEntity();
        JSONObject json = new JSONObject(entity);
        json = json.getJSONObject("resource");
        Assert.assertEquals(200, status);
        Assert.assertEquals("search", json.get("id"));

        // query redirects:
        response = search.query("search.json", "informat", null, null, null);
        status = response.getStatus();
        String location = response.getHeaderString("Location");
        Assert.assertEquals(302, status);
        Assert.assertEquals("http://searsia.org/searsia/search.json?q=informat", location);
        LOGGER.finest("Redirect: " + location);
    }
    

}
