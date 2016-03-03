package org.searsia.index;

import java.io.IOException;

import org.junit.Assert;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import org.searsia.index.ResourceEngines;
import org.searsia.engine.SearchEngine;

public class TestResourceEngines {
	
    private static final String PATH  = "target/index-test";
    private static final String INDEX = "test";
    private static ResourceEngines engines;
   
    @BeforeClass
    public static void setUp() throws Exception {
    	engines = new ResourceEngines(PATH, INDEX);
    	SearchEngine engine = searsia();
    	engines.putMother(engine);
    }
    
    @AfterClass
    public static void lastThing() throws IOException {
    	engines.put(newby());
    	checkFiles();
    }
     
    private static SearchEngine utwente() {
    	SearchEngine e = new SearchEngine("http://utwente.nl/search?q={q}", "567");
    	e.setName("UT");
    	return e;
    }
    
    private static SearchEngine searsia() {
    	SearchEngine e = new SearchEngine("http://searsia.com/?q={q}", "1234");
    	e.addPrivateParameter("api", "topsecret");
    	return e;
    }
    
    private static SearchEngine newby() {
    	SearchEngine e = new SearchEngine("http://new.com/?q={q}");
    	e.changeId("890");
    	e.addPrivateParameter("apikey", "secret");
    	return e;
    }
    
    private static SearchEngine me() {
    	SearchEngine e = new SearchEngine("http://me.org");
    	e.setName("Me");
    	return e;
    }
    
    
    public static void checkFiles() throws IOException {
    	SearchEngine e1 = me();
    	SearchEngine e2 = engines.getMyself();
    	Assert.assertTrue("Trying to retrieve me", e1.equals(e2));
    	SearchEngine e3 = utwente();
    	SearchEngine e4 = engines.get(e3.getId());
    	Assert.assertTrue("Trying to retrieve utwente", e3.equals(e4));
    	Assert.assertTrue("No private parameters expected", e4.getJsonPrivateParameters() == null);
    	SearchEngine e6 = engines.get(newby().getId());
    	Assert.assertTrue("Private parameters expected", e6.getJsonPrivateParameters() != null);
    	Assert.assertTrue("Top 1", engines.topValues("", 1).size() == 1);
    	Assert.assertTrue("Top 2", engines.topValues(null, 2).size() == 2);
    	Assert.assertTrue("Top 3", engines.topValues("anything", 3).size() == 3);
    }
	
    @Test
    public void addResource() {
    	SearchEngine e1 = utwente();
    	engines.put(e1);
    	SearchEngine e2 = engines.get(e1.getId());
    	Assert.assertTrue("add", e1.equals(e2));
    }
 
    @Test
    public void addMe() {
    	SearchEngine e1 = me();
    	engines.putMyself(e1);
    	SearchEngine e2 = engines.getMyself();
    	Assert.assertTrue("me", e1.equals(e2));
    }

}
