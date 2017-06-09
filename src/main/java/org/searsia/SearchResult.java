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
import org.searsia.index.ResourceIndex;
import org.searsia.engine.Resource;

/**
 * A Searsia Search result page, 
 * consisting of "hits", a "query" and a "resource".
 * 
 * @author Djoerd Hiemstra and Dolf Trieschnigg
 */

public class SearchResult {
	public  static final String SEARSIA_MIME_TYPE     = "application/searsia+json";
	public  static final String SEARSIA_MIME_ENCODING = SEARSIA_MIME_TYPE + "; charset=utf-8";
	private static final DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
	private static final String TOKENIZER = "[^A-Za-z0-9]+";
	private List<Hit> hits;
	private Random random;
	private Resource resource;
	private String debugOut;
	private String query;
    private String resourceId;
	private String version;
	
	public SearchResult() {
		this(null);
	}
	
	public SearchResult(Hit hit) {
		this.hits = new ArrayList<Hit>();
		this.random = new Random();
		this.resource = null;
		this.query = null;
		this.version = null;
		this.debugOut = null;
		if (hit != null) {
			this.hits.add(hit);
		}
	}
	
	public List<Hit> getHits() {
		return hits;
	}
	
	public void addHit(Hit hit) {
  		this.hits.add(hit);
	}
	
	public void setResource(Resource resource) {
		this.resource = resource;
	}
	
	public Resource getResource() {
		return this.resource;
	}
	
    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    public String getResourceId() {
        return this.resourceId;
    }

	public String getVersion() {
	    return this.version;
	}

    public void setVersion(String version) {
        this.version = version;
    }

	public void setDebugOut(String debugOut) {
		this.debugOut = debugOut;
	}

	public String getDebugOut() {
		return this.debugOut;
	}

	public void setQuery(String query) {
		this.query = query;
	}

	public String getQuery() {
		return this.query;
	}

	// TODO: maybe a list of query-resource pairs, if result found by multiple engines for multiple queries.
	public void addResourceDate(String resourceID) {
		for (Hit hit: this.hits) {
			hit.putIfEmpty("rid", resourceID);  // TODO: if unknown rid, then replace!
			hit.putIfEmpty("foundBefore", df.format(new Date()));
		}
	}
	
	public void removeResource() {
		for (Hit hit: this.hits) {
			hit.remove("rid");
			hit.remove("query"); // for legacy reasons, we added the query to the result before
		}
	}

	/**
	 * New resource ranker, adds rscore.
	 * @param query
	 * @param engines
	 */
	public void scoreResourceSelection(String query, ResourceIndex engines) {
		final float boost = 1.0f;
		Map<String, Float> maxScore   = new HashMap<String, Float>();
		Map<String, Float> topEngines = engines.topValues(query, 10);
		for (Hit hit: this.hits) {
			String rid = hit.getString("rid");
			if (rid != null) {
				float prior = 0.0f;
				if (engines.containsKey(rid)) {
    				prior = engines.get(rid).getPrior();
				}
                Float top = topEngines.get(rid);
    			if (top != null) { 
    			    if (top > prior) {
    			        prior = top;
    			    }
    			    topEngines.remove(rid);
				}
                Float score = prior + hit.getScore() * boost;
				Float max = maxScore.get(rid);
				if (max == null || max < score) {
                    max = score;
					maxScore.put(rid, max);
				} 
                hit.setScore(score);
                hit.setResourceScore(max);
			} else {
			    hit.setResourceScore(hit.getScore() * boost);
			}
		}
    	for (String rid: topEngines.keySet()) {
   	        Hit hit = new Hit();
            hit.put("rid", rid);
            hit.setScore(topEngines.get(rid));
            hit.setResourceScore(topEngines.get(rid));
            this.hits.add(hit);
		}
	    Collections.sort(this.hits, Collections.reverseOrder());
	}

	/**
     * Scoring follows these rules:
     * (TODO: needs a proper implementation, refactoring, and research ;-) ) 
     *   1. hits are ordered such that the first hit per rid determines the resource ranking;
     *   2. if a resource has a exact query match, then these are ranked highest (given rule 1);
     *   3. order by score (given rule 1 and rule 2);
     *   4. TODO: not more than x (=10?) hits per resource;
     *   5. stop after 20 resources.
     * @param query
     * @param engines
     */
    public void scoreResourceSelectionOld(String query, ResourceIndex engines) {
        final float boost = 1.0f;
        Map<String, Float> maxScore = new HashMap<String, Float>();
        Map <String, Float> topEngines = engines.topValues(query, 20);
        for (Hit hit: this.hits) {
            String rid = hit.getString("rid");
            if (rid != null) {
                float prior = 0.0f;
                if (engines.containsKey(rid)) {
                    prior = engines.get(rid).getPrior();
                }
                float score = hit.getScore() * boost + prior;
                Float top = topEngines.get(rid);
                if (top != null) { 
                    if (top > score) {
                        score = top;
                    }
                    topEngines.remove(rid);
                }
                Float max = maxScore.get(rid);
                if (max == null || max < score) {
                    maxScore.put(rid, score);
                    max = score;
                } 
                hit.setScore(score);
                //hit.put("rscore", max);
            }
        }
        for (String rid: topEngines.keySet()) {
            Hit hit = new Hit();
            hit.put("rid", rid);
            hit.setScore(topEngines.get(rid));
            //hit.put("rscore", topEngines.get(rid));
            this.hits.add(hit);
        }
        Collections.sort(this.hits, Collections.reverseOrder());
    }


    public void scoreReranking(String query, String model) {
        if ("random".equals(model)) {
            scoreRerankingRandom();
        } else {
            scoreRerankingRest(query);
        }
    }


    private void scoreRerankingRandom() {
        Hit hit;
        int i, j,
            size = this.hits.size();
        for (i = 0; i < size; i += 1) {
            j = random.nextInt(size);
            hit = this.hits.get(i);
            this.hits.set(i, this.hits.get(j));
            this.hits.set(j, hit);
        }
    }


	private void scoreRerankingRest(String query) {
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
	        		score += 1.0f; // TODO: single query term multiple times?
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

	
	public String randomTerm(String notThisOne) { // TODO: keep track of more previous random queries?
        int size = this.hits.size();
        if (size > 0) {
    		int nr = random.nextInt(this.hits.size());
    		String text = this.hits.get(nr).toTitleDescriptionIndexVersion().toLowerCase();
    		String terms[] = text.split(TOKENIZER); // TODO Lucene tokenizer?
    		nr = random.nextInt(terms.length);
    		String thisOne = terms[nr];
    		int i = nr;
    		while (thisOne.length() < 1 || notThisOne.equals(thisOne)) {
    		    if (i + 1 >= terms.length) { i = 0; }
    		    else { i += 1; }
    		    if (i == nr) { return null; }
                thisOne = terms[i];
    		}
    		return thisOne;
        } else {
        	return null;
        }
	}
	
    public JSONObject toJson() {
        return toJson(false);
    }

	public JSONObject toJson(boolean censorQueryResourceId) {
		JSONObject r = new JSONObject();
		r.put("hits", new JSONArray());
		for (Hit hit: hits) {
		    if (censorQueryResourceId) {
                r.append("hits", hit.toJsonNoQueryResourceId());
		    } else {
     			r.append("hits", hit.toJson());
		    }
		}
		return r;
	}


}
