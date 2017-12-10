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

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import org.searsia.engine.Resource;
import org.searsia.index.ResourceIndex;

/**
 * Implements the OpenSearch description document from http://opensearch.org
 * 
 * @author hiemstra
 *
 */
@Path("opensearch")
public class OpenSearch {

	private ResourceIndex engines;
	private boolean dontshare;

	public OpenSearch(ResourceIndex engines, boolean dontshare) throws IOException {
		this.engines   = engines;
		this.dontshare = dontshare;
	}
	
	@GET @Path("{resourceid}")
	@Produces("application/opensearchdescription+xml; charset=utf-8")
	public Response get(@PathParam("resourceid") String resourceid) {
        resourceid = resourceid.replaceAll("\\.xml$", "");
        Resource engine = null;
        if (resourceid.equals(engines.getMyself().getId())) {
            engine = engines.getMyself();
        } else {
            engine = engines.get(resourceid);
        }
        if (engine != null) {
    		String xmlString = engineXML(engine);
	    	return  Response.ok(xmlString).build();
        } else {
            return SearsiaApplication.responseError(404, "Not found: " + resourceid);
        }
	}

	
	private String xmlEncode(String text) {
		text = text.replaceAll("<", "&lt;");
		text = text.replaceAll(">", "&gt;");
		return text.replaceAll("&", "&amp;");
	}

	private String templateEncode(String url) {
		url = url.replaceAll("\\{q", "{searchTerms"); // backwards compatible with Searsia v0.x
        return xmlEncode(url);		
	}

	private String engineXML(Resource engine) {
        String response = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n";
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
        response += "<OpenSearchDescription xmlns=\"http://a9.com/-/spec/opensearch/1.1/\">\n";
        response += " <ShortName>" + xmlEncode(shortName) + "</ShortName>\n";
        response += " <Description>Search the web with " + xmlEncode(shortName) + "</Description>\n";
        if(!dontshare && apiTemplate != null) { // TODO: own api or foward API?
        	response += " <Url type=\"" + mimeType + "\" method=\"" + method + "\" template=\"" + templateEncode(apiTemplate) + "\"/>\n";
        }
        if (userTemplate != null) response += " <Url type=\"text/html\" method=\"GET\" template=\"" + templateEncode(userTemplate) + "\"/>\n";
        if (suggestTemplate != null) response += " <Url type=\"application/x-suggestions+json\" method=\"GET\" template=\"" + templateEncode(suggestTemplate) + "\"/>\n";
        if (testQuery != null) response += " <Query role=\"example\" searchTerms=\"" + xmlEncode(testQuery) + "\"/>\n";
        if (favicon != null) response += " <Image>" + xmlEncode(favicon) + "</Image>\n";
        response += " <InputEncoding>UTF-8</InputEncoding>\n";
        response += " <OutputEncoding>UTF-8</OutputEncoding>\n";
        response += "</OpenSearchDescription>\n";
        return response;
	}
	
}
