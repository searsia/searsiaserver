package org.searsia;

import java.io.IOException;

import javax.ws.rs.core.Response;

import org.junit.Assert;
import org.junit.Test;
import org.searsia.Hit;
import org.searsia.SearchResult;
import org.searsia.web.Search;

public class SearchResultTest {

	@Test
	public void test1() {
		SearchResult sr = new SearchResult();
		Hit h = new Hit();
		h.put("title", "boo");
		sr.addHit(h);
		Assert.assertEquals("{\"hits\":[{\"title\":\"boo\"}]}", sr.toJson().toString());
	}

	@Test
	public void test2() {
		SearchResult sr = new SearchResult();
		Assert.assertEquals("{\"hits\":[]}", sr.toJson().toString());
	}

	@Test
	public void test3() {
		SearchResult sr = new SearchResult();
		Hit h = new Hit("The ultimate test", "Oh yeah", "http://searsia.org", 
				"http://searsia.org/images/search.png");
		sr.addHit(h);
        h = new Hit("Another test", "yeah", "http://searsia.org/test.html", 
                "http://searsia.org/images/search.png");
        sr.addHit(h);
		String notThis = "test";
    	String term = sr.randomTerm(notThis);
        Assert.assertFalse("Same random term", term.equals(notThis));
		String terms = h.toIndexVersion().toLowerCase();
		Assert.assertTrue("Index contains random term: " + term, terms.contains(term));
        Assert.assertEquals("Total nr of hits", sr.getHits().size(), 2);
        sr.scoreReranking("test", "or");
        Assert.assertEquals("Nr of hits after reranking", sr.getHits().size(), 2);
		sr.scoreReranking("doesnotmatch", "or");
		Assert.assertEquals("Query matches zero results", sr.getHits().size(), 0);
	}
	
}
