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
	private String error;
	
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
		this.error = null;
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

    public String getError() {
        return this.error;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public void setError(String error) {
        this.error = error;
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

	/* ******************************************************************* 
	 *  Code below reranks search results for resource selection
	 * *******************************************************************/
	
	
	/**
	 * New resource ranker, adds rscore.
	 * @param query
	 * @param engines
	 */
	public void scoreResourceSelection(String query, ResourceIndex engines, int max, int start) {
	    SearchResult newResult = new SearchResult();
		final float boost = 0.05f;
		final int maxSize = max + start;
		Map<String, Float> maxScores   = new HashMap<String, Float>();
        Map<String, Integer> resourceReturned = new HashMap<String, Integer>();
		Map<String, Float> topEngines = engines.topValuesNotDeleted(query, maxSize);
		for (Hit hit: this.hits) {
			String rid = hit.getString("rid");
			if (rid != null) {
                Resource engine = engines.get(rid);
                float prior = 0.0f;
                if (engine != null) {
                    if (engine.isDeleted()) { continue; } // cached result from a deleted resource	    
    				prior = engines.get(rid).getPrior();
				}
                Float top = topEngines.get(rid);
    			if (top != null) { 
    			    if (top > prior) {
    			        prior = top;
    			    }
    			    topEngines.remove(rid);
				}
                Integer returned = resourceReturned.get(rid);
                if (returned == null) {
                    returned = 0;
                }
                resourceReturned.put(rid, returned + 1);
                Float score = prior + hit.getScore() * boost;
				Float maxScore = maxScores.get(rid);
				if (maxScore == null || maxScore < score) {
                    maxScore = score;
					maxScores.put(rid, maxScore);
					returned = 0; // this is the best one, so we will add it below no matter what
				}
                hit.setScore(score);
                hit.setResourceScore(maxScore);
                if (returned < 4) { // at most 4 results per resource
                    newResult.addHit(hit);
                }
			} else {
			    hit.setResourceScore(hit.getScore() * boost);
	            newResult.addHit(hit);
			}
		}
        for (String rid: topEngines.keySet()) {
            Hit hit = new Hit();
            hit.put("rid", rid);
            hit.setScore(topEngines.get(rid));
            hit.setResourceScore(topEngines.get(rid));
            newResult.addHit(hit);
        }           
		this.hits = newResult.hits;
	    Collections.sort(this.hits, Collections.reverseOrder());
	    selectBestResources(max, start); // TODO: efficiently combine this with sort?
	}
	
    /**
     * Selects the 'max' best resources, starting at resource 'start'
     * Hits MUST be sorted already on rid (rscore).
     * @param max
     * @param start
     */
	public void selectBestResources(int max, int start) {
	    String rid, previousRid = null;
        int rFound  = 0;
	    int rNeeded = start + max;
	    int first = 0, i = 0;
        for (Hit hit: this.hits) {
            rid = hit.getRid();
            if (rid != null && !rid.equals(previousRid)) {
                previousRid = rid;
                if (start > 0 && rFound == start) { first = i; } 
                rFound += 1;
                if (rFound > rNeeded) { break; } 
            }
            i += 1;
        }		
        if (rFound < start) {
            this.hits.clear();
        } else {
    	    this.hits = this.hits.subList(first, i);
        }
	}
	
	
    public void scoreReranking(String query, String model) {
        if ("random".equals(model)) {
            scoreRerankingRandom();
        } else if ("bestrandom".equals(model)) {
            scoreRerankingBestRandom(query);
        } else {
            scoreRerankingRest(query);
        }
    }

	private void scoreRerankingBestRandom(String query) {
	    scoreRerankingRandom();
	    scoreRerankingGeneral(query, 10);
	}

	private void scoreRerankingRest(String query) {
	    scoreRerankingGeneral(query, 0);
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

    private float scoreText(String text, Map<String, Float> queryTerms, float weight) {
        float score = 0.0f;
        if (text != null) {
            for (String term: queryTerms.keySet()) {
                queryTerms.put(term, weight);
            }
            for (String term: text.toLowerCase().split(TOKENIZER)) {
                if (queryTerms.containsKey(term)) {
                    score += queryTerms.get(term);
                    queryTerms.put(term, 0.0f);
                }
            }
        }
        return score;
    }

	private void scoreRerankingGeneral(String query, int count) {
        SearchResult newResult = new SearchResult();
        Map<String, Float> queryTerms  = new HashMap<String, Float>();
        for (String term: query.toLowerCase().split(TOKENIZER)) {
        	queryTerms.put(term, 0.1f); // TODO idf from Lucene index
        };
		for (Hit hit: this.hits) {
	        float score = scoreText(hit.toIndexVersion(), queryTerms, 0.1f);
			score += scoreText(hit.getTitle(), queryTerms, 0.01f);
			if (count > 0) {
			    score += 0.01f;
			    count -= 1;
			}
			if (score > 0.001f) {
				hit.put("score", score);
				newResult.addHit(hit);
			}
		}
		this.hits = newResult.getHits();
		Collections.sort(this.hits, Collections.reverseOrder());
	}

	
    /* ******************************************************************* 
     *  End of reranking code
     * *******************************************************************/

	
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
