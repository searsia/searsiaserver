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

/**
 * A single search hit. A hit can have any field. 
 * Standard fields are "title", "description", "url, "favicon", "image".
 * 
 * @author Djoerd Hiemstra and Dolf Trieschnigg
 */

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
	
    public void setResourceScore(float score) {
        map.put("rscore", score);
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
	
	/**
	 * This id of will be used the Lucene index.
	 * One url may be indexed multiple times, 
	 * once for each resource id (rid).
	 * @return unique identifier
	 */
	public String getId() {
		String result = (String) map.get("url");
		String rid = "";
		if (result == null) {
			result = (String) map.get("title");
		} else {
            rid = (String) map.get("rid");
            if (rid == null) {
                rid = "";
            }
		}
		return rid + "@" + result;
	}
	
	public float getScore() {
		Float score = (Float) map.get("score");
		if (score == null) {
			return 0.0f;
		} else {
			return score;
		}
	}
	
    public float getResourceScore() {
        Float score = (Float) map.get("rscore");
        if (score == null) {
            return 0.0f;
        } else {
            return score;
        }
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
	
    public String getRid() {
        return (String) map.get("rid");
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

	
	public JSONObject toJsonNoQueryResourceId() {
		JSONObject json = new JSONObject();
		for (Entry<String,Object> e: map.entrySet()) {
			Object value = e.getValue();
			if (value instanceof String) {
				value = noHTML((String) value);
			}
			String key = e.getKey();
			if (!key.equals("query") && !key.equals("rid")) {
                json.put(key, value);
			}
		}
		return json;
	}
	
    public String toIndexVersion() { // TODO: special treatment for urls, etc. and StringBuilder
    	String result = "";
    	for (Object s : map.values()) {
    		if (s instanceof String) {
    		    result += s + " ";
    		}
    	}
    	return result.trim();
    }
    
    public String toTitleDescriptionIndexVersion() {
        String result = (String) this.get("title");
        String desc   = (String) this.get("description");
        if (result == null) { result = ""; }
        if (desc != null) {
            result += " " + desc;
        }
        return result.trim();
    }
    
    @Override
    public int compareTo(Hit hit2) {
    	Float score1 = getResourceScore();  // order by best resources
    	Float score2 = hit2.getResourceScore();
    	int compare = score1.compareTo(score2);
    	if (compare != 0) {
            return compare;
    	} else {
    	    String rid1 = getRid(); // if two resources the same score
    	    String rid2 = hit2.getRid();
    	    if (rid1 != null && rid2 != null && rid1.compareTo(rid2) != 0) {
    	        return compare = rid1.compareTo(rid2);
    	    } else {
        		score1 = getScore();
        		score2 = hit2.getScore();
        		if (score1 != null && score2 != null) {
        		    return score1.compareTo(score2);
        		} else {
        		    return 0;
        		}
    		}
    	}
    }
    
}
