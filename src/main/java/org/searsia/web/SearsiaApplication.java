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

import javax.ws.rs.core.Response;

import org.glassfish.jersey.server.ResourceConfig;
import org.json.JSONObject;
import org.w3c.dom.Element;

import org.searsia.SearsiaOptions;
import org.searsia.index.SearchResultIndex;
import org.searsia.index.ResourceIndex;
import org.searsia.engine.DOMBuilder;

/**
 * Searsia Web API application.
 * 
 * @author Dolf Trieschnigg and Djoerd Hiemstra
 */
public class SearsiaApplication extends ResourceConfig {

	public static final String SEARSIAVERSION  = "v1.2.0";
    public static final String RSSVERSION      = "2.0";
    public static final String MIMEXML         = "application/json";
    public static final String MIMEJSON        = "application/xml";
    

    protected static Response responseError(int status, String error, boolean isJson) {
        if (isJson) {
            return jsonResponseError(status, error);
        } else {
            return xmlResponseError(status, error);
        }
    }

	protected static Response jsonResponseError(int status, String error) {
		JSONObject json = new JSONObject();
		json.put("searsia", SEARSIAVERSION);
		json.put("error", error);
		String entity = json.toString();
		return  Response
				.status(status)
				.entity(entity)
				.header("Access-Control-Allow-Origin", "*")
                .header("Mime-type", MIMEJSON)
				.build();
	}

    private static Response xmlResponseError(int status, String error) {
        DOMBuilder builder = new DOMBuilder();
        builder.newDocument();
        Element root = builder.createElement("rss");
        root.setAttribute("version", "2.0");
        Element message = builder.createElement("message");
        message.setAttribute("error", error);
        builder.setRoot(root);
        String entity = builder.toString();
        return  Response
                .status(status)
                .entity(entity)
                .header("Access-Control-Allow-Origin", "*")
                .header("Mime-type", MIMEXML)
                .build();
    }
    
	protected static Response jsonResponse(int status, JSONObject json) {
		json.put("searsia", SEARSIAVERSION);
		String entity = json.toString();
		return  Response
				.status(status)
				.entity(entity)
				.header("Access-Control-Allow-Origin", "*")
                .header("Mime-type", SearsiaApplication.MIMEJSON)
				.build();
	}

    protected static Response xmlResponse(int status, DOMBuilder builder) {
        builder.getDocumentElement().setAttribute("searsia", SEARSIAVERSION);
        String entity = builder.toString();
        return  Response
                .status(status)
                .entity(entity)
                .header("Access-Control-Allow-Origin", "*")
                .header("Mime-type", SearsiaApplication.MIMEXML)
                .build();
    }

	public SearsiaApplication(SearchResultIndex index, 
			                  ResourceIndex engines, 
			                  SearsiaOptions options) throws IOException {
		super();
		java.util.logging.Logger.getLogger("").setLevel(java.util.logging.Level.OFF);
		register(new Search(index, engines, options));
        register(new Redirect(engines.getMyself().getId()));
	}
	
}
