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
import org.searsia.SearsiaOptions;
import org.searsia.index.SearchResultIndex;
import org.searsia.index.ResourceIndex;

/**
 * Searsia Web API application.
 * 
 * @author Dolf Trieschnigg and Djoerd Hiemstra
 */
public class SearsiaApplication extends ResourceConfig {

	public static final String VERSION = "v1.1.0";

	protected static Response responseOk(JSONObject json) {
		json.put("searsia", VERSION);
		return  Response
				.ok(json.toString())
				.header("Access-Control-Allow-Origin", "*")
				.build();
	}

	protected static Response responseError(int status, String error) {
		JSONObject json = new JSONObject();
		json.put("searsia", VERSION);
		json.put("error", error);
		String entity = json.toString();
		return  Response
				.status(status)
				.entity(entity)
				.header("Access-Control-Allow-Origin", "*")
				.build();
	}
	
	protected static Response jsonResponse(int status, JSONObject json) {
		json.put("searsia", VERSION);
		String entity = json.toString();
		return  Response
				.status(status)
				.entity(entity)
				.header("Access-Control-Allow-Origin", "*")
				.build();
	}

	public SearsiaApplication(SearchResultIndex index, 
			                  ResourceIndex engines, 
			                  SearsiaOptions options) throws IOException {
		super();
		java.util.logging.Logger.getLogger("").setLevel(java.util.logging.Level.WARNING);
		register(new Search(index, engines, options));
		register(new OpenSearch(engines, options.isNotShared()));
        register(new Redirect(engines.getMyself().getId()));
	}
	
}
