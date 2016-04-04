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

import java.util.List;

import javax.ws.rs.DELETE;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;

import org.json.JSONObject;
import org.searsia.Hit;
import org.searsia.SearchResult;
import org.searsia.index.ResourceEngines;
import org.searsia.engine.SearchEngine;

/**
 * Enables on-line updates, only if --open set in the options.
 * 
 * @author Dolf Trieschnigg and Djoerd Hiemstra
 */
@Path("update")
public class Update {

	private ResourceEngines engines;
	private Boolean wideOpen;

	
	public Update(ResourceEngines engines, Boolean wideOpen) {
		this.engines = engines;
		this.wideOpen = wideOpen;
	}
		

	private JSONObject getJSONResource(String postString, HttpHeaders headers) {
		JSONObject jsonResource = null;
		String contentType = headers.getHeaderString("Content-Type").toLowerCase();
		if (contentType.equals(SearchResult.SEARSIA_MIME_ENCODING)) {
			JSONObject jsonInput = new JSONObject(postString);
			jsonResource = jsonInput.getJSONObject("resource");
		} else {
			throw new RuntimeException("Content-type not implemented");
		}
		return jsonResource;
	}


	@OPTIONS
	@Path("{id}")
	public Response options() {
	    return Response.status(Response.Status.NO_CONTENT)
				.header("Access-Control-Allow-Origin", "*")
				.header("Access-Control-Allow-Methods", "DELETE, PUT")
				.header("Access-Control-Allow-Headers", "Content-Type")
        		.build();
	}

	/**
	 * Updates the engines database with a new resource. Test with:
	 * curl -X PUT -H 'Content-Type: application/searsia+json; charset=UTF-8' http://localhost:16842/searsia/update/2 -d '{"resource":{"id":"2", "apitemplate":"https://search.utwente.nl/searsia/suggestions.php?q={q}", "testquery":"osiris"}}'
	 * 
	 * @param id engine identifier
	 * @param headers http headers
	 * @param putString data
	 * @return search results for the test query if the update is successful
	 */
    @PUT  // 
	@Path("{id}")
	@Produces(SearchResult.SEARSIA_MIME_ENCODING) 
	public Response put(@PathParam("id") String id,  @Context HttpHeaders headers, String putString) {
		if (!this.wideOpen) {
			return SearsiaApplication.responseError(401, "Unauthorized");
		}
		SearchEngine engine = null;
		try {
			JSONObject jsonResource = getJSONResource(putString, headers);
			if (!id.equals(jsonResource.get("id"))) {
				return SearsiaApplication.responseError(400, "Conflicting id's");
			}
    		engine = new SearchEngine(jsonResource);
		} catch (Exception e) {
			return SearsiaApplication.responseError(400, e.getMessage());
		}
		SearchResult result = null;
    	updateEngine(engine);
		try {			
			result = engine.search(engine.getTestQuery(), true); // debug = true
		} catch (Exception e) {
			return SearsiaApplication.responseError(503, "Resource unavailable: " + e.getMessage());
		}
		
		JSONObject jsonOutput = result.toJson();
		jsonOutput.put("resource", engine.toJson());
		jsonOutput.put("debug", result.getXmlOut());
		List<Hit> hits = result.getHits();
		if (result == null || hits.size() == 0) {
			jsonOutput.put("error", "No results for test query: '" + engine.getTestQuery() + "'" );
			return SearsiaApplication.jsonResponse(405, jsonOutput);
			//return SearsiaApplication.responseError(405, "No results for test query: '" + engine.getTestQuery() + "'" );
		} else {
			for (Hit hit: hits) {
			    if (hit.getTitle() == null) {
					jsonOutput.put("error", "Search result without title for query: '" + engine.getTestQuery() + "'");
					return SearsiaApplication.jsonResponse(405, jsonOutput);
			    }
			    break; // check only first
			}
		}
		try {
			engines.put(engine);
		} catch (Exception e) {
			return SearsiaApplication.responseError(400, e.getMessage());
		}
		return SearsiaApplication.responseOk(jsonOutput);
	}
    
    /**
     * If Searsia engine, get several values. Will change the value of 'engine'
     * @param engine
     */
    private void updateEngine(SearchEngine engine) {
		if (engine.getMimeType().equals(SearchResult.SEARSIA_MIME_TYPE)) {
            SearchResult result = null;
        	SearchEngine resource = null;
	    	try {
		    	result = engine.search();
			    resource = result.getResource();
    			if (resource != null) {
    				engine.setUrlAPITemplate(resource.getAPIUserTemplate());
	    			if (engine.getName() == null) { engine.setName(resource.getName()); }
		    		if (engine.getBanner() == null) { engine.setBanner(resource.getBanner()); }
			    	if (engine.getFavicon() == null) { engine.setFavicon(resource.getFavicon()); }
				    if (engine.getRerank() == null) { engine.setRerank(resource.getRerank()); }
    				if (engine.getTestQuery().equals(SearchEngine.defaultTestQuery)) { engine.setTestQuery(resource.getTestQuery()); } // awkward if the user typed 'searsia'
	    		}
		    } catch (Exception e) {
			    // nothing
		    }
		}
    }
	
    /**
     * Deletes the engine with resource id: id. Test with:
     * curl -X DELETE http://localhost:16842/searsia/update/2
     *  
     * @param id engine identifier
     * @return only searsia version if successful
     */
	@DELETE
	@Path("{id}")
	@Produces(SearchResult.SEARSIA_MIME_ENCODING)
	public Response delete(@PathParam("id") String id) {
		if (!this.wideOpen) {
			return SearsiaApplication.responseError(401, "Unauthorized");
		}
		JSONObject jsonOutput = new JSONObject();
		try {
			engines.delete(id);
		} catch (Exception e) {
			return SearsiaApplication.responseError(400, e.getMessage());
		}
		return SearsiaApplication.responseOk(jsonOutput);
	}

}
