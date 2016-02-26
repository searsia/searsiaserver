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

package org.searsia.xpath;

import org.json.JSONObject;

import org.searsia.SearchResult;


public interface SearchEngine {
	public void changeId(String id);
	public void setPrior(float prior);
    public void setFavicon(String favicon);
    public void setBanner(String banner);
	public void setUrlAPITemplate(String urlTemplate);
	public void setUrlUserTemplate(String urlTemplate);
    public void setName(String name);
    public void setRerank(String rerank);
    public void setTestQuery(String query);
	public void addPrivateParameter(String key, String value);
	public String getId();
	public String getMD5();
	public String getName();
	public String getRerank();
    public String getUrlUserTemplate();
	public String getAPIUserTemplate();
    public String getMimeType();
    public String getFavicon();
    public String getBanner();
    public String getTestQuery();
    public float getPrior();
    public JSONObject toJson();
	public JSONObject getJsonPrivateParameters();
	public SearchResult search() throws SearchException;
	public SearchResult search(String query) throws SearchException;
	public SearchResult randomSearch() throws SearchException;
	public SearchEngine searchResource(String resourceid) throws SearchException;
}
