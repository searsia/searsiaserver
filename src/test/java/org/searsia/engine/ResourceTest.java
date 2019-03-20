package org.searsia.engine;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.xml.xpath.XPathExpressionException;

import org.searsia.Hit;
import org.searsia.SearchResult;
import org.searsia.engine.Resource;

public class ResourceTest {

	private static final String SECRET_API_KEY = "a7235cdsf43d3a2dfgeda";

    @BeforeClass
    public static void setUp() {
    	Logger.getLogger("").setLevel(Level.WARN); 
    }
	
	@Test
	public void testSearchSearsia() throws XPathExpressionException, SearchException {
        Resource se = new Resource("file:src/test/resources/index.json").updateFromAPI();
		String query = "informat";
		SearchResult result = se.search(query);
		Assert.assertEquals(query, result.getQuery());
		Assert.assertTrue(result.getHits().size() > 0);
	}

	@Test
	public void testSearchHtml() throws XPathExpressionException, SearchException {
		Resource se = new Resource("file:src/test/resources/hiemstra.json").updateFromAPI();
		SearchResult result = se.search("dolf trieschnigg", "xml");
		Assert.assertEquals("text/html", se.getMimeType());
		Assert.assertEquals(10, result.getHits().size());
	}

	@Test
	public void testSearchPost() throws XPathExpressionException, SearchException {
        Resource se = new Resource("file:src/test/resources/hiemstrapost.json").updateFromAPI();
		SearchResult result = se.search("dolf trieschnigg");
		Assert.assertEquals("text/html", se.getMimeType());
		Assert.assertEquals(10, result.getHits().size());
	}

	@Test
	public void testSearchXml() throws XPathExpressionException, SearchException {
		Resource se1 = new Resource("http://searsia.org/searsia/wiki/index{searchTerms}.json").updateFromAPI();
        Resource se2 = se1.searchResource("wikifull1");
		SearchResult result = se2.search("informat");
		Assert.assertEquals("application/xml", se2.getMimeType());
		Assert.assertEquals(10, result.getHits().size());
	}

	@Test
	public void testSearchXml2() throws XPathExpressionException, SearchException {
        Resource se = new Resource("file:src/test/resources/hiemstraxml.json").updateFromAPI();
		long startTime = System.currentTimeMillis();
		SearchResult result = se.search("test");
        Assert.assertEquals("application/xml", se.getMimeType());
		Assert.assertEquals(10, result.getHits().size());
		Assert.assertFalse("Parser timed out", System.currentTimeMillis() - startTime > 10000);
	}

	@Test
	public void testSearchXml3() throws XPathExpressionException, SearchException {
		Resource se1 = new Resource("http://searsia.org/searsia/wiki/cse1{searchTerms}.json").updateFromAPI();
		SearchResult result = se1.search("life");
		Assert.assertEquals("application/xml", se1.getMimeType());
		Assert.assertEquals(10, result.getHits().size());
	}

	@Test
	public void testSearchJson() throws XPathExpressionException, SearchException {
		Resource se = new Resource("file:src/test/resources/searsia.json").updateFromAPI();
		String debug = "xml";
		SearchResult result = se.search("informat", debug);
		Assert.assertNotNull(result.getDebugOut());
		Assert.assertEquals("application/json", se.getMimeType());
		Assert.assertTrue("Result size 10 or more", result.getHits().size() >= 10);
	}

	@Test
	public void testSearchHtmlPlusJson() throws XPathExpressionException, SearchException {
		Resource se = new Resource("http://searsia.org/searsia/wiki/cse2.json").updateFromAPI();
		SearchResult result = se.search(se.getTestQuery());
		Assert.assertEquals("application/html+json", se.getMimeType());
		Assert.assertTrue("Result size 3", result.getHits().size() == 3);
		for (Hit hit: result.getHits()) {
		    Assert.assertEquals("Url starts with", "http://searsia.org/blog/", hit.getUrl().substring(0, 24));
		}
	}

	@Test
	public void testSearchJson2() throws XPathExpressionException, SearchException {
        Resource se = new Resource("http://searsia.org/searsia/wiki/wikifull1{searchTerms}.json");
		SearchResult result = se.search("json");
		Assert.assertEquals(1, result.getHits().size());
		Assert.assertEquals("extra content", result.getHits().get(0).getString("content"));
	}

    @Test
    public void testSearchJsonStrangeKeys() throws XPathExpressionException, SearchException {
        Resource se = new Resource("http://searsia.org/searsia/wiki/wikifull1{searchTerms}.json");
        SearchResult result = se.search("strange keys");
        Assert.assertEquals(1, result.getHits().size());
    }

    @Test
    public void testSearchJsonHtmlAndlinks() throws XPathExpressionException, SearchException {
        Resource se = new Resource("http://searsia.org/searsia/wiki/wikifull1{searchTerms}.json");
        SearchResult result = se.search("html and links");
        Assert.assertEquals(2, result.getHits().size());
        Assert.assertEquals("Another test for Searsia", result.getHits().get(0).getTitle());
        Assert.assertEquals("mailto:info@searsia.org", result.getHits().get(1).getString("url")); // TODO getUrl instead of getString
    }

    @Test
	public void testSearchJavascript() throws XPathExpressionException, SearchException {
		Resource se = new Resource("file:src/test/resources/javascript.json").updateFromAPI();
		String debug = "xml";
		SearchResult result = se.search("informat", debug);
		Assert.assertEquals("application/x-javascript", se.getMimeType());
		Assert.assertEquals(10, result.getHits().size());
	}

	@Test
	public void testSearchSearsiaEmpty() throws XPathExpressionException, SearchException {
		Resource se = new Resource("http://searsia.org/searsia/wiki/index{searchTerms}.json").updateFromAPI();
		SearchResult result = se.searchWithoutQuery();
		Assert.assertTrue(result.getHits().size() > 0);
	}

	@Test
	public void testSearchResource() throws XPathExpressionException, SearchException {
		Resource se = new Resource("file:src/test/resources/index.json").updateFromAPI();
		Resource engine = se.searchResource("wikifull1");
		Assert.assertTrue(engine != null);
	}

    @Test
    public void testSearchNoResource1() throws XPathExpressionException, SearchException {
        Resource se = new Resource("file:src/test/resources/hiemstra.json").updateFromAPI();
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
        Resource se = new Resource("file:src/test/resources/randomid.json").updateFromAPI();
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
        Resource se = new Resource("file:src/test/resources/wrong.json").updateFromAPI();
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
        Resource se1 = new Resource("file:src/test/resources/hiemstracrazy.json").updateFromAPI();
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
        Assert.assertEquals("type", se1.getResultTypes(), se2.getResultTypes());
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
        Resource se1 = new Resource("file:src/test/resources/hiemstra.json").updateFromAPI();
		JSONObject json = se1.toJson();
		Resource se2 = new Resource(json);
        Assert.assertTrue("Equals big engine", se1.equals(se2));
	}
	
	@Test
	public void equalEngines2() throws XPathExpressionException, SearchException {
		Resource se1 = new Resource("file:src/test/resources/index.json").updateFromAPI();
		JSONObject json = se1.toJson();
		Resource se2 = new Resource(json);
        Assert.assertTrue("Truely Equals small engine", se1.equals(se2));
        Assert.assertEquals("Equals small equals", se1, se2);
	}
	
	@Test
	public void testResultTypes() throws SearchException {
        Resource se = new Resource("file:src/test/resources/searsia.json").updateFromAPI();
        Assert.assertEquals("ResultType 1", "web", se.getResultTypes());
        se.addResultType("Blog");
        Assert.assertEquals("ResultType 2", "web blog", se.getResultTypes());
        se.addResultType("blog");
        se.addResultType("Web");
        Assert.assertEquals("ResultType 3", "web blog", se.getResultTypes());
        Assert.assertTrue("ResultType matches blog", se.matchesResultTypes("blog"));
        Assert.assertFalse("ResultType does not match video", se.matchesResultTypes("video"));
        for (int i = 0; i < 50; i++) { // maximum of 250 characters
            se.addResultType("type" + i);
        }
        Assert.assertTrue("ResultType starts with type", se.getResultTypes().startsWith("type"));
        Resource se2 = new Resource("file:src/test/resources/searsia.json").updateFromAPI();
        Assert.assertTrue("ResultType comparison", se.compareTo(se2) == 0);
	}
	
    @Test
    public void testScoring() throws SearchException {
        Resource se = new Resource("file:src/test/resources/searsia.json").updateFromAPI();
        Assert.assertEquals("Score does not match", 0.0f, se.score("somethingstrange"), 0.001f);
        Assert.assertTrue("Score matches", se.score("searsia") > 0.001f);
    }

}
