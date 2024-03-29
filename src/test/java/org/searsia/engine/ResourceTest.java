package org.searsia.engine;

import java.util.logging.Logger;
import java.util.logging.Level;

import javax.xml.xpath.XPathExpressionException;

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
    	Logger.getLogger("").setLevel(Level.WARNING); 
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
	public void testSearcJsonPlusHtml() throws XPathExpressionException, SearchException {
		Resource se = new Resource("http://searsia.org/searsia/wiki/cse3.json").updateFromAPI();
		SearchResult result = se.search(se.getTestQuery());
		Assert.assertEquals("application/json+html", se.getMimeType());
		Assert.assertTrue("Result size 6", result.getHits().size() == 6);
		for (Hit hit: result.getHits()) {
		    Assert.assertEquals("Url starts with", "http://searsia.org/", hit.getUrl().substring(0, 19));
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
    public void testSearchJsonUnicode() throws XPathExpressionException, SearchException {
        Resource se = new Resource("http://searsia.org/searsia/wiki/wikifull1{searchTerms}.json");
        SearchResult result = se.search("unicode");
        Assert.assertEquals(3, result.getHits().size());
        Assert.assertEquals("Naïeve ingrediënten überhaupt vóór hè?", result.getHits().get(0).getDescription());
        Assert.assertEquals("عشق و صلح", result.getHits().get(1).getTitle());
    }

    @Test(expected = SearchException.class)
    public void testSearchUrlWrong() throws XPathExpressionException, SearchException {
        Resource se = new Resource("http://searsia.org/searsia/wiki/urltest.json").updateFromAPI();
      	se.randomSearch();
    }

    @Test
    public void testSearchUrlRight() throws XPathExpressionException, SearchException {
        Resource se = new Resource("http://searsia.org/searsia/wiki/urltest.json").updateFromAPI();
        SearchResult result = se.search("url pretty please");
      	Assert.assertEquals(5, result.getHits().size());
      	Assert.assertEquals("http://searsia.org/blog/2017-12-14-searsia-version-1/index.html", result.getHits().get(1).getUrl());
      	Assert.assertEquals("http://searsia.org/blog/2018-01-19-web-search-no-tracking/index.html", result.getHits().get(2).getUrl());
      	Assert.assertEquals("http://searsia.org/searsia/wiki/2017-11-30-ethical-search-advertising/", result.getHits().get(3).getUrl());
      	Assert.assertEquals("http://searsia.org/blog/2017-11-24-searsia-at-dir/", result.getHits().get(4).getUrl());
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
		Assert.assertEquals("directaccess", se1.getDirectAccess(), se2.getDirectAccess());
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
        Assert.assertFalse("ResultType does not match video", se.matchesResultTypes("video"));
	}
	
    @Test
    public void testScoring() throws SearchException {
        Resource se = new Resource("file:src/test/resources/searsia.json").updateFromAPI();
        Assert.assertEquals("Score does not match", 0.0f, se.score("somethingstrange"), 0.001f);
        Assert.assertTrue("Score matches", se.score("searsia") > 0.001f);
    }

    @Test
    public void testDirectAccess() throws SearchException {
        Resource se = new Resource("file:src/test/resources/search.json").updateFromAPI();
        SearchResult result = se.randomSearch();
        Assert.assertTrue("Successful directaccess engine", result != null);
    }
}
