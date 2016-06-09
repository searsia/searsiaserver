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

package org.searsia.web;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.ws.rs.GET;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import org.apache.log4j.Logger;
import org.json.JSONObject;

import org.searsia.SearchResult;
import org.searsia.index.SearchResultIndex;
import org.searsia.index.ResourceIndex;
import org.searsia.engine.Resource;
import org.searsia.engine.SearchException;

/**
 * Generates json response for HTTP GET search.
 * 
 * @author Dolf Trieschnigg and Djoerd Hiemstra
 */
@Path("search")
public class Search {

	private final static Logger LOGGER = Logger.getLogger(Search.class);
	
	private ResourceIndex engines;
    private SearchResultIndex index;

	public Search(SearchResultIndex index, ResourceIndex engines) throws IOException {
		this.engines  = engines;
    	this.index = index;
	}
	
	private static final DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.S");
	
	private void logQuery(String resourceid, String query) {
		JSONObject r = new JSONObject();
		r.put("time", df.format(new Date()));
		if (resourceid != null) r.put("resourceid", resourceid);
		r.put("query", query);
		LOGGER.info(r.toString());		
	}

	private void logWarning(String message) {
		JSONObject r = new JSONObject();
		r.put("time", df.format(new Date()));
		r.put("warning", message);
		LOGGER.warn(r.toString());		
	}

	@OPTIONS
	public Response options() {
	    return Response.status(Response.Status.NO_CONTENT)
				.header("Access-Control-Allow-Origin", "*")
				.header("Access-Control-Allow-Methods", "GET")
        		.build();
	}
	
	@GET
	@Produces(SearchResult.SEARSIA_MIME_ENCODING)
	public Response query(@QueryParam("r") String resourceid, @QueryParam("q") String query) {
		// TODO: also log the outcome of the query
		logQuery(resourceid, query);
		
		Resource me, engine, mother;
		SearchResult result;
		JSONObject json;
		me = engines.getMyself();
		mother = engines.getMother();
		if (resourceid != null && resourceid.trim().length() > 0 && !resourceid.equals(me.getId())) {
			engine = engines.get(resourceid);
			if (engine == null) {  // unknown? ask your mother
				if (mother != null) {
				    try {
    				    engine  = mother.searchResource(resourceid);
				    } catch (SearchException e) {
				    	String message = "Resource not found: @" + resourceid;
				    	logWarning(message);
					    return SearsiaApplication.responseError(404, message);
				    }
				}
				if (engine == null) {
					String message = "Unknown resource identifier: @" + resourceid;
			    	logWarning(message);
    				return SearsiaApplication.responseError(404, message);
				} 
    		    engines.put(engine);
 			}
			if (query != null && query.trim().length() > 0) {
				try {
					result = engine.search(query);
    				result.removeResourceRank();     // only trust your mother
					json = result.toJson();                         // first json for response, so
					result.addQueryResourceRankDate(query, engine.getId()); // response will not have query + resource
					index.offer(result);  //  maybe do this AFTER the http response is sent:  https://jersey.java.net/documentation/latest/async.html (11.1.1)
					json.put("resource", engine.toJson());
					return SearsiaApplication.responseOk(json);
				} catch (Exception e) {
					String message = "Resource @" + resourceid + " unavailable: " + e.getMessage();
					logWarning(message);
					return SearsiaApplication.responseError(503, message);
				} 
			} else {
				json = new JSONObject().put("resource", engine.toJson());
				return SearsiaApplication.responseOk(json);
			}
		} else {
			if (query != null && query.trim().length() > 0) {
		    	try {
			        result = index.search(query);
			    } catch (IOException e) {
			    	String message = "Service unavailable: " + e.getMessage();
			    	logWarning(message);
				    return SearsiaApplication.responseError(503, message);				
			    }
		    	if (result.getHits().isEmpty() && mother != null) {  // empty? ask mother!
				    try {
    				    result  = mother.search(query);
				    } catch (SearchException e) {
				    	String message = "Mother not available";
				    	logWarning(message);
					}		    		
		    	} else {  // own results? Do resource ranking.
			        result.scoreResourceSelection(query, engines);
		    	}
			} else {  // no query? Return empty results
				result = new SearchResult();
		        result.scoreResourceSelection(query, engines);
			}
		    json = result.toJson();
		    json.put("resource", engines.getMyself().toJson());
			return SearsiaApplication.responseOk(json);
		}
	}
	
}
