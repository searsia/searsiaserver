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

package org.searsia.web;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import javax.ws.rs.GET;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import org.apache.log4j.Logger;
import org.json.JSONObject;
import org.searsia.SearchResult;
import org.searsia.SearsiaOptions;
import org.searsia.index.SearchResultIndex;
import org.searsia.index.ResourceIndex;
import org.searsia.engine.Resource;
import org.searsia.engine.SearchException;

/**
 * Generates json response for HTTP GET search.
 * 
 * @author Dolf Trieschnigg and Djoerd Hiemstra
 */

@Path("searsia")
public class Search {

    private final static Logger LOGGER = Logger.getLogger(Search.class);
    private final static DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT);
    private final static String startTime = dateFormat.format(new Date());
	
    private ResourceIndex engines;
    private SearchResultIndex index;
    private boolean health;
    private boolean shared;
    private long nrOfQueriesOk = 0;
    private long nrOfQueriesError = 0;


	public Search(SearchResultIndex index, ResourceIndex engines, SearsiaOptions options) throws IOException {
		this.engines = engines;
    	this.index   = index;
    	this.health  = !options.isNoHealthReport();
    	this.shared  = !options.isNotShared();
	}
		
	@OPTIONS @Path("{resourceid}")
	public Response options() {
	    return Response.status(Response.Status.NO_CONTENT)
				.header("Access-Control-Allow-Origin", "*")
				.header("Access-Control-Allow-Methods", "GET")
	            .header("Access-Control-Allow-Headers", "Content-Type")
        		.build();
	}

	// TODO: gives 406 not acceptable with "Accept: application/json"
	
	@GET @Path("{resourceid}")
	@Produces(SearchResult.SEARSIA_MIME_ENCODING)
	public Response query(@PathParam("resourceid")  String resourceid, 
	                      @QueryParam("q")          String searchTerms,
	                      @QueryParam("type")       String resultType,
                          @QueryParam("resources")  String countResources, 
	                      @QueryParam("page")       String startPage) {
        LOGGER.trace("Web call " + resourceid + ": " + searchTerms + ", " + resultType);
        resourceid = resourceid.replaceAll("\\.json$", "");
		Resource me = engines.getMyself();
		if (!resourceid.equals(me.getId())) {
		    return getRemoteResults(resourceid, searchTerms); // TODO: also pass resultType to remote Searsia engines?
		} else {
		    Integer max = 10, start = 0;
		    if (countResources != null) {
		       try {
		           max = Integer.parseInt(countResources);
		       } catch (NumberFormatException e) {
		           max = 10;
		       }
		       if (max > 200) { max = 200; } // FedWeb14 has about 150
		       if (max < 1) { max = 1; }
		    }
            if (startPage != null) {
                try {
                    start = Integer.parseInt(startPage);
                    start = (start - me.getIndexOffset()) * max; // openSearch standard default starts at 1
                } catch (NumberFormatException e) {
                    start = 0;
                }
                if (start < 0) { start = 0; }
            }
		    return getLocalResults(searchTerms, resultType, max, start);
		}
	}

    private Response getRemoteResults(String resourceid, String query) {
        Resource engine = engines.get(resourceid);
        Resource mother = engines.getMother();
        JSONObject json = null;
        if (engine == null || engine.getLastUpdatedSecondsAgo() > 9600) {  // unknown or really old? ask your mother
            if (mother != null) {     // TODO: option for 9600 and similar value (7200) in Main
                try {
                    Resource newEngine  = mother.searchResource(resourceid);
                    engine = newEngine;
                    engines.put(engine);
                } catch (SearchException e) {
                    if (engine != null) {
                        LOGGER.warn("Not found at mother: " + resourceid);
                    }
                }
            }
            if (engine == null) {
                String message = "Not found: " + resourceid;
                LOGGER.warn(message);
                return SearsiaApplication.responseError(404, message);
            }
        }
        if (engine.isDeleted()) {
            String message = "Gone: " + resourceid;
            LOGGER.warn(message);
            return SearsiaApplication.responseError(410, message);
        }
        
        if (query != null && query.trim().length() > 0) {
            try {
            	String redirect = engine.getDirectAccess();
                if (redirect != null && redirect.equals("yes")) {
                	LOGGER.info("Redirect: " + resourceid);
                    return SearsiaApplication.responseRedirect(engine.redirectSearch(query));
                }       	
                SearchResult result = index.cacheSearch(query, engine.getId());
                if (result != null) {
                    boolean censorQueryResourceId = true;
                    json = result.toJson(censorQueryResourceId);
                    LOGGER.info("Cache " + resourceid + ": " + query);
                } else {
                    result = engine.search(query);
                    result.removeResource();     // only trust your mother
                    json = result.toJson();                         // first json for response, so
                    result.addResourceDate(engine.getId()); // response will not have resource id + date
                    index.offer(result);  //  maybe do this AFTER the http response is sent:  https://jersey.java.net/documentation/latest/async.html (11.1.1)
                    LOGGER.info("Query " + resourceid + ": " + query);
                }
            } catch (Exception e) {
                String message = "Resource " + resourceid + " unavailable: " + e.getMessage();
                LOGGER.warn(message);
                return SearsiaApplication.responseError(503, message);
            }
        } else {
            json = new JSONObject();
            if (this.health) {
                json.put("health", engine.toJsonHealth());
            }
            LOGGER.info("Resource " + resourceid + ".");
        }
        if (this.shared) {
            json.put("resource", engine.toJson());
        } else {
            json.put("resource", engine.toJsonEngineDontShare());
        }
        return SearsiaApplication.responseOk(json);
    }

    private Response getLocalResults(String query, String type, int max, int start) {  
        JSONObject json = null, healthJson = null;
        Resource mother = engines.getMother();
        Resource me     = engines.getMyself();
        SearchResult result = null;
        LOGGER.trace("Local query: " + query + ", " + type);
        if (query != null && query.trim().length() > 0) {
            try {
                result = index.search(query); // TODO: pass on type.
            } catch (Exception e) {
                String message = "Service unavailable: " + e.getMessage();
                LOGGER.warn(message);
                this.nrOfQueriesError += 1;
                return SearsiaApplication.responseError(503, message);
            }
            this.nrOfQueriesOk += 1;
            if (result.getHits().isEmpty() && mother != null) {  // empty? ask mother!
                try {
                    result  = mother.search(query);
                    index.offer(result);  // really trust mother
                } catch (SearchException e) {
                    LOGGER.warn("Mother not available");
                } catch (Exception e) {
                    LOGGER.warn(e);
                }
            }
            result.scoreResourceSelection(query, type, engines, max, start);
            LOGGER.info("Local: " + query);
        } else { // no query: create a 'resource only' result, plus health report
            result = new SearchResult();
            result.scoreResourceSelection(null, type, engines, max, start);
            if (this.health) {
                healthJson = engines.toJsonHealth();
                healthJson.put("requestsok", this.nrOfQueriesOk);
                healthJson.put("requestserr", this.nrOfQueriesError);
                healthJson.put("upsince", startTime);
            }
            LOGGER.info("Local.");
        }
        json = result.toJson();
        json.put("resource", me.toJson());
        if (healthJson != null) {
            json.put("health", healthJson);
        }
        return SearsiaApplication.responseOk(json);
    }

}
