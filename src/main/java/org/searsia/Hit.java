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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.json.JSONObject;

public class Hit implements Comparable<Hit> {

	private Map<String,Object> map;
	
	public Hit() {
		this.map = new HashMap<>();
	}
	
	public Hit(String title, String description, String url, String favicon) {
		this.map = new HashMap<>();
		map.put("title", title);
		map.put("url",   url);
		map.put("description", description);
		map.put("favicon", favicon);
	}

	public Hit(JSONObject json) {
		this.map = new HashMap<>();
		Iterator<?> keys = json.keys();
		while (keys.hasNext()) {
			String key = (String) keys.next();
			map.put(key, json.get(key));
		}
	}

	public Hit(String jsonString) {
		this(new JSONObject(jsonString));
	}
	

	public void put(String field, Object value) {
		map.put(field, value);
	}
	
	public void remove(String field) {
		map.remove(field);
	}

	public void putIfEmpty(String field, Object value) {
		if (!map.containsKey(field)) {
    		map.put(field, value);
		}
	}

	public void setScore(float score) {
		map.put("score", score);
	}
	
	public void setRank(int rank) {
		map.put("rank", rank);
	}
	
	public void setTitle(String title) {
		map.put("title", title);
	}
	
	public void setDescription(String description) {
		map.put("description", description);
	}
	
	public void setUrl(String url) {
		map.put("url", url);
	}
	
	public String getId() {
		String result = (String) map.get("url");
		if (result == null) {
			result = (String) map.get("title");
		}
		return result;
	}
	
	public float getScore() {
		Float score = (Float) map.get("score");
		if (score == null) {
			return 0.0f;
		} else {
			return score;
		}
	}
	
	public Integer getRank() {
		return (Integer) map.get("rank");
	}
	
	public Object get(String field) {
		return map.get(field);
	}

	public String getString(String field) {
		return (String) map.get(field);
	}

	public String getDescription() {
		return (String) map.get("description");
	}
	
	public String getTitle() {
		return (String) map.get("title");
	}
	
	@Override
	public String toString() {
		return map.entrySet().toString();
	}

	private String noHTML(String value) {
		value = value.replaceAll("<[^>]+>", ""); // no HTML
		return value.replaceAll("[<>]", "");
	}

	public JSONObject toJson() {
		JSONObject json = new JSONObject();
		for (Entry<String,Object> e: map.entrySet()) {
			Object value = e.getValue();
			if (value instanceof String) {
				value = noHTML((String) value);
			}
			json.put(e.getKey(), value);
		}
		return json;
	}
	
    public String toIndexVersion() { // TODO: special treatment for urls, etc.
    	String result = "";
    	for (Object s : map.values()) {
    		if (s instanceof String) {
    		    result += s + " ";
    		}
    	}
    	return result.trim();
    }
    
    @Override
    public int compareTo(Hit hit2) {
    	Float score1 = getScore();
    	Float score2 = hit2.getScore();
    	if (score1.compareTo(score2) == 0) {
    		Integer rank1 = getRank();
    		Integer rank2 = hit2.getRank();
    		if (rank1 != null && rank2 != null) {
        		return rank2.compareTo(rank1); // yes reversed!
    		} else {
    			return 0;
    		}
    	} else {
    		return score1.compareTo(score2);
    	}
    }
    
}
