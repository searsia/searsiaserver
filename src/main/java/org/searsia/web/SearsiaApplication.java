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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.ws.rs.core.Response;

import org.glassfish.jersey.server.ResourceConfig;
import org.json.JSONObject;
import org.searsia.SearchResult;
import org.searsia.index.HitsSearcher;
import org.searsia.index.ResourceEngines;

/**
 * Searsia Web API application.
 * 
 * @author Dolf Trieschnigg and Djoerd Hiemstra
 */
public class SearsiaApplication extends ResourceConfig {
	
	public static final String VERSION = "v0.3.2";
	
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

	public SearsiaApplication(ArrayBlockingQueue<SearchResult> queue, HitsSearcher searcher, ResourceEngines engines, Boolean openWide) throws IOException {
		super();
    	Logger.getLogger("org.glassfish.grizzly").setLevel(Level.WARNING);
		register(new Search(queue, searcher, engines));
		register(new Update(engines, openWide));
		register(new OpenSearch(engines));
	}
	
}
