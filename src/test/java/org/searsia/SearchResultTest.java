package org.searsia;

import org.junit.Assert;
import org.junit.Test;

import org.searsia.Hit;
import org.searsia.SearchResult;

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
		String term = sr.randomTerm();
		String terms = h.toIndexVersion();
		Assert.assertTrue(terms.contains(term));
		Assert.assertTrue(sr.getHits().size() > 0);
		sr.scoreReranking("doesnotmatch", "or");
		Assert.assertTrue(sr.getHits().size() == 0);
	}
	
	
}
