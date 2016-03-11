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
    private Boolean openWide;
    private Boolean exit;
    private Boolean quiet;
	private int cacheSize;
    private int pollInterval;
    private int logLevel;
    private String myURI;
    private String motherTemplate;
    private String indexPath; 
    private String myName;

    /**
     * Takes command line options and sensible defaults
     * 
     */
    public SearsiaOptions(String[] args) throws IllegalArgumentException {
    	Options options = new Options();
        options.addOption("c", "cache",    true,  "Set cache size (integer: number of result pages).");
        options.addOption("e", "exit",     false, "Exit immediately after startup.");
        options.addOption("h", "help",     false, "Show help.");
        options.addOption("i", "interval", true,  "Set poll interval (integer: in seconds).");
        options.addOption("l", "log",      true,  "Set log level (0=off, 1=error, 2=warn=default, 3=info, 4=debug).");
        options.addOption("m", "mother",   true,  "Set api template of the mother. ('none' for standalone)");
        options.addOption("n", "name",     true,  "Set my id (name).");
        options.addOption("o", "open",     false, "Open the system for on-line updates (be careful!)");
        options.addOption("p", "path",     true,  "Set index path.");
        options.addOption("q", "quiet",    false, "No output on console.");
        options.addOption("u", "url",      true,  "Set url of my web service endpoint.");
        setDefaults();
        parse(options, args);
    }

    
    private void setDefaults() {
        openWide       = false;
        exit           = false;
        quiet          = false;
        cacheSize      = 500;
        pollInterval   = 120;
        logLevel       = 2;
        myURI          = "http://localhost:16842/searsia/";
        motherTemplate = "https://search.utwente.nl/searsia/search?q={q?}&r={r?}";
        indexPath      = friendlyIndexPath();
        myName         = null;
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
            help(options);
            throw new IllegalArgumentException(e);
        }
        
        if (cmd.hasOption("c")) {
            cacheSize = new Integer(cmd.getOptionValue("c"));
            if (cacheSize < 30) {
            	cacheSize = 30;
            }
        }
        if (cmd.hasOption("e")) {
            exit =  true;
        }
        if (cmd.hasOption("h") || cmd.getArgs().length > 0) {
          	help(options);
            throw new IllegalArgumentException("Help!"); // misusing exceptions :-(
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
            help(options);
            throw new IllegalArgumentException(e);        	
        }
        if (cmd.hasOption("m")) {
            motherTemplate = cmd.getOptionValue("m");
            if (motherTemplate.equals("none")) motherTemplate = null;
        }
        if (cmd.hasOption("n")) {
            myName    = cmd.getOptionValue("n");
        }
        if (cmd.hasOption("o")) {
            openWide =  true;
        }
        if (cmd.hasOption("p")) {
            indexPath = cmd.getOptionValue("p");
        }
        if (cmd.hasOption("q")) {
            quiet = true;
        }
        if (cmd.hasOption("u")) {
            myURI  = cmd.getOptionValue("u");
        }
    }
    
    
    private void help(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("SearsiaServer", options);
     }
    
    public int getCacheSize() {
    	return cacheSize;
    }

    public Boolean isExit() {
    	return exit;
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
    
    public String getMyName() {
    	return myName;
    }
    
    public Boolean openedWide() {
    	return openWide;
    }

    public Boolean isQuiet() {
    	return quiet;
    }
    
    @Override
    public String toString() {
    	String result = "SearsiaOptions:";
    	result += "\n  Log Level     = " + getLoggerLevel();
    	result += "\n  Base Url      = " + getMyURI();
    	result += "\n  Mother        = " + getMotherTemplate();
    	result += "\n  Index Name    = " + getMyName();
    	result += "\n  Index Path    = " + getIndexPath();
    	result += "\n  Poll Interval = " + getPollInterval();
    	result += "\n  Allows update = " + openedWide();
    	result += "\n  Cache Size    = " + getCacheSize();
    	return result;
    }

}
