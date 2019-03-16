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
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import org.json.JSONObject;
import org.searsia.SearchResult;
import org.searsia.SearsiaOptions;
import org.searsia.index.SearchResultIndex;
import org.w3c.dom.Element;
import org.searsia.index.ResourceIndex;
import org.searsia.engine.DOMBuilder;
import org.searsia.engine.Resource;
import org.searsia.engine.SearchException;

/**
 * Generates json response for HTTP GET search.
 * 
 * @author Dolf Trieschnigg and Djoerd Hiemstra
 */

@Path("searsia")
public class Search {

	private final static org.apache.log4j.Logger LOGGER = org.apache.log4j.Logger.getLogger(Search.class);
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
		
	@OPTIONS @Path("{filename}")
	public Response options() {
	    return Response.status(Response.Status.NO_CONTENT)
				.header("Access-Control-Allow-Origin", "*")
				.header("Access-Control-Allow-Methods", "GET")
	            .header("Access-Control-Allow-Headers", "Content-Type")
        		.build();
	}

	@GET @Path("{filename}")
	public Response query(@PathParam("filename")   String filename, 
	                      @QueryParam("q")         String searchTerms, 
                          @QueryParam("resources") String countResources, 
	                      @QueryParam("page")      String startPage) {
	    String[] parts = filename.split("\\.");
	    String resourceid = parts[0];
	    String format = "json";
	    if (parts.length > 1) {
	        format = parts[1].toLowerCase();
	    }
	    if (format.equals("osdx")) {
	        return openSearchDescriptionXml(resourceid);
	    }
        if (format.equals("json") || format.equals("xml")) {
            boolean isJson = format.equals("json");
            return queryFormat(resourceid, isJson, searchTerms, countResources, startPage);
        } else {
            return SearsiaApplication.jsonResponseError(404, "Format " + format + " not supported.");
        }
	}
	

    private Response jsonResponseOk(Resource engine, SearchResult result, String searchTerms) {
        if (result == null) {
            result = new SearchResult();    
        }
        JSONObject json = result.toJson();
        json.put("searsia", SearsiaApplication.SEARSIAVERSION);
        if (this.shared) {
            json.put("resource", engine.toJson());
        } else {
            json.put("resource", engine.toJsonEngineDontShare());
        }
        if (this.health && (searchTerms == null || searchTerms.equals(""))) {
            Resource me = this.engines.getMyself();
            if (engine.getId().equals(me.getId())) {
                JSONObject healthJson = this.engines.toJsonHealth();
                healthJson.put("requestsok", this.nrOfQueriesOk);
                healthJson.put("requestserr", this.nrOfQueriesError);
                healthJson.put("upsince", startTime);
                json.put("health", healthJson);
            } else {
                json.put("health", engine.toJsonHealth());
            }
        }
        return SearsiaApplication.jsonResponse(200, json);
    }
    
    private Response xmlResponseOk(Resource engine, SearchResult result, String searchTerms) {
        DOMBuilder builder = new DOMBuilder();
        builder.newDocument();
        Element root = builder.createElement("rss");
        builder.setRoot(root);
        root.setAttribute("version", SearsiaApplication.RSSVERSION);
        Element channel = null;
        if (this.shared) {
            channel = engine.toXml(builder);
        } else {
            channel = engine.toXmlEngineDontShare(builder);
        }
        channel = result.toXml(builder, channel);
        root.appendChild(channel);        
        if (this.health && (searchTerms == null || searchTerms.equals(""))) {
            Resource me = this.engines.getMyself();
            if (engine.getId().equals(me.getId())) {
                Element healthXml = this.engines.toXmlHealth(builder);
                healthXml.appendChild(builder.createTextElement("requestok", Long.toString(this.nrOfQueriesOk)));
                healthXml.appendChild(builder.createTextElement("requesterr", Long.toString(this.nrOfQueriesError)));
                healthXml.appendChild(builder.createTextElement("upsince", startTime));
            } else {
                root.appendChild(engine.toXmlHealth(builder));
            }
        }
        return SearsiaApplication.xmlResponse(200, builder, SearsiaApplication.MIMEXML);
    }

    private Response responseOk(Resource engine, SearchResult result, String searchTerms, boolean isJson) {
        if (isJson) {
            return jsonResponseOk(engine, result, searchTerms);
        } else {
            return xmlResponseOk(engine, result, searchTerms);
        }
    }

	
	private Response queryFormat(String resourceid, boolean isJson, String searchTerms, String countResources, String startPage) {	    
		Resource me = engines.getMyself();
		if (!resourceid.equals(me.getId())) {
		    return getRemoteResults(resourceid, isJson, searchTerms);
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
		    return getLocalResults(searchTerms, isJson, max, start);
		}
	}

    private Response getRemoteResults(String resourceid, boolean isJson, String query) {
        Resource engine = engines.get(resourceid);
        Resource mother = engines.getMother();
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
                return SearsiaApplication.responseError(404, message, isJson);
            }
        }
        if (engine.isDeleted()) {
            String message = "Gone: " + resourceid;
            LOGGER.warn(message);
            return SearsiaApplication.responseError(410, message, isJson);
        }
        SearchResult result = null;
        if (query != null && query.trim().length() > 0) {
            result = index.cacheSearch(query, engine.getId());
            if (result != null) {
                result.removeHitsResourceId(); // only trust mother, remove resource ID from each hit
                LOGGER.info("Cache " + resourceid + ": " + query);
            } else {
                try {
                    result = engine.search(query);
                    result.removeHitsResourceId();     // only trust your mother
                    assert(result.getResourceId() != null);
                    index.offer(result);  //  maybe do this AFTER the http response is sent:  https://jersey.java.net/documentation/latest/async.html (11.1.1)
                    LOGGER.info("Query " + resourceid + ": " + query);
                } catch (Exception e) {
                    String message = "Resource " + resourceid + " unavailable: " + e.getMessage();
                    LOGGER.warn(message);
                    return SearsiaApplication.responseError(503, message, isJson);
                }
            }
        } 
        return responseOk(engine, result, null, isJson);
    }

    
    private Response getLocalResults(String searchTerms, boolean isJson, int max, int start) {  
        Resource mother = engines.getMother();
        Resource me     = engines.getMyself();
        SearchResult result = null;
        if (searchTerms != null && searchTerms.trim().length() > 0) {
            try {
                result = index.search(searchTerms);
            } catch (Exception e) {
                String message = "Service unavailable: " + e.getMessage();
                LOGGER.warn(message);
                this.nrOfQueriesError += 1;
                return SearsiaApplication.responseError(503, message, isJson);
            }
            this.nrOfQueriesOk += 1;
            if (result.getHits().isEmpty() && mother != null) {  // empty? ask mother!
                try {
                    result  = mother.search(searchTerms);
                    index.offer(result);
                } catch (SearchException e) {
                    LOGGER.warn("Mother not available");
                } catch (Exception e) {
                    LOGGER.warn(e);
                }
            }
            result.scoreResourceSelection(searchTerms, engines, max, start);
            LOGGER.info("Local: " + searchTerms);
        } else { // no query: create a 'resource only' result, plus health report
            result = new SearchResult();
            result.scoreResourceSelection(null, engines, max, start);
            LOGGER.info("Local.");
        }
        return responseOk(me, result, searchTerms, isJson);
    }
    
    private Response openSearchDescriptionXml(String resourceid) {
        DOMBuilder builder = new DOMBuilder();
        builder.newDocument();
        Element root = builder.createElement("OpenSearchDescription");
        builder.setRoot(root);
        root.setAttribute("xmlns", "http://a9.com/-/spec/opensearch/1.1/");
        Resource me = this.engines.getMyself();
        Resource engine = null;
        if (resourceid.equals(me.getId())) {
            engine = me;
        } else {
            engine = this.engines.get(resourceid);
        }
        if (engine == null) {
            Element element = builder.createTextElement("error", "not found");
            root.appendChild(element);
            return SearsiaApplication.xmlResponse(404, builder, SearsiaApplication.MIMEOSDX);
        }
        String shortName    = engine.getName();
        String favicon      = engine.getFavicon();
        String userTemplate = engine.getUserTemplate();
        String suggestTemplate = engine.getSuggestTemplate();
        String apiTemplate  = engine.getAPITemplate();
        String mimeType     = engine.getMimeType();
        String postString   = engine.getPostString();
        String testQuery    = engine.getTestQuery();
        String method       = "GET";
        if (postString != null) method = "POST";
        if (shortName == null) shortName = "Searsia";
        root.appendChild(builder.createTextElement("shortName", shortName));
        root.appendChild(builder.createTextElement("Description", "Search the web with " + shortName));
        if(this.shared && apiTemplate != null) { // TODO: own api or foward API?
            root.appendChild(xmlTemplateElement(builder, mimeType, method, apiTemplate));
        }
        if (userTemplate != null) {
            root.appendChild(xmlTemplateElement(builder, "text/html", "GET", userTemplate));
        }
        if (suggestTemplate != null) {
            root.appendChild(xmlTemplateElement(builder, "application/x-suggestions+json", "GET", suggestTemplate));
        }
        if (testQuery != null) {
            Element element = builder.createElement("Query");
            element.setAttribute("role", "example");
            element.setAttribute("searchTerms", testQuery);
        }
        if (favicon != null) {
            root.appendChild(builder.createTextElement("Image", favicon));
        }
        root.appendChild(builder.createTextElement("InputEncoding", "UTF-8"));
        root.appendChild(builder.createTextElement("OutputEncoding", "UTF-8"));
        return SearsiaApplication.xmlResponse(200, builder, SearsiaApplication.MIMEOSDX);
    }


    private Element xmlTemplateElement(DOMBuilder builder, String mimeType, String method, String template) {
        Element element = builder.createElement("Url");
        element.setAttribute("type", mimeType);
        element.setAttribute("method", method);
        element.setAttribute("template", template);
        return element;
    }

}
