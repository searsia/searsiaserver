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
import java.util.Random;

import org.apache.log4j.Appender;
import org.apache.log4j.DailyRollingFileAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
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
	private static Random random = new Random();

	
    private static void searsiaDaemon(SearchResultIndex index, ResourceIndex engines, 
    		int pollInterval) throws InterruptedException {
    	Resource mother = engines.getMother();
    	Resource engine = null;
        while(true) {
            Thread.sleep(pollInterval * 1000);
            try {
                if (!index.checkFlush()) {
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
               		LOGGER.info("Sample " + engine.getId() + ": " + result.getQuery());
                }
            } catch (Exception e) { 
            	LOGGER.warn("Sampling " + engine.getId() + " failed: " + e.getMessage());
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
    	    		 System.err.println("Warning: Not found: " + rid + ": " + e.getMessage());
    	    		 break;
    	    	 }
    	    	 try {
    	    	     engines.put(engine);
    	    	 } catch(Exception e) {
    	    		 fatalError(e.getMessage());
    	    	 }
     	     } 
    	     if (i > 10) {
    	         break; // not more than the first 10. Rest will follow when needed
    	     }
    	}
    }

    private static boolean sameTemplates(String uri1, String uri2, String myId) {
        if (uri1 == null) {
            return (uri2 == null);
        } else {
            return  uriNormalize(uri1, myId).equals(uriNormalize(uri2, myId));      
        }
    } 
    
    private static String uriNormalize(String uri, String myId) {
        if (uri != null) {
            uri = uri.replaceAll("\\?.*$", "");
            uri = uri.replaceAll("\\/?search\\/?", "");
            if (uri.endsWith(myId)) {
                uri = uri.replace(myId, "");
            }
        }
        return uri;
    }

    private static String uriToTemplate(String uri, String myId) {
      	if (!(uri == null) && !(uri.contains("{q"))) {
       		if (!uri.endsWith("/")) {
   	    		uri += "/";
   		    }
   		    uri += myId + "/search?q={q}";
    	}
    	return uri;
    }


	private static void printMessage(String message, Boolean isQuiet) {
        if (!isQuiet) {
            System.err.println(message);
        }
	}
	
	private static void fatalError(String message) {
	    System.err.println("ERROR: " + message);
	    System.exit(1);
	}

    // for unique filename
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

    
    private static void testMother(Resource mother, String debugInfo, Boolean isQuiet) {
        SearchResult result = null;
        try {
            result = mother.search(mother.getTestQuery(), debugInfo);
        } catch (SearchException e) {
            fatalError("Test failed: " + e.getMessage());
        }
        if (!isQuiet) {
            if (debugInfo.equals("json")) {
                System.out.println(result.toJson());
            } else if (debugInfo.equals("xml") || debugInfo.equals("response")) {
                String debugOut = result.getDebugOut();
                if (debugOut == null) {
                    System.out.println ("Warning: No " + debugInfo + " output.");
                } else {
                    System.out.println(debugOut);
                }
            }
        }
        System.out.flush();
        if (result.getHits().isEmpty()) {
            fatalError("Test failed: No results for test query.");
        } else {
            if (result.getHits().size() < 10) {
                printMessage("Warning: less than 10 results; see \"testquery\" or \"rerank\".", isQuiet);
            }
            printMessage("Test succeeded.", isQuiet);
        }
    }

    /**
     * Attaches a rolling file logger for search queries
     *
     * @param path
     * @param filename
     * @throws IOException
     */
	private static void setupLogger(String path, String filename, Level level) throws IOException {
        Path logDir = Paths.get(path, filename + "_log");
		if (!Files.exists(logDir)) {
			Files.createDirectories(logDir);
		}
		Appender appender = new DailyRollingFileAppender(
				new PatternLayout("%p %d{ISO8601} %m%n"),
				logDir.resolve("searsia.log").toString(),
				"'.'yyyy-MM-dd");
		LOGGER.addAppender(appender);
		LOGGER.setLevel(level);
		LOGGER.warn("Searsia restart");
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
            fatalError(e.getMessage());
        }
    	if (options.isHelp()) { 
    	    System.exit(0); 
    	}
        printMessage("Searsia server " + SearsiaApplication.VERSION, options.isQuiet());
        
        
        
        
    	// Connect to the mother engine and gather information from the mother. 
   		Resource myself = null;
    	Resource mother = null;
    	Resource connect = new Resource(options.getMotherTemplate(), null);
    	String version  = null;
    	SearchResult result = null;
  	    try {
           	result = connect.searchWithoutQuery();	
           	mother = result.getResource();
           	version = result.getVersion();
      	} catch (SearchException e) {
            fatalError("Connection failed: " + e.getMessage());
      	}
        if (mother == null) {
            fatalError("Initialization failed: JSONObject[\"resource\"] not found.");
        }
        if (version != null && !version.startsWith("v1")) {
            printMessage("Warning: Wrong major Searsia version " + version, options.isQuiet());
        }
        myself = mother.deepcopy();
        myself.setUrlAPITemplate(options.getMyURI());
        if (mother.getAPITemplate() == null) {
            mother.setUrlAPITemplate(options.getMotherTemplate());
        } else if (!sameTemplates(mother.getAPITemplate(), options.getMotherTemplate(), mother.getId())) {
            printMessage("Warning: Mother changed to " + mother.getAPITemplate(), options.isQuiet()); 
        }
  	    

  	    // If test is set, test the mother
  	    if (options.getTestOutput() != null) {
  	        printMessage("Testing: " + mother.getId(), options.isQuiet());
  	        testMother(mother, options.getTestOutput(), options.isQuiet());
        } else {
        	printMessage("Starting: " + myself.getId(), options.isQuiet());
        }


        // Create or open indexes. The filename appends the MD5 of the id so we don't confuse indexes
        String fileName = myself.getId() + "_" + getHashString(options.getMotherTemplate());
        String path     = options.getIndexPath();
        Level level     = options.getLoggerLevel();
        try {
            setupLogger(path, fileName, level);
            engines  = new ResourceIndex(path, fileName);
            index    = new SearchResultIndex(path, fileName, options.getCacheSize());
        } catch (Exception e) {
            fatalError("Setup failed: " + e.getMessage());
    	}
	    engines.putMother(mother);
	    engines.putMyself(myself);
        
	    getResources(mother, result, engines);


    	// Start the web server
		String myURI = uriNormalize(options.getMyURI(), myself.getId());
    	try {
            server = GrizzlyHttpServerFactory.createHttpServer(URI.create(myURI), 
                new SearsiaApplication(index, engines));
    	} catch (Exception e) {
            fatalError("Server failed: " + e.getMessage());
    	}

        
        // Start the update daemon if not testing
        if (options.getTestOutput() == null) {
            printMessage("API end point: " + uriToTemplate(myURI, myself.getId()), options.isQuiet());
            printMessage("Use Ctrl+c to stop.", options.isQuiet());
            try {
                searsiaDaemon(index, engines, options.getPollInterval());
            } catch (InterruptedException e) {  }
        }
        server.shutdownNow();
    }
} 
