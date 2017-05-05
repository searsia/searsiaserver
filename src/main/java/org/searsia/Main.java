/*
 * Copyright 2016-2017 Searsia
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
 * Searsia Main class does the following actions:
 * 
 *  1. Connect to mother peer
 *  2. If it runs in test mode, test the mother, print results and exit.
 *  3. Open/create Lucene indexes
 *  4. Get the 10 top resources if older than one hour
 *  5. Run the web server
 *  6. Run the daemon to periodically poll the mother and resources
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
    		SearsiaOptions options) throws InterruptedException {
    	Resource mother  = engines.getMother();
    	Resource engine  = null;
    	int pollInterval = options.getPollInterval();
    	String myUri     = options.getMyURI();
        while(true) {
            Thread.sleep(pollInterval * 1000);
            try {
                if (!index.checkFlush()) {
                	SearchResult result = null;
                	if (mother != null && random.nextBoolean()) { // sample mostly from mother
                		engine = mother;
                       	result = engine.randomSearch();
                        Resource newmother = result.getResource();
                        if (newmother != null && newmother.getId().equals(mother.getId())) {
                            engines.putMother(newmother);
                            engines.putMyself(newmother.getLocalResource(myUri));
                        } else {
                            LOGGER.warn("Unable to update mother: Did ids change?");
                        }
                        getResources(mother, result, engines);
                	} else {
                    	engine = engines.getRandom();
                       	result = engine.randomSearch();
        				result.removeResourceQuery();     // only trust your mother
        				result.addQueryResourceDate(engine.getId());
                	}
               		index.offer(result);
               		LOGGER.info("Sampled " + engine.getId() + ": " + result.getQuery());
                }
            } catch (Exception e) { 
            	LOGGER.warn("Sampling " + engine.getId() + " failed: " + e.getMessage());
            }
        }
    }

    
    private static int getResources(Resource mother, SearchResult result, ResourceIndex engines) {
    	int i = 0;
    	for (Hit hit: result.getHits()) {
    	     String rid = hit.getString("rid");
    	     if (rid != null ) {
    	         Resource engine = engines.get(rid);
    	         if (engine == null || engine.getLastUpdatedSecondsAgo() > 7200) { // TODO: option for 7200 ?
    	     	     i += 1;
    	    	     try {
    	    	         engine = mother.searchResource(rid);
    	    	     } catch (Exception e) {
    	    	         LOGGER.warn("Warning: Update failed: " + e.getMessage());
    	    	     }
                     if (engine != null && rid.equals(engine.getId())) { 
                         engines.put(engine);
                         LOGGER.debug("Updated " + rid);
                     } else {
                         LOGGER.warn("Warning: Resource not found: " + rid);
                     }
    	         }
     	     } 
    	     if (i > 10) {
    	         break; // not more than the first 10 per check
    	     }
    	}
        engines.flush();
    	return i;
    }

    private static boolean sameTemplates(String uri1, String uri2, String myId) {
        if (uri1 == null) {
            return (uri2 == null);
        } else {
            uri1 = uri1.replaceAll("\\?.*$", "");
            uri2 = uri2.replaceAll("\\?.*$", "");
            return  uri1.equals(uri2);      
        }
    } 
    
    private static String removeFileNameUri(String uri) {
        if (uri != null) {
            uri = uri.replaceAll("\\/[^\\/]+$", "/");
        }
        return uri;
    }
    
    private static String normalizedUriToTemplate(String uri, String rid) {
        if (uri != null) {
            if (uri.endsWith("/") ) {
                uri += rid + ".json?q={q}";
            } else if (!uri.contains("{q")) { // check for tests on searsia.org
                uri += "?q={q}";
            }
            
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

    /**
     * For a unique filename (public because used in searsiafedweb)
     * @param inputString
     * @return
     */
	public static String getHashString(String inputString) {
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

    
    private static void testAll(Resource mother, SearchResult result, Boolean isQuiet) throws SearchException {
        int nrFailed = 0;
        for (Hit hit: result.getHits()) {
            if (hit.getRid() != null) {
                try {
                    Resource engine = mother.searchResource(hit.getRid());
                    testMother(engine, "none", isQuiet);
                } catch (Exception e) {
                    nrFailed += 1;
                    printMessage("Test failed: " + e.getMessage(), isQuiet);
                }
            }                    
        }
        if (nrFailed > 0) {
            throw new SearchException(nrFailed + " engines failed.");
        }
    }
    
    
	private static void testMother(Resource mother, String debugInfo, Boolean isQuiet) throws SearchException {
        printMessage("Testing: " + mother.getName() + " (" + mother.getId() + ")", isQuiet);
        SearchResult result = null;
        result = mother.search(mother.getTestQuery(), debugInfo);
        if (!isQuiet) {
            if (debugInfo.equals("json")) {
                System.out.println(result.toJson().toString(2));
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
            throw new SearchException("No results for test query.");
        } 
        if (result.getHits().size() < 10) {
            printMessage("Warning: less than 10 results for query: " + result.getQuery() + "; see \"testquery\" or \"rerank\".", isQuiet);
        }
        if (debugInfo.equals("all")) {
            testAll(mother, result, isQuiet);
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
		// Appender appender = new ConsoleAppender(new PatternLayout("%m%n"), ConsoleAppender.SYSTEM_ERR);	       
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
    	Resource connect = new Resource(options.getMotherTemplate());
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
        if (!options.getMotherTemplate().matches(".*" + mother.getId() + "[^/]*$")) {
            fatalError("API Template (" + options.getMotherTemplate() + "): file name must contain id (" + mother.getId() +")");
        }
        if (version != null && !version.startsWith("v1")) {
            fatalError("Wrong major Searsia version " + version + ": Must be v1.0.0 or higher.");
        }
        
        if (mother.getAPITemplate() == null) {
            mother.setUrlAPITemplate(options.getMotherTemplate());
        } else if (!sameTemplates(mother.getAPITemplate(), options.getMotherTemplate(), mother.getId())) {
            printMessage("Warning: Mother changed to " + mother.getAPITemplate(), options.isQuiet()); 
        }
        myself = mother.getLocalResource(options.getMyURI());

  	    
  	    // If test is set, test the mother
  	    if (options.getTestOutput() != null) {
  	        try {
  	            testMother(mother, options.getTestOutput(), options.isQuiet());
                printMessage("Test succeeded.", options.isQuiet());
  	        } catch (Exception e) {
  	            fatalError("Test failed: " + e.getMessage());
  	        }
        } else {
        	printMessage("Starting: " + myself.getName() + " (" + myself.getId() + ")", options.isQuiet());
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
		String myURI = removeFileNameUri(options.getMyURI());
    	try {	    
    	    SearsiaApplication app = new SearsiaApplication(index, engines);
            server = GrizzlyHttpServerFactory.createHttpServer(URI.create(myURI), app); 
    	} catch (Exception e) {
            fatalError("Server failed: " + e.getMessage());
    	}

        
        // Start the update daemon if not testing
        if (options.getTestOutput() == null) {
            printMessage("API end point: " + normalizedUriToTemplate(myURI, myself.getId()), options.isQuiet());
            printMessage("Use Ctrl+c to stop.", options.isQuiet());
            try {
                searsiaDaemon(index, engines, options);
            } catch (InterruptedException e) { }
        }
        server.shutdownNow(); // Catch ctrl+c: http://www.waelchatila.com/2006/01/13/1137143896635.html
    }
} 
