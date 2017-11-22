package org.searsia;

import org.junit.Assert;
import org.junit.Test;
import org.searsia.Hit;
import org.searsia.SearchResult;

public class SearchResultTest {

	@Test
	public void testSimple() {
		SearchResult sr = new SearchResult();
		Hit h = new Hit();
		h.put("title", "boo");
		sr.addHit(h);
		Assert.assertEquals("{\"hits\":[{\"title\":\"boo\"}]}", sr.toJson().toString());
	}

	@Test
	public void testEmpty() {
		SearchResult sr = new SearchResult();
		Assert.assertEquals("{\"hits\":[]}", sr.toJson().toString());
	}

	@Test
	public void testSampleAndRerank() {
		SearchResult sr = new SearchResult();
		Hit h = new Hit("The ultimate test", "Oh yeah", "http://searsia.org", 
				"http://searsia.org/images/search.png");
		sr.addHit(h);
        String terms = h.toIndexVersion().toLowerCase();
        h = new Hit("Another test", "yeah", "http://searsia.org/test.html", 
                "http://searsia.org/images/search.png");
        sr.addHit(h);
        terms += " " + h.toIndexVersion().toLowerCase();
		String notThis = "test";
    	String term = sr.randomTerm(notThis);
        Assert.assertFalse("Same random term", term.equals(notThis));
		Assert.assertTrue("Index contains random term: " + term, terms.contains(term));
        Assert.assertEquals("Total nr of hits", 2, sr.getHits().size());
        sr.scoreReranking("test", "or");
        Assert.assertEquals("Nr of hits after reranking", 2, sr.getHits().size());
		sr.scoreReranking("doesnotmatch", "or");
		Assert.assertEquals("Query matches zero results", 0, sr.getHits().size());
	}
	
	@Test
	public void testCast() {
	    float score = 0.1f;
        Hit h1 = new Hit();
        h1.put("score", score);
        Assert.assertEquals(score, h1.getScore(), 0.0001f);
        Hit h2 = new Hit();
        h2.put("score", Float.toString(score));
        Assert.assertEquals(score, h2.getScore(), 0.0001f);
        Hit h3 = new Hit();
        h3.put("score", "wrong means zero");
        Assert.assertEquals(0.0f, h3.getScore(), 0.0001f);
        Hit h4 = new Hit("{\"title\":\"boo\",\"score\":1.0}");
        Assert.assertEquals(1.0f, h4.getScore(), 0.0001f);
        Hit h5 = new Hit("{\"title\":\"boo\",\"score\":1}");
        Assert.assertEquals(1.0f, h5.getScore(), 0.0001f);
        Hit h6 = new Hit("{\"title\":\"boo\",\"score\":9.7E-4}");
        Assert.assertTrue(h6.getScore() > 0.0f);
	}
}
