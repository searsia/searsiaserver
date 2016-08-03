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
	
	private Resource htmlSearch() throws XPathExpressionException {
		Resource hiemstra = new Resource("http://wwwhome.cs.utwente.nl/~hiemstra/?s={q}&api={apikey}&p={p?}","hiemstra");
		hiemstra.setUrlUserTemplate(hiemstra.getAPIUserTemplate());
		hiemstra.setItemXpath("//div[./h3/a]");
		hiemstra.addPrivateParameter("apikey", SECRET_API_KEY);
		hiemstra.addHeader("User-Agent", "Test/1.0");
		hiemstra.setPrior(0.3f);
		hiemstra.setRate(133);
        hiemstra.setMimeType("text/html");
        hiemstra.setRerank("lm");
        hiemstra.setFavicon("http://wwwhome.cs.utwente.nl/~hiemstra/images/ut.ico");
		hiemstra.addExtractor(
			new TextExtractor("title", ".//h3"),
			new TextExtractor("description", "."),
			new TextExtractor("url", ".//h3/a/@href")
		);
		return hiemstra;
	}
	
	private Resource searsiaSearch() throws XPathExpressionException {
		return new Resource("http://searsia.org/searsia/wiki-{q?}-{r?}.json");
	}
	
    private Resource xmlSearch() throws XPathExpressionException, SearchException { 	
		Resource wiki = new Resource("http://searsia.org/searsia/wiki-{q?}-{r?}.json");
		Resource wikifull = wiki.searchResource("wikifull");
        return wikifull;
	}

    private Resource jsonSearch() throws XPathExpressionException {
		Resource wiki = new Resource("http://searsia.org/searsia/wiki-{q?}-wikifull.json");
		wiki.setMimeType("application/json");
		wiki.setItemXpath("//hits");
		wiki.addExtractor(
			new TextExtractor("title", "./title"),
			new TextExtractor("description", "./description"),
			new TextExtractor("url", "./url")
		);
		return wiki;
	}
    
    private Resource javascriptSearch() throws XPathExpressionException {
		Resource wikifull = new Resource("http://searsia.org/searsia/wiki-{q}-wikifull.js");
		wikifull.setMimeType("application/x-javascript");
		wikifull.setItemXpath("//hits");
		wikifull.addExtractor(
			new TextExtractor("title", "./title"),
			new TextExtractor("description", "./description"),
			new TextExtractor("url", "./url")
		);
		return wikifull;
	}
    
    @BeforeClass
    public static void setUp() {
    	Logger.getLogger("").setLevel(Level.WARNING); 
    }
	
	@Test
	public void testSearchSearsia() throws XPathExpressionException, SearchException {
		Resource se = searsiaSearch();
		String query = "informat";
		SearchResult result = se.search(query);
		Assert.assertEquals(query, result.getQuery());
		Assert.assertTrue(result.getHits().size() > 0);
	}

	@Test
	public void testSearchHtml() throws XPathExpressionException, SearchException {
		Resource se = htmlSearch();
		SearchResult result = se.search("dolf");
		Assert.assertEquals("text/html", se.getMimeType());
		Assert.assertEquals(7, result.getHits().size());
	}

	@Test
	public void testSearchXml() throws XPathExpressionException, SearchException {
		Resource se = xmlSearch();
		SearchResult result = se.search("informat");
		Assert.assertEquals("application/xml", se.getMimeType());
		Assert.assertEquals(10, result.getHits().size());
	}

	@Test
	public void testSearchJson() throws XPathExpressionException, SearchException {
		Resource se = jsonSearch();
		Boolean debug = true;
		SearchResult result = se.search("informat", debug);
		Assert.assertNotNull(result.getXmlOut());
		Assert.assertEquals("application/json", se.getMimeType());
		Assert.assertEquals(10, result.getHits().size());
	}

	@Test
	public void testSearchJavascript() throws XPathExpressionException, SearchException {
		Resource se = javascriptSearch();
		Boolean debug = true;
		SearchResult result = se.search("informat", debug);
		Assert.assertEquals("application/x-javascript", se.getMimeType());
		Assert.assertEquals(10, result.getHits().size());
	}

	@Test
	public void testSearchSearsiaEmpty() throws XPathExpressionException, SearchException {
		Resource se = searsiaSearch();
		SearchResult result = se.search();
		Assert.assertTrue(result.getHits().size() > 0);
	}

	@Test
	public void testSearchResource() throws XPathExpressionException, SearchException {
		Resource se = searsiaSearch();
		Resource engine = se.searchResource("wikifull");
		Assert.assertTrue(engine != null);
	}

	@Test
	public void testSearchError() throws XPathExpressionException  {
		Resource se = htmlSearch();
		se.setUrlAPITemplate("http://wwwhome.cs.utwente.nl/~hiemstra/WRONG/?s={q}&api={apikey}&p={p?}");
		String message = null;
		try {
			se.search("test");
		} catch (SearchException e) {
			message = e.getMessage();
		}
		Assert.assertNotNull(message);
		Assert.assertFalse("error message reveals secret", message.contains(SECRET_API_KEY));
	}

	@Test
	public void testJsonRoundtrip() throws XPathExpressionException {
		Resource se1 = htmlSearch();
		se1.setPostString("POST");
		se1.setBanner("me.png");
		JSONObject json = se1.toJson();
		Resource se2 = new Resource(json);
		Assert.assertEquals("id", se1.getId(), se2.getId());
		Assert.assertEquals("name", se1.getName(), se2.getName());
		Assert.assertEquals("mimetype", se1.getMimeType(), se2.getMimeType());
		Assert.assertEquals("urltemplate", se1.getUrlUserTemplate(), se2.getUrlUserTemplate());
		Assert.assertEquals("apitemplate", se1.getUrlAPITemplate(), se2.getUrlAPITemplate());
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
		Assert.assertFalse("secret revealed", json.toString().contains(SECRET_API_KEY));
	}

	@Test
	public void equalEngines1() throws XPathExpressionException {
		Resource se1 = htmlSearch();
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
