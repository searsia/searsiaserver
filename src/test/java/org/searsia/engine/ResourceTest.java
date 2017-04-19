package org.searsia.engine;

import org.json.JSONObject;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.xpath.XPathExpressionException;

import org.searsia.SearchResult;
import org.searsia.engine.Resource;

public class ResourceTest {

	private static final String SECRET_API_KEY = "a7235cdsf43d3a2dfgeda";
	
    private Resource searsiaMimeOnlySearch() throws XPathExpressionException {
        return new Resource("http://searsia.org/searsia/wiki/wikididyoumean{q?}.json", "randomid");
    }	

	private Resource searsiaSearch() throws XPathExpressionException {
		return new Resource("http://searsia.org/searsia/wiki/index{q}.json", "index");
	}
	
    private Resource xmlSearch() throws XPathExpressionException, SearchException { 	
		Resource wiki = new Resource("http://searsia.org/searsia/wiki/index{q?}.json", "index");
		Resource wikifull = wiki.searchResource("wikifull1");
        return wikifull;
	}

    private Resource jsonSearch() throws XPathExpressionException {
		Resource wiki = new Resource("http://searsia.org/searsia/wiki/wikifull1{q?}.json", "wikifull1");
		wiki.setMimeType("application/json");
		wiki.setItemXpath("//hits");
		wiki.addExtractor(
			new TextExtractor("title", "./title"),
			new TextExtractor("description", "./description"),
			new TextExtractor("url", "./url"),
			new TextExtractor("content", "./content")
		);
		return wiki;
	}
    
    private Resource javascriptSearch() throws XPathExpressionException {
		Resource wikifull = new Resource("http://searsia.org/searsia/wiki/wikifull1{q}.js", "wikifull1");
		wikifull.setMimeType("application/x-javascript");
		wikifull.setItemXpath("//hits");
		wikifull.addExtractor(
			new TextExtractor("title", "./title"),
			new TextExtractor("description", "./description"),
			new TextExtractor("url", "./url")
		);
		return wikifull;
	}
    
    @Test
    public void testje()  throws XPathExpressionException, SearchException {
        System.out.println(searsiaSearch().toJson());
        System.out.println(searsiaMimeOnlySearch().toJson());
        System.out.println(xmlSearch().toJson());
        System.out.println(jsonSearch().toJson());
        System.out.println(javascriptSearch().toJson());
    }
    
    @BeforeClass
    public static void setUp() {
    	Logger.getLogger("").setLevel(Level.WARNING); 
    }
	
	@Test
	public void testSearchSearsia() throws XPathExpressionException, SearchException {
        Resource se = new Resource("file:src/test/resources/index.json", null).updateFromAPI();
		String query = "informat";
		SearchResult result = se.search(query);
		Assert.assertEquals(query, result.getQuery());
		Assert.assertTrue(result.getHits().size() > 0);
	}

	@Test
	public void testSearchHtml() throws XPathExpressionException, SearchException {
		Resource se = new Resource("file:src/test/resources/hiemstra.json", null).updateFromAPI();
		SearchResult result = se.search("dolf trieschnigg", "xml");
		Assert.assertEquals("text/html", se.getMimeType());
		Assert.assertEquals(10, result.getHits().size());
	}

	@Test
	public void testSearchPost() throws XPathExpressionException, SearchException {
        Resource se = new Resource("file:src/test/resources/hiemstrapost.json", null).updateFromAPI();
		SearchResult result = se.search("dolf trieschnigg");
		Assert.assertEquals("application/xml", se.getMimeType());
		Assert.assertEquals(10, result.getHits().size());
	}

	@Test
	public void testSearchXml() throws XPathExpressionException, SearchException {
		Resource se = xmlSearch();
		SearchResult result = se.search("informat");
		Assert.assertEquals("application/xml", se.getMimeType());
		Assert.assertEquals(10, result.getHits().size());
	}

	@Test
	public void testSearchXml2() throws XPathExpressionException, SearchException {
        Resource se = new Resource("file:src/test/resources/hiemstra.json", null).updateFromAPI();
		se.setMimeType("application/xml");
		se.setRerank(null);
		long startTime = System.currentTimeMillis();
		SearchResult result = se.search("test");
		Assert.assertEquals(10, result.getHits().size());
		Assert.assertFalse("Parser timed out", System.currentTimeMillis() - startTime > 10000);
	}

	@Test
	public void testSearchJson() throws XPathExpressionException, SearchException {
		Resource se = jsonSearch();
		String debug = "xml";
		SearchResult result = se.search("informat", debug);
		Assert.assertNotNull(result.getDebugOut());
		Assert.assertEquals("application/json", se.getMimeType());
		Assert.assertEquals(10, result.getHits().size());
	}

	@Test
	public void testSearchJson2() throws XPathExpressionException, SearchException {
		Resource se = jsonSearch();
		SearchResult result = se.search("json");
		Assert.assertEquals(1, result.getHits().size());
		Assert.assertEquals("extra content", result.getHits().get(0).getString("content"));
	}

    @Test
    public void testSearchJson3() throws XPathExpressionException, SearchException {
        Resource se = jsonSearch();
        SearchResult result = se.search("strange keys");
        Assert.assertEquals(1, result.getHits().size());
    }

	@Test
	public void testSearchJavascript() throws XPathExpressionException, SearchException {
		Resource se = javascriptSearch();
		String debug = "xml";
		SearchResult result = se.search("informat", debug);
		Assert.assertEquals("application/x-javascript", se.getMimeType());
		Assert.assertEquals(10, result.getHits().size());
	}

	@Test
	public void testSearchSearsiaEmpty() throws XPathExpressionException, SearchException {
		Resource se = searsiaSearch();
		SearchResult result = se.searchWithoutQuery();
		Assert.assertTrue(result.getHits().size() > 0);
	}

	@Test
	public void testSearchResource() throws XPathExpressionException, SearchException {
		Resource se = searsiaSearch();
		Resource engine = se.searchResource("wikifull1");
		Assert.assertTrue(engine != null);
	}

    @Test
    public void testSearchNoResource1() throws XPathExpressionException, SearchException {
        Resource se = new Resource("file:src/test/resources/hiemstra.json", null).updateFromAPI();
        Boolean exception = false;
        try {
            se.searchResource("wikifull1");
        } catch (SearchException e) {
            exception = true;
        }
        Assert.assertTrue("Non-Searsia engine throws exception", exception);
    }

    @Test
    public void testSearchNoResource2() throws XPathExpressionException, SearchException {
        Resource se = searsiaMimeOnlySearch();
        Boolean exception = false;
        try {
            se.searchResource("wikifull1");
        } catch (SearchException e) {
            exception = true;
        }
        Assert.assertTrue("No resources exception", exception);
    }

	@Test
	public void testSearchError() throws XPathExpressionException, SearchException  {
        Resource se = new Resource("file:src/test/resources/wrong.json", null).updateFromAPI();
		String message = null;
		String apiKey = se.getPrivateParameter("apikey");
		try {
			se.search("test");
		} catch (SearchException e) {
			message = e.getMessage();
		}
		Assert.assertNotNull("Error message", message);
        Assert.assertNotNull("API key", apiKey);
		Assert.assertFalse("Error message reveals secret", message.contains(apiKey));
	}

	@Test
	public void testJsonRoundtrip() throws XPathExpressionException, SearchException {
        Resource se1 = new Resource("file:src/test/resources/hiemstra.json", null).updateFromAPI();
		se1.setPostString("POST");
		se1.setPostQueryEncode("application/x-www-form-urlencoded");
        se1.setRerank("lm");
		se1.setBanner("me.png");
		se1.setUrlSuggestTemplate("http://whatever");
		JSONObject json = se1.toJson();
		Resource se2 = new Resource(json);
		Assert.assertEquals("id", se1.getId(), se2.getId());
		Assert.assertEquals("name", se1.getName(), se2.getName());
		Assert.assertEquals("mimetype", se1.getMimeType(), se2.getMimeType());
		Assert.assertEquals("urltemplate", se1.getUserTemplate(), se2.getUserTemplate());
		Assert.assertEquals("apitemplate", se1.getAPITemplate(), se2.getAPITemplate());
		Assert.assertEquals("suggesttemplate", se1.getSuggestTemplate(), se2.getSuggestTemplate());
		Assert.assertEquals("favicon", se1.getFavicon(), se2.getFavicon());
		Assert.assertEquals("rerank", se1.getRerank(), se2.getRerank());
		Assert.assertEquals("banner", se1.getBanner(), se2.getBanner());
		Assert.assertEquals("itempath", se1.getItemXpath(), se2.getItemXpath());
		Assert.assertEquals("testquery", se1.getTestQuery(), se2.getTestQuery());
		Assert.assertEquals("prior", se1.getPrior(), se2.getPrior(), 0.0001d);
		Assert.assertEquals("maxqueriesperday", se1.getRate(), se2.getRate());
		Assert.assertEquals("extractors", se1.getExtractors().size(), se2.getExtractors().size());
		Assert.assertEquals("headers", se1.getHeaders().size(), se2.getHeaders().size());
		Assert.assertEquals("post", se1.getPostString(), se2.getPostString());
		Assert.assertEquals("postencode", se1.getPostQueryEncode(), se2.getPostQueryEncode());
		Assert.assertFalse("secret revealed", json.toString().contains(SECRET_API_KEY));
	}
	
	@Test
	public void testJsonPrivateParameter() throws XPathExpressionException {
	    JSONObject json = new JSONObject("{\"id\":\"test\", \"privateparameters\":{\"apikey\":\"secret\"}}");
	    Resource se = new Resource(json);
	    Assert.assertEquals("private parameters", se.getPrivateParameter("apikey"), "secret");
	}

	@Test
	public void equalEngines1() throws XPathExpressionException, SearchException {
        Resource se1 = new Resource("file:src/test/resources/hiemstra.json", null).updateFromAPI();
		JSONObject json = se1.toJson();
		Resource se2 = new Resource(json);
        Assert.assertTrue("Equals big engine", se1.equals(se2));
	}
	
	@Test
	public void equalEngines2() throws XPathExpressionException {
		Resource se1 = searsiaSearch();
		JSONObject json = se1.toJson();
		Resource se2 = new Resource(json);
        Assert.assertTrue("Truely Equals small engine", se1.equals(se2));
        Assert.assertEquals("Equals small equals", se1, se2);
	}
	
}
