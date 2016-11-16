package org.searsia.index;

import java.io.IOException;

import org.junit.Assert;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import org.searsia.index.ResourceIndex;
import org.searsia.engine.Resource;

public class TestResourceIndex {
	
    private static final String PATH  = "target/index-test";
    private static final String INDEX = "test";
    private static ResourceIndex engines;
   
    @BeforeClass
    public static void setUp() throws Exception {
    	engines = new ResourceIndex(PATH, INDEX);
    	Resource engine = searsia();
    	engines.putMother(engine);
    }
    
    @AfterClass
    public static void lastThing() throws IOException {
    	engines.put(newby());
    	checkFiles();
    }
     
    private static Resource utwente() {
    	Resource e = new Resource("http://utwente.nl/search?q={q}", "567");
    	e.setName("UT");
    	return e;
    }
    
    private static Resource searsia() {
    	Resource e = new Resource("http://searsia.com/?q={q}", "1234");
    	e.addPrivateParameter("api", "topsecret");
    	return e;
    }
    
    private static Resource newby() {
    	Resource e = new Resource("http://new.com/?q={q}", "new");
    	e.changeId("890");
    	e.addPrivateParameter("apikey", "secret");
    	return e;
    }
    
    private static Resource me() {
    	Resource e = new Resource("http://me.org", "me");
    	e.setName("Me");
    	return e;
    }
    
    
    public static void checkFiles() throws IOException {
    	Resource e1 = me();
    	Resource e2 = engines.getMyself();
    	Assert.assertTrue("Trying to retrieve me", e1.equals(e2));
    	Resource e3 = utwente();
    	Resource e4 = engines.get(e3.getId());
    	Assert.assertTrue("Trying to retrieve utwente", e3.equals(e4));
    	Assert.assertTrue("No private parameters expected", e4.getJsonPrivateParameters() == null);
    	Resource e6 = engines.get(newby().getId());
    	Assert.assertTrue("Private parameters expected", e6.getJsonPrivateParameters() != null);
    	Assert.assertTrue("Top 1", engines.topValues("anything", 1).size() == 1);
    	Assert.assertTrue("Top 2", engines.topValues(null, 2).size() == 2);
    }
	
    @Test
    public void addResource() {
    	Resource e1 = utwente();
    	engines.put(e1);
    	Resource e2 = engines.get(e1.getId());
    	Assert.assertTrue("Add", e1.equals(e2));
    }
 
    @Test
    public void addMe() {
    	Resource e1 = me();
    	engines.putMyself(e1);
    	Resource e2 = engines.getMyself();
    	Assert.assertTrue("Me", e1.equals(e2));
    }

    @Test
    public void getMother() {
    	Resource e1 = searsia();
    	Resource e2 = engines.getMother();
    	Assert.assertTrue("Mother", e1.equals(e2));
    }


}
