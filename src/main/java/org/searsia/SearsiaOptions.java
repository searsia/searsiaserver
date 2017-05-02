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

import java.io.File;

import org.apache.log4j.Level;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

/**
 * Searsia Server options
 * @author Djoerd Hiemstra
 *
 */
public class SearsiaOptions {
		
    /* See setDefaults() below */
    private String test;
    private Boolean quiet;
    private Boolean help;
    private Boolean dontshare;
	private int cacheSize;
    private int pollInterval;
    private int logLevel;
    private String myURI;
    private String motherTemplate;
    private String indexPath; 

    /**
     * Takes command line options and sensible defaults
     * 
     */
    public SearsiaOptions(String[] args) throws IllegalArgumentException {
    	Options options = new Options();
        options.addOption("c", "cache",    true,  "Set cache size (integer: number of result pages).");
        options.addOption("d", "dontshare",false, "Do not share resource definitions.");
        options.addOption("h", "help",     false, "Show help.");
        options.addOption("i", "interval", true,  "Set poll interval (integer: in seconds).");
        options.addOption("l", "log",      true,  "Set log level (0=off, 1=error, 2=warn=default, 3=info, 4=debug).");
        options.addOption("m", "mother",   true,  "Set url of mother's web service end point.");
        options.addOption("p", "path",     true,  "Set directory path to store the index.");
        options.addOption("q", "quiet",    false, "No output to console.");
        options.addOption("t", "test",     true,  "Print test output and exit (string: 'json', 'xml', 'response', 'all').");
        options.addOption("u", "url",      true,  "Set url of my web service endpoint.");
        setDefaults();
        parse(options, args);
        if (myURI == null) {
            myURI = "http://localhost:16842/searsia/" + lastDir(motherTemplate);
        }
    }

    
    private static String lastDir(String uri) {
        if (uri.contains("/")) {
            uri = uri.replaceAll("\\/[^\\/]*$", "");
            uri = uri.replaceAll("^.+\\/", "");
            return uri + "/";
        } else {
            return "";
        }
    }


    private void setDefaults() {
        test           = null; // no test 
        help           = false;
        quiet          = false;
        dontshare      = false;
        cacheSize      = 500;
        pollInterval   = 120;
        logLevel       = 2;
        myURI          = null; // is set in constructor
        motherTemplate = null;
        indexPath      = friendlyIndexPath();
    }
    
    
    private boolean pathExists(String path) {
    	if (path == null) return false;
    	File dir = new File(path);
        return dir.exists();
    }
    
    
    private String friendlyIndexPath() { 
    	String path;
    	String file = "searsia";
    	String home = System.getProperty("user.home");
    	if (home == null || !pathExists(home)) home = ".";
    	
    	String os   = System.getProperty("os.name").toLowerCase();
    	if (os.contains("win")) {  // On Windows
    	    path = System.getenv("AppData");
    	    if (!pathExists(path)) {
    	    	path = home;
    	    }
    	    file = "Searsia";
    	} else if (os.contains("nux") || os.contains("nix") | os.contains("aix") || os.contains("freebsd")) {
    	    path = home + "/.local/share";  // on Linux 
    	} else if (os.contains("mac")) {
    		path = home + "/Library/Application Support"; // on Apple
    	} else {
    		path = home;
    	}
	    File dir = new File(path, file);
        if (!dir.exists()) {
            if (!dir.mkdir()) {
            	dir = new File(home);
            }
        }
    	return dir.toString(); 
    }
    

    private void parse(Options options, String[] args) throws IllegalArgumentException {
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = null;
        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            throw new IllegalArgumentException(e.getMessage() + " (use '-h' for help)");
        }
        
        if (cmd.hasOption("c")) {
            cacheSize = new Integer(cmd.getOptionValue("c"));
            if (cacheSize < 30) {
            	cacheSize = 30;
            }
        }
        if (cmd.hasOption("t")) {
            test = cmd.getOptionValue("t").toLowerCase();
            if (!(test.equals("json") || test.equals("xml") || test.equals("response") || test.equals("all"))) {
                throw new IllegalArgumentException("Test output must be one of 'json', 'xml', 'response', or 'all'.");        	            	
            }
        }
        try {
            if (cmd.hasOption("i")) {
                pollInterval = new Integer(cmd.getOptionValue("i"));
                if (pollInterval < 5) {
                  	pollInterval = 5;
                }
            }
            if (cmd.hasOption("l")) {
                logLevel = new Integer(cmd.getOptionValue("l"));
                if (logLevel < 0) {
                	logLevel = 0;
                }
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
        if (cmd.hasOption("p")) {
            indexPath = cmd.getOptionValue("p");
        }
        if (cmd.hasOption("q")) {
            quiet = true;
        }
        if (cmd.hasOption("d")) {
            dontshare = true;
        }
        if (cmd.hasOption("u")) {
            myURI  = cmd.getOptionValue("u");
        }
        if (cmd.hasOption("m")) {
            motherTemplate = cmd.getOptionValue("m");
            if (!motherTemplate.matches("^https?://.*|^file:.*")) {
                motherTemplate = "file:" + motherTemplate.replace("\\", "/"); // TODO C:\file on Windows?
            }

        }
        if (cmd.hasOption("h") || cmd.getArgs().length < 0 || !cmd.hasOption("m")) {
            if (!cmd.hasOption("m")) {
                System.out.println("Please provide mother's url template (use '-m').");
            }
            help(options);
            help = true;
        }
    }
    
    
    private void help(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("SearsiaServer", options);
     }
    
    public int getCacheSize() {
    	return cacheSize;
    }

    public String getTestOutput() {
    	return test;
    }
    
    public int getLogLevel() {
    	return logLevel;
    }
    
    public Level getLoggerLevel() {
    	switch(logLevel) {
        	case 0 : return Level.OFF; 
        	case 1 : return Level.ERROR; // or FATAL we don't care
    	    case 2 : return Level.WARN;
    	    case 3 : return Level.INFO;
    	    case 4 : return Level.DEBUG;
    	    case 5 : return Level.TRACE;
    	    default: return Level.WARN;    	   
    	}
    }
    
    public int getPollInterval() {
    	return pollInterval;
    }
    
    public String getMyURI() {
    	return myURI;
    }
    
    public String getMotherTemplate() {
    	return motherTemplate;
    }
    
    public String getIndexPath() {
    	return indexPath;
    }
    
    public Boolean isQuiet() {
    	return quiet;
    }
    
    public Boolean isNotShared() {
        return dontshare;
    }
    
    public Boolean isHelp() {
        return help;
    }

    @Override
    public String toString() {
    	String result = "SearsiaOptions:";
    	result += "\n  Log Level     = " + getLoggerLevel();
    	result += "\n  Base Url      = " + getMyURI();
    	result += "\n  Mother        = " + getMotherTemplate();
    	result += "\n  Index Path    = " + getIndexPath();
    	result += "\n  Poll Interval = " + getPollInterval();
    	result += "\n  Cache Size    = " + getCacheSize();
    	result += "\n  Test Output   = " + getTestOutput();
        result += "\n  Do Not Share  = " + isNotShared();
    	return result;
    }

}
