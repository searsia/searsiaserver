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
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import org.searsia.index.ResourceEngines;

/**
 * Implements the OpenSearch description document from http://opensearch.org
 * 
 * @author hiemstra
 *
 */
@Path("opensearch.xml")
public class OpenSearch {

	private ResourceEngines engines;

	public OpenSearch(ResourceEngines engines) throws IOException {
		this.engines  = engines;
	}
	
	@GET
	@Produces("application/opensearchdescription+xml; charset=utf-8")
	public Response get() {
		String response = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n";
		String shortName    = engines.getMyself().getName();
		String favicon      = engines.getMyself().getFavicon();
		String userTemplate = engines.getMyself().getUrlUserTemplate();
		String apiTemplate  = engines.getMyself().getAPIUserTemplate();
		String testQuery    = engines.getMyself().getTestQuery();
		if (shortName == null) shortName = "Searsia";
		response += "<OpenSearchDescription xmlns=\"http://a9.com/-/spec/opensearch/1.1/\" xmlns:searsia=\"http://searsia.org/1.0/\">\n";
		response += " <ShortName>" + xmlEncode(shortName) + "</ShortName>\n";
		response += " <Description>Search the web with " + xmlEncode(shortName) + "</Description>\n";
		response += " <Url type=\"application/searsia+json\" method=\"GET\" template=\"" + templateEncode(apiTemplate) + "\"/>\n";
		if (userTemplate != null) response += " <Url type=\"text/html\" method=\"GET\" template=\"" + templateEncode(userTemplate) + "\"/>\n";
		if (testQuery != null) response += " <Query role=\"example\" searchTerms=\"" + xmlEncode(testQuery) + "\"/>\n";
		if (favicon != null) response += " <Image>" + xmlEncode(favicon) + "</Image>\n";
		response += " <InputEncoding>UTF-8</InputEncoding>\n";
		response += " <OutputEncoding>UTF-8</OutputEncoding>\n";
		response += "</OpenSearchDescription>\n";
		return  Response.ok(response).build();
	}


	private String xmlEncode(String text) {
		text = text.replaceAll("<", "&lt;");
		text = text.replaceAll(">", "&gt;");
		return text.replaceAll("&", "&amp;");
	}

	private String templateEncode(String url) {
		url = url.replaceAll("\\{q", "{searchTerms");
		url = url.replaceAll("\\{r", "{searsia:resourceId");
        return xmlEncode(url);		
	}

}
