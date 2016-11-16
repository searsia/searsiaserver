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
import java.security.MessageDigest;
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
        				result.addQueryResourceRankDate(engine.getId());
                	}
               		index.offer(result);
               		logSample(engine.getId(), result.getQuery());
                }
            } catch (Exception e) { 
            	logWarning("Sampling " + engine.getId() + " failed: " + e.getMessage());
            }
        }
    }

    
    private static void testResources(Resource mother, SearchResult result, ResourceIndex engines) {
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
    

    private static void getResources(SearchResult result, ResourceIndex index) {
    	for (Hit hit: result.getHits()) {
    	     String rid = hit.getString("rid");
    	     if (rid != null && !index.containsKey(rid)) {
    	    	 //index.reserve(rid);
    	     }
    	}
    }
    

    private static String uriToTemplate(String uri) {
      	if (!(uri == null) && !(uri.contains("{q"))) {
       		if (!uri.endsWith("/")) {
   	    		uri += "/";
   		    }
   		    uri += "search?q={q}";
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
	
	private static void printMessage(String message, Boolean isQuiet) {
        if (!isQuiet) {
            System.err.println(message);
        }
	}

	
    // for 'random' ids, if not provided   
    private static String getHashString(String inputString) {
        MessageDigest md;
        byte[] hash;
        try {
            md = MessageDigest.getInstance("MD5");
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        try {
            hash = md.digest(inputString.getBytes("UTF-8"));
        } catch (java.io.UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        StringBuilder sb = new StringBuilder();
        for(byte b : hash){
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
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
        printMessage("Searsia server " + SearsiaApplication.VERSION, options.isQuiet());

        
    	// Connect to the mother engine and gather information from the mother. 
   		Resource myself = null;
    	Resource mother = null;
    	Resource connect =  new Resource(options.getMotherTemplate(), null);
    	SearchResult result = null;
  	    try {
           	result = connect.search();
           	mother = result.getResource();
           	if (mother.getAPITemplate() == null) {
           		mother.setUrlAPITemplate(options.getMotherTemplate());
           	}
       		myself = mother.deepcopy();
       		myself.setUrlAPITemplate(options.getMyURI());
      	} catch (SearchException e) {
            System.err.println("Warning: Connection failed: " + e.getMessage());
      	}

  	    
  	    if (options.isTest()) {
        	printMessage("Testing: " + myself.getId(), options.isQuiet());
  	        try {
  	        	
  	    	    result = mother.search(mother.getTestQuery());
  	    	    if (!options.isQuiet()) {
  	    	    	//System.out.println(result.toJson());
  	    	    	if (result.getHits().isEmpty()) {
  	    	    		System.err.println("Test failed.");
  	    	  	        System.exit(1);
  	    	    	} else {
  	    	    		System.err.println("Ok.");
  	    	  	        System.exit(0);
  	    	    	}
  	    	    }
  	        } catch (SearchException e) {
  	        	printMessage("Error: " + e.getMessage(), options.isQuiet());
  	        }
        } else {
        	printMessage("Starting: " + myself.getId(), options.isQuiet());
        }

  	    
        // Create or open indexes. The index is the MD5 of the mother     	
        String fileName = getHashString(options.getMotherTemplate());
    	String path     = options.getIndexPath();
        Level level     = options.getLoggerLevel();
        try {
        	engines  = new ResourceIndex(path, fileName);
        	index    = new SearchResultIndex(path, fileName, options.getCacheSize());
    		setupQueryLogger(path, fileName, level);
    	} catch (Exception e) {
            printMessage("Setup failed: " + e.getMessage(), options.isQuiet());
            System.exit(1);
    	}

        if (mother == null || myself == null) {
        	mother = engines.getMother();
        	myself = engines.getMyself();
        } else {
   		    engines.putMother(mother);
   		    engines.putMyself(myself);
        }
        

    	// Start the web server
		String myURI = options.getMyURI();
    	try {
            server = GrizzlyHttpServerFactory.createHttpServer(URI.create(myURI), 
                new SearsiaApplication(index, engines));
    	} catch (Exception e) {
            System.err.println("Server failed: " + e.getMessage());
    		System.exit(1);    		
    	}
        printMessage("API end point: " + uriToTemplate(myURI), options.isQuiet());
        printMessage("Use Ctrl+c to stop.", options.isQuiet());

        // Start the update daemon
        if (!options.isTest()) {
            try {
                searsiaDaemon(index, engines, options.getPollInterval());
            } catch (InterruptedException e) {  }
        }
        server.shutdownNow();
    }
}
