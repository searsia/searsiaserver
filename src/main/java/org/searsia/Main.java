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
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.logging.SimpleFormatter;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.searsia.index.SearchResultIndex;
import org.searsia.index.ResourceIndex;
import org.searsia.web.SearsiaApplication;
import org.searsia.engine.Resource;
import org.searsia.engine.SearchException;


/**
 * Searsia Main class. Does the following actions:
 * 
 *  1. Connect to mother peer;
 *  2. If it runs in test mode, test the mother, print results and exit;
 *  3. Open/create Lucene indexes;
 *  4. Get the 10 top resources if not existing or too old;
 *  5. Run the web server;
 *  6. Run the daemon to periodically poll the mother and resources.
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
        while(true) {
            Thread.sleep(pollInterval * 1000);
            try {
                if (!index.checkFlush()) {
                	SearchResult result = null;
                	if (mother != null && random.nextBoolean()) { // sample mostly from mother
                		engine = mother;
                        LOGGER.finest("Next: mother sample");
                       	result = engine.randomSearch();
                        Resource newmother = result.getResource();
                        if (newmother != null && newmother.getId().equals(mother.getId())) {
                            if (newmother.getAPITemplate() == null) {
                                newmother.setUrlAPITemplate(mother.getAPITemplate());
                            }
                            engines.putMother(newmother);
                            engines.putMyself(newmother.getLocalResource());
                        } else {
                            LOGGER.warning("Unable to update mother: Did ids change?");
                        }
                        getResources(mother, result, engines);
                	} else {
                    	engine = engines.getRandom();
                        LOGGER.finest("Next sample: " + engine.getId());
                       	result = engine.randomSearch();
        				result.removeResource();     // only trust your mother
        				result.addResourceDate(engine.getId());
                	}
               		index.offer(result);
               		LOGGER.info("Sampled " + engine.getId() + ": " + result.getQuery());
                }
            } catch (Exception e) {
                if (engine != null) {
                	LOGGER.warning("Sampling " + engine.getId() + " failed: " + e.getMessage());
                } else {
                    LOGGER.warning("Flushing index to disk failed:" + e.getMessage());
                }
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
    	    	     } catch (SearchException e) {
    	    	         LOGGER.warning("Warning: Update failed: " + e.getMessage());
    	    	     }
                     if (engine != null && rid.equals(engine.getId())) { 
                         engines.put(engine);
                         if (engine.isDeleted()) {
                             LOGGER.fine("Deleted: " + rid);
                         } else {
                             LOGGER.fine("Updated: " + rid);
                         }
                     } else {
                         LOGGER.warning("Warning: Resource not found: " + rid);
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
                uri += rid + "?q={searchTerms}&page={startPage?}";
            } else if (!uri.contains("{q") && !uri.contains("{searchTerms")) { // check for tests on searsia.org
                uri += "?q={searchTerms}&page={startPage?}";
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
     * @return Unique hash
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
        boolean isDone = false;
    	int startPage = mother.getIndexOffset();
    	Map<String, Boolean> tested = new HashMap<String, Boolean>();
    	tested.put(mother.getId(), true);
    	while (!result.getHits().isEmpty() && !isDone) {
    		isDone = true;
            for (Hit hit: result.getHits()) {
            	String rid = hit.getRid();
                if (rid != null && !tested.containsKey(rid)) {
                	tested.put(rid,  true);
                	isDone = false;
                	Resource engine = null;
                    try {
                        engine = mother.searchResource(hit.getRid());
                        testEngine(engine, "none", isQuiet);
                    } catch (Exception e) {
                        nrFailed += 1;
                        if (engine == null) { // resource not found, so test did not even start
                            printMessage("Testing: " + hit.getRid(), isQuiet);
                        } 
                        printMessage("Test failed: " + e.getMessage(), isQuiet);                        	
                    }
                }
            }
            startPage += 1;
            try {
                result = mother.search(mother.getTestQuery(), "all", startPage);
            } catch (Exception e) {
            	throw new SearchException("Mother error: " + e.getMessage());
            }
    	}
        if (nrFailed > 0) {
            throw new SearchException(nrFailed + " engines failed.");
        }
    }
    
    
	private static void testEngine(Resource mother, String debugInfo, Boolean isQuiet) throws SearchException {
	    if (mother.getName() != null) {
            printMessage("Testing: " + mother.getId() + " (" + mother.getName() + ")", isQuiet);
	    } else {
            printMessage("Testing: " + mother.getId(), isQuiet);	        
	    }
        SearchResult result = null;
        result = mother.randomSearch(debugInfo);
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
            System.out.flush();
        }
        String error = result.getError();
        if (error != null) {
            throw new SearchException(error);
        }
        if (result.getHits().size() < 10) {
            printMessage("Warning: less than 10 results for query '" + result.getQuery() + "'; see \"testquery\" or \"rerank\".", isQuiet);
        } else if (result.getHits().size() > 49) {
            printMessage("Warning: more than 49 results for query '" + result.getQuery() + "'", isQuiet);
        }
        if (debugInfo.equals("all")) {
        	String rid = null;
        	if (result.getResource() != null) {
        		rid = result.getResource().getId();
        	}
        	if (rid != null && rid.equals(mother.getId())) { // do not trust resources if the mother API provides another ID than the mother ID
                testAll(mother, result, isQuiet);
        	} else if (rid == null ){
        		printMessage("Warning: no resources available.", isQuiet);
        	} else {
        		printMessage("Warning: no resources. ID '" + mother.getId() + "' changed to '" + rid + "'", isQuiet);        		
        	}
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
		String fileName = logDir.resolve("searsia.log").toString();
        Handler fileHandler = new FileHandler(fileName, 1048576, 100, true);
        fileHandler.setFormatter(new SimpleFormatter());
        LogManager.getLogManager().reset();
		LOGGER.addHandler(fileHandler);
		LOGGER.setLevel(level);
		LOGGER.warning("Searsia restart");
	}


    public static void main(String[] args) {
    	ResourceIndex engines   = null;
    	SearchResultIndex index = null;
    	SearsiaOptions options  = null; 
    	HttpServer server       = null;

    	// Get options. This will also set the default options.
    	try {
    	    options = new SearsiaOptions(args);
        } catch (Exception e) {
            fatalError(e.getMessage());
        }
    	if (options.isHelp()) { 
    	    System.exit(0); 
    	}
        printMessage("Searsia server " + SearsiaApplication.VERSION, options.isQuiet());
       
        
    	// Connect to the mother engine and gather information from the mother.
        Resource myself  = null;
    	Resource mother  = null;
    	Resource connect = new Resource(options.getMotherTemplate());
    	String version   = null;
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
        if (version == null || !version.startsWith("v1")) {
            fatalError("Wrong major Searsia version. Must be v1.x.x.");
        }


        if (mother.getAPITemplate() == null) {
            mother.setUrlAPITemplate(options.getMotherTemplate());
        } else {
            if (!sameTemplates(mother.getAPITemplate(), options.getMotherTemplate(), mother.getId())) {
                printMessage("Warning: Mother changed to " + mother.getAPITemplate(), options.isQuiet()); 
            }
            if (mother.getAPITemplate().contains("{q")) {
                printMessage("Warning: API Template parameter {q} is deprecated. Use {searchTerms}.", options.isQuiet());
            }
        }
        myself = mother.getLocalResource();
        String fileName = myself.getId() + "_" + getHashString(mother.getAPITemplate());
        String path     = options.getIndexPath();
        Level level     = options.getLoggerLevel();

  	    
        // If test is set, test the mother
  	    if (options.getTestOutput() != null) {
  	        String tmpDir = System.getProperty("java.io.tmpdir");
  	        if (tmpDir != null) { 
  	            path = tmpDir;
  	        }
  	        try {
  	            testEngine(mother, options.getTestOutput(), options.isQuiet());
                printMessage("Test succeeded.", options.isQuiet());
  	        } catch (Exception e) {
  	            fatalError("Test failed: " + e.getLocalizedMessage());
  	        }
        } else {
        	printMessage("Starting: " + myself.getName() + " (" + myself.getId() + ")", options.isQuiet());
        }

        // Create or open indexes. The filename appends the MD5 of the id so we don't confuse indexes
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
	    
	    // Export index and exit
	    if (options.isExport()) {
	        String encoding = System.getProperties().getProperty("file.encoding");
	        if (encoding == null || !encoding.equals("UTF-8")) {
	            printMessage("Warning: Unknown encoding. Set JVM encoding with '-Dfile.encoding=UTF-8'", options.isQuiet());
	        }
            printMessage("Exporting index...", options.isQuiet());
	        try {
    	        engines.dump();
	            engines.close();
                index.dump();
	            index.close();
	        } catch (IOException e) {
	            fatalError("Index export failed: " + e.getMessage());
	        }
            printMessage("Done.", options.isQuiet());
	        System.exit(0);
	    }

    	// Start the web server
        String myURI = removeFileNameUri(options.getMyURI());
    	try {
    	    SearsiaApplication app = new SearsiaApplication(index, engines, options);
            server = GrizzlyHttpServerFactory.createHttpServer(URI.create(myURI), app); 
    	} catch (Exception e) {
            fatalError("Server failed: " + e.getMessage());
    	}

        
        // Start the update daemon if not testing
        if (options.getTestOutput() == null) {
        	String myAPI = normalizedUriToTemplate(myURI + "searsia/", myself.getId()); 
            printMessage("API template: " + myAPI, options.isQuiet());
            printMessage("Use Ctrl+c to stop.", options.isQuiet());
            try {
                searsiaDaemon(index, engines, options);
            } catch (InterruptedException e) { }
        }
        server.shutdownNow(); // Catch ctrl+c: http://www.waelchatila.com/2006/01/13/1137143896635.html
    }
} 
