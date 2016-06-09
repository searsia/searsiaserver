/*
 * Copyright 2016 Searsia
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.searsia;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;

import org.apache.log4j.Appender;
import org.apache.log4j.DailyRollingFileAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.json.JSONObject;
import org.searsia.index.SearchResultIndex;
import org.searsia.index.ResourceIndex;
import org.searsia.web.SearsiaApplication;
import org.searsia.engine.Resource;
import org.searsia.engine.SearchException;


/**
 * Searsia Main class
 * 
 * Start as:  java -jar target/searsiaserver.jar
 * More info: java -jar target/searsiaserver.jar --help
 * 
 * @author Djoerd Hiemstra and Dolf Trieschnigg
 * 
 */
public class Main {
	
	private static final Logger LOGGER = Logger.getLogger("org.searsia");
	private static final DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.S");
	private static Random random   = new Random();

	
    private static void searsiaDaemon(SearchResultIndex index, ResourceIndex engines, 
    		int pollInterval) throws InterruptedException {
    	Resource mother = engines.getMother();
    	Resource engine = null;
        while(true) {
            Thread.sleep(pollInterval * 1000);
            try {
                if (!index.check()) {
                	SearchResult result = null;
                	if (mother != null && random.nextBoolean()) { // sample mostly from mother
                		engine = mother;
                       	result = engine.randomSearch();
                	} else {
                    	engine = engines.getRandom();
                       	result = engine.randomSearch();
        				result.removeResourceRank();     // only trust your mother
                	}
               		index.offer(result);
               		logSample(engine.getId(), result.getQuery());
                }
            } catch (Exception e) { 
            	logWarning("Sampling " + engine.getId() + " failed: " + e.getMessage());
            }
        }
    }

    
    private static void getResources(Resource mother, SearchResult result, ResourceIndex engines) {
    	int i = 0;
    	for (Hit hit: result.getHits()) {
    	     String rid = hit.getString("rid");
    	     if (rid != null && !engines.containsKey(rid)) {
    	    	 Resource engine;
         	     i += 1;
    	    	 try {
    	             engine = mother.searchResource(rid);
    	    	 } catch (SearchException e) {
    	    		 System.err.println("Warning: Unable to get resources from " + mother.getId());
    	    		 break;
    	    	 }
    	    	 try {
    	    	     engines.put(engine);
    	    	 } catch(Exception e) {
    	    		 System.err.println("Error: " + e.getMessage());
    	    		 System.exit(1);
    	    	 }
     	     } 
    	     if (i > 10) {
    	    	 break; // not more than the first 10.
    	     }
    	}
    }
    

    private static String uriToTemplate(String uri) {
      	if (!(uri == null) && !(uri.contains("{q"))) {
       		if (!uri.endsWith("/")) {
   	    		uri += "/";
   		    }
   		    uri += "search?q={q?}&r={r?}";
    	}
    	return uri;
    }


	private static void logWarning(String message) {
		JSONObject r = new JSONObject();
		r.put("time", df.format(new Date()));
		r.put("message", message);
		LOGGER.warn(r.toString());
	}


	private static void logSample(String resourceid, String query) {
		JSONObject r = new JSONObject();
		r.put("time", df.format(new Date()));
		r.put("sample", resourceid);
		r.put("query", query);
		LOGGER.info(r.toString());
	}


    /**
     * Attaches a rolling file logger for search queries
     *
     * @param path
     * @param filename
     * @throws IOException
     */
	private static void setupQueryLogger(String path, String filename, Level level) throws IOException {
        Path querylogDir = Paths.get(path, filename + "_log");
		if (!Files.exists(querylogDir)) {
			Files.createDirectories(querylogDir);
		}
		Appender appender = new DailyRollingFileAppender(
				new PatternLayout("%m%n"),
				querylogDir.resolve("queries.log").toString(),
				"'.'yyyy-MM-dd");
		LOGGER.addAppender(appender);
		LOGGER.setLevel(level);
		logWarning("Searsia restart");
	}


    public static void main(String[] args) {
    	ResourceIndex engines = null;
    	SearchResultIndex index = null;
    	SearsiaOptions options  = null; 
    	HttpServer server       = null;

    	// Get options. This will also set the default options.
    	try {
    	    options = new SearsiaOptions(args);
        } catch (IllegalArgumentException e) {
        	System.exit(1);
        }

        if (!options.isQuiet()) {
            System.err.println("Searsia server " + SearsiaApplication.VERSION);
    	}

    	// Connect to the mother engine and gather information from the mother. 
    	String motherTemplate = options.getMotherTemplate();
    	Resource mother = null;
        SearchResult result = null;
        if (motherTemplate != null) {
        	mother = new Resource(motherTemplate);
    	    try {
               	result = mother.search();
         	} catch (SearchException e) {
                System.err.println("Error: Connection failed: " + e.getMessage());
    		    System.exit(1);
         	}
    		Resource newMother = result.getResource();
    		if (newMother != null) {
    			String id = newMother.getId();
    			if (id != null) { 
    		        mother.changeId(id);
    			}
    			mother.setPrior(newMother.getPrior());
    			mother.setName (newMother.getName());
    			mother.setFavicon(newMother.getFavicon());
    			mother.setBanner(newMother.getBanner());
    			mother.setTestQuery(newMother.getTestQuery());
    			mother.setUrlUserTemplate(newMother.getUrlUserTemplate());
    		}
            if (!options.isQuiet()) {
                System.err.println("Connected to: " + mother.getId());	
            }
        }

    	// This is about me:
        String myURI = options.getMyURI();
        String myTemplate = uriToTemplate(myURI);
        Resource me = null;
        String myId = options.getMyName();
        if (myId == null) {
        	if (motherTemplate != null) { 
        		myId = mother.getId(); // no Id and mother? Take my mother's name
            	me = new Resource(myTemplate, myId);
        	} else {
            	me = new Resource(myTemplate);  
            	myId = me.getId();  // no Id and no mother?, this will result in a random Id
        	}
        } else {
        	me = new Resource(myTemplate, myId);
        }
        String prefix;
    	if (motherTemplate != null) {
    		prefix = mother.getMD5();
    	} else {
    		prefix = "local";
    	}
    	
    	
        // Create or open indexes. The index name combines the mother unique MD5 with the local Id;
    	// MD5, so we will not mix indexes of we have two mothers with the same name.    	
    	String fileName = prefix + "_" + myId;
    	String path     = options.getIndexPath();
        Level level     = options.getLoggerLevel();
        try {
        	engines  = new ResourceIndex(path, fileName);
        	index    = new SearchResultIndex(path, fileName, options.getCacheSize());
    		setupQueryLogger(path, fileName, level);
    	} catch (Exception e) {
            System.err.println("Setup failed: " + e.getMessage());
            System.exit(1);
    	}

    	
    	// My mother: Remember her, and ask her for advice
		if (mother != null) {
			try {
    			engines.putMother(mother);
			} catch (Exception e) { 
				System.err.println("Error: " + e.getMessage());
				System.exit(1);
			}
			getResources(mother, result, engines);
		}

    	// Myself:
    	Resource newMe = engines.getMyself();
        if (newMe != null) {
			me.setName (newMe.getName());
			me.setFavicon(newMe.getFavicon());
			me.setBanner(newMe.getBanner());
			me.setTestQuery(newMe.getTestQuery());
			me.setUrlUserTemplate(newMe.getUrlUserTemplate());
        } else if (mother != null) {
        	me.setName(mother.getName());
            me.setFavicon(mother.getFavicon());  // first time? get images from mother.
            me.setBanner(mother.getBanner());
        }
    	me.setPrior(engines.maxPrior());
    	try {
    		engines.putMyself(me);
    	} catch (Exception e) {
    		System.err.println("Error: " + e.getMessage());
    		System.exit(1);
    	}


    	// Start the web server
		Boolean openWide = options.openedWide();
    	try {
            server = GrizzlyHttpServerFactory.createHttpServer(URI.create(myURI), 
                new SearsiaApplication(index, engines, openWide));
    	} catch (Exception e) {
            System.err.println("Server failed: " + e.getMessage());
    		System.exit(1);    		
    	}
        if (!options.isQuiet()) {
            System.err.println("API template: " + uriToTemplate(myURI));
            System.err.println("Use Ctrl+c to stop.");
    	}

        // Start the update daemon
        if (!options.isExit()) {
            try {
                searsiaDaemon(index, engines, options.getPollInterval());
            } catch (InterruptedException e) {  }
        }
        server.shutdownNow();
    }
}
