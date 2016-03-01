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

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.json.JSONArray;
import org.json.JSONObject;
import org.searsia.index.ResourceEngines;
import org.searsia.xpath.SearchEngine;

public class SearchResult {
	public  static final String SEARSIA_MIME_TYPE     = "application/searsia+json";
	public  static final String SEARSIA_MIME_ENCODING = SEARSIA_MIME_TYPE + "; charset=utf-8";
	private static final DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	private static final String TOKENIZER = "[^A-Za-z0-9]+";
	private List<Hit> hits;
	private SearchEngine resource;
	private Random random;
	
	public SearchResult() {
		this.hits = new ArrayList<Hit>();
		this.random = new Random();
	}
	
	public SearchResult(Hit hit) {
		this.hits = new ArrayList<Hit>();
		this.random = new Random();
		this.hits.add(hit);
	}
	
	public List<Hit> getHits() {
		return hits;
	}
	
	public void addHit(Hit hit) {
  		this.hits.add(hit);
	}
	
	public void setResource(SearchEngine resource) {
		this.resource = resource;
	}
	
	public SearchEngine getResource() {
		return this.resource;
	}
	
	// TODO: maybe a list of query-resource pairs, if result found by multiple engines for multiple queries.
	public void addQueryResourceRankDate(String query, String resourceID) {
		int rank = 1;
		for (Hit hit: this.hits) {
			hit.putIfEmpty("query", query);
			hit.putIfEmpty("rid", resourceID);  // TODO: if unknown rid, then replace!
			hit.putIfEmpty("rank", rank++);
			hit.putIfEmpty("time", df.format(new Date()));
		}
	}
	
	public void removeResourceRank() {
		for (Hit hit: this.hits) {
			hit.remove("rid");
			hit.remove("rank");
		}
	}
	
	// TODO: needs a proper implementation, refactoring, and research ;-) 
	// Scoring follows these rules:
	//   1. hits are ordered such that the first hit per rid determines the resource ranking
	//   2. if a resource has a exact query match, then these are ranked highest (given rule 1) 
	//   3. order by score (given rule 1 and rule 2)
	//   4. TODO: not more than x (=10?) hits per resource
	//   5. stop after 20 resources
	public void scoreResourceSelection(String query, ResourceEngines engines) {
		final float bias = 1.0f;
		Map<String, Float> maxScore = new HashMap<String, Float>();
		for (Hit hit: this.hits) {
			String rid = hit.getString("rid");
			if (rid != null) {
				float prior = 0.0f;
				if (engines.containsKey(rid)) {
    				prior = engines.get(rid).getPrior();
				}
    			float score = hit.getScore() * bias + prior;
				Float max = maxScore.get(rid);
				if (max == null || max < score) {
					maxScore.put(rid, score);
					max = score;
				} 
        		String hitQuery = hit.getString("query"); // TODO add getQuery()
    			if (hitQuery != null) {
    				if (hitQuery.equals(query)) {
        			    score = max;
    				} else {
    				    hit.remove("query"); // for privacy reasons removed
    				}
    			}
                hit.setScore(score);    				
			}
		}
		Map <String, Float> topEngines = engines.topValues(query, 20);
    	for (String rid: topEngines.keySet()) {
	   		if (!maxScore.containsKey(rid)) {
	   	        Hit hit = new Hit();
	            hit.put("rid", rid);
	            hit.setScore(topEngines.get(rid));
	            this.hits.add(hit);
	   		}
		}
	    Collections.sort(this.hits, Collections.reverseOrder());
	}
	
	
	public void scoreReranking(String query, String model) { // TODO use model
        SearchResult newResult = new SearchResult();
        Map<String, Float> queryTerms  = new HashMap<String, Float>();
        for (String term: query.toLowerCase().split(TOKENIZER)) {
        	queryTerms.put(term, 0.01f); // TODO df from Lucene index?
        };
		for (Hit hit: this.hits) {
	        float score = 0.0f;
			String text = hit.toIndexVersion();
			for (String term: text.toLowerCase().split(TOKENIZER)) {
	        	if (queryTerms.containsKey(term)) {
	        		score += 1.0f;
	        	}
			}
			if (score > 0.001f) {
				hit.put("score", score);
				newResult.addHit(hit);
			}
		}
		this.hits = newResult.getHits();
		Collections.sort(this.hits, Collections.reverseOrder());
	}

	
	public String randomTerm() {
        int size = this.hits.size();
        if (size > 0) {
    		int nr = random.nextInt(this.hits.size());
    		String text = this.hits.get(nr).toIndexVersion();
    		String terms[] = text.split(TOKENIZER); // TODO Lucene tokenizer?
    		nr = random.nextInt(terms.length);
    		return terms[nr];
        } else {
        	return null;
        }
	}
	
	public JSONObject toJson() {
		JSONObject r = new JSONObject();
		r.put("hits", new JSONArray());
		for (Hit hit: hits) {
			r.append("hits", hit.toJson());
		}
		return r;
	}
}
