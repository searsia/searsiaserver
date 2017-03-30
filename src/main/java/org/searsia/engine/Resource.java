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

package org.searsia.engine;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.XML;
import org.jsoup.Jsoup;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.searsia.Hit;
import org.searsia.SearchResult;

public class Resource implements Comparable<Resource> {

    public final static String defaultTestQuery = "searsia";

    // For rate limiting: Default = 1000 queries per day
    private final static int defaultRATE = 1000;    // unit: queries
    private final static int defaultPER = 86400000; // unit: miliseconds (86400000 miliseconds is one day)
    
	// TODO: private static final Pattern queryPattern = Pattern.compile("\\{q\\??\\}");

    // data to be set by setters
	private String id = null;
	private String name = null;
	private String urlAPITemplate = null;
	private String urlUserTemplate = null;
	private String urlSuggestTemplate = null;
    private String mimeType = null;
    private String postString = null;
    private String postQueryEncode = null;
    private String favicon = null;
    private String banner = null;
	private String itemXpath = null;
	private String testQuery = defaultTestQuery;
	private List<TextExtractor> extractors = new ArrayList<>();
	private Map<String, String> headers     = new LinkedHashMap<>();
	private Map<String, String> privateParameters = new LinkedHashMap<>();
	private Float prior = null;
	private String rerank = null;
	private int rate = defaultRATE;

	// internal data not to be shared
	private String nextQuery = null;
	private double allowance = defaultRATE / 2;
	private Long lastUsedCheck = new Date().getTime(); // Unix time
    private Long lastUpdatedCheck = new Date().getTime(); // Unix time


	public Resource(String urlAPITemplate, String id) {
		this.urlAPITemplate = urlAPITemplate;
		this.id = id;
		this.name = null;
		this.mimeType = SearchResult.SEARSIA_MIME_TYPE;
		this.testQuery = defaultTestQuery;
	}
	
	public Resource(JSONObject jo) throws XPathExpressionException, JSONException {	
		this.mimeType = SearchResult.SEARSIA_MIME_TYPE;
		this.testQuery = defaultTestQuery;
		if (jo.has("id"))              this.id              = jo.getString("id");
		if (jo.has("apitemplate"))     this.urlAPITemplate  = jo.getString("apitemplate");
		if (jo.has("mimetype"))        this.mimeType        = jo.getString("mimetype");
		if (jo.has("post"))            this.postString      = jo.getString("post");
		if (jo.has("postencode"))      this.postQueryEncode = jo.getString("postencode");
		if (jo.has("name"))            this.name            = jo.getString("name");
		if (jo.has("testquery"))       this.testQuery       = jo.getString("testquery");
		if (jo.has("urltemplate"))     this.urlUserTemplate = jo.getString("urltemplate");
		if (jo.has("suggesttemplate")) this.urlSuggestTemplate = jo.getString("suggesttemplate");
		if (jo.has("favicon"))         this.favicon         = jo.getString("favicon");
		if (jo.has("rerank"))          this.rerank          = jo.getString("rerank");
		if (jo.has("banner"))          this.banner          = jo.getString("banner");
		if (jo.has("itempath"))        this.itemXpath       = jo.getString("itempath");
		if (jo.has("prior"))           this.prior           = new Float(jo.getDouble("prior"));
		if (jo.has("maxqueriesperday")) this.rate           = jo.getInt("maxqueriesperday");
		if (jo.has("extractors")) {
			JSONObject json = (JSONObject) jo.get("extractors");
			Iterator<?> keys = json.keys();
			while (keys.hasNext()) {
				String key = (String) keys.next();
				addExtractor(new TextExtractor((String) key, (String) json.get(key)));
			}
		}
		if (jo.has("headers")) {
			JSONObject json = (JSONObject) jo.get("headers");
			Iterator<?> keys = json.keys();
			while (keys.hasNext()) {
				String key = (String) keys.next();
				addHeader((String) key, (String) json.get(key));
			}
		}
		if (jo.has("privateparameters")) {
			JSONObject json = (JSONObject) jo.get("privateparameters");
			Iterator<?> keys = json.keys();
			while (keys.hasNext()) {
				String key = (String) keys.next();
				addPrivateParameter((String) key, (String) json.get(key));
			}
		}
		if (this.urlAPITemplate != null && this.urlAPITemplate.startsWith("file")) {
			throw new IllegalArgumentException("Illegal 'file' API Template");
		}
		if (this.id == null) {
			throw new IllegalArgumentException("Missing Identifier");
		}
	}
	
	public void setUrlAPITemplate(String urlTemplate) {
		this.urlAPITemplate = urlTemplate;
	}
   
	public void setUrlUserTemplate(String urlTemplate) {
		this.urlUserTemplate = urlTemplate;
	}
   
	public void setUrlSuggestTemplate(String suggestTemplate) {
		this.urlSuggestTemplate = suggestTemplate;
	}
   
    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }
	
    public void setPostString(String postString) {
        this.postString = postString;
    }

    public void setPostQueryEncode(String postQueryEncode) {
        this.postQueryEncode = postQueryEncode;
    }

    public void setFavicon(String favicon) {
        this.favicon = favicon;
    }

    public void setBanner(String banner) {
        this.banner = banner;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setTestQuery(String query) {
        this.testQuery = query;
    }

	public void setItemXpath(String itemXpath) {
		this.itemXpath = itemXpath;
	}
	
	public void addExtractor(TextExtractor ... e) {
		for (TextExtractor ee: e) {
			this.extractors.add(ee);
		}
	}

	public void addHeader(String key, String value) {
		this.headers.put(key, value);
	}

	public void addPrivateParameter(String key, String value) {
		this.privateParameters.put(key, value);
	}

	public void setPrior(float prior) {
		this.prior = prior;
	}
	
	public void setRate(int maxQueriesPerDay) {
		this.rate = maxQueriesPerDay;
	}

	public void setRerank(String rerank) {
		this.rerank  = rerank;
	}

	public void setLastUpdatedToNow() {
	    this.lastUpdatedCheck = new Date().getTime();
	}


	public SearchResult randomSearch() throws SearchException {
		if (nextQuery == null) {
			nextQuery = this.testQuery;
		}
		String thisQuery = nextQuery;
		nextQuery = null; // so, nextQuery will be null in case of a searchexception
		SearchResult result = search(thisQuery);
		nextQuery = result.randomTerm();
		return result;
	}


	public SearchResult search(String query) throws SearchException {
        return search(query, null);
	}


	public SearchResult search(String query, String debug) throws SearchException {
		try {
			String url = fillTemplate(this.urlAPITemplate, URLEncoder.encode(query, "UTF-8"));
			String postString = "";
			String postQuery;
			if (this.postString != null && !this.postString.equals("")) {
				if (this.postQueryEncode != null) {
					if (this.postQueryEncode.equals("application/x-www-form-urlencoded")) {
						postQuery = URLEncoder.encode(query, "UTF-8");
					} else if (this.postQueryEncode.equals("application/json")) {
						postQuery = query.replaceAll("\"", "\\\\\\\\\"");  // really? yes, really.
					} else {
						postQuery = query;
					}
				} else {
					postQuery = URLEncoder.encode(query, "UTF-8");
				}
				postString = fillTemplate(this.postString, postQuery);
			}
			String page = getCompletePage(url, postString, this.headers);
			SearchResult result;
            if (this.mimeType != null && this.mimeType.equals(SearchResult.SEARSIA_MIME_TYPE)) {
            	result = searsiaSearch(page, debug);
            } else {
            	result = xpathSearch(url, page, debug);
            }
            if (this.rerank != null && query != null) {
                result.scoreReranking(query, this.rerank);
            }
			result.setQuery(query);
			return result;
		} catch (Exception e) {  // catch all, also runtime exceptions
			throw createPrivateSearchException(e);
		} 
	}

	public SearchResult searchWithoutQuery() throws SearchException {
		if (!this.mimeType.equals(SearchResult.SEARSIA_MIME_TYPE)) {
			throw new SearchException("Engine is not a searsia engine: " + this.id);
		}
		try {
			String url = fillTemplate(this.urlAPITemplate, "");
			String page = getCompletePage(url, this.postString, this.headers);
			return searsiaSearch(page, null);
		} catch (Exception e) {  // catch all, also runtime exceptions
			throw createPrivateSearchException(e);
		} 
	}

	
	public Resource searchResource(String resourceid) throws SearchException {
		if (!this.mimeType.equals(SearchResult.SEARSIA_MIME_TYPE)) {
			throw new SearchException("Resource is not a searsia engine: " + this.getId());
		}
		Resource engine = null;
   		String url = this.urlAPITemplate;
   		String rid = this.getId();
   		int lastIndex = url.lastIndexOf(rid); // replace last occurrence of resourceId
   		if (lastIndex < 0) { 
   		    throw new SearchException("No resources available"); 
   		}
        try {
            String newRid = URLEncoder.encode(resourceid, "UTF-8");
            url = url.substring(0, lastIndex) + url.substring(lastIndex).replaceFirst(rid, newRid);
            url = url.replaceAll("\\{[0-9A-Za-z\\-_]+\\?\\}|\\{q\\}", ""); // remove optional parameters and query 
       		String jsonPage = getCompletePage(url, this.postString, this.headers);
    		JSONObject json = new JSONObject(jsonPage);
    		if (json.has("resource")) {
        		engine = new Resource(json.getJSONObject("resource"));
    		}
            return engine;
		} catch (Exception e) {
			throw createPrivateSearchException(e);
		}
	}


	private SearchResult searsiaSearch(String jsonPage, String debug) throws XPathExpressionException, JSONException {
		SearchResult result = new SearchResult();
		if (debug != null && debug.equals("response")) {
			result.setDebugOut(jsonPage);
		}
		JSONObject json = new JSONObject(jsonPage);
		JSONArray hits  = new JSONArray();
		try {
            hits  = json.getJSONArray("hits");
		} catch (JSONException e) { }
		for (int i = 0; i < hits.length(); i += 1) {
			result.addHit(new Hit((JSONObject) hits.get(i)));
		}
		if (json.has("resource")) {
 		    Resource engine = new Resource(json.getJSONObject("resource"));
   		    result.setResource(engine);
		}
		if (json.has("searsia")) {
		    result.setVersion(json.getString("searsia"));
		}
		return result;
	}
	

	private SearchResult xpathSearch(String url, String page, String debug)
			throws IOException, XPathExpressionException {
		Document document = null;
		if (this.mimeType == null) {
		    throw new IOException("No MIME Type provided.");
		}
		if (this.mimeType.equals("application/json")) {
		   document = parseDocumentJSON(page);
		} else if (this.mimeType.equals("application/x-javascript")) {
		    document = parseDocumentJavascript(page);
		} else if (this.mimeType.equals("application/xml")) {
		    document = parseDocumentXML(page);
		} else if (this.mimeType.equals("text/html")){
		    document = parseDocumentHTML(page, url);
		} else {
		    throw new IOException("MIME Type not supported: " + this.mimeType);
		}
		if (document == null) {
			throw new IOException("Error parsing document. Wrong mimetype?");
		}
		SearchResult result = new SearchResult();
		if (debug != null) {
			if (debug.equals("xml")) {
    			result.setDebugOut(DOMBuilder.DOM2String(document));
			} else if (debug.equals("response")) {
				result.setDebugOut(page);
			}
		}
		XPathFactory xFactory = XPathFactory.newInstance();
		XPath xpath = xFactory.newXPath();
		NodeList xmlNodeList = (NodeList) xpath.evaluate(itemXpath, document, XPathConstants.NODESET);
		for (int i = 0; i < xmlNodeList.getLength() && i < 30; i++) {
			Node item = xmlNodeList.item(i);
			result.addHit(extractHit(item));
		}
		return result;
	}
	
    private Hit extractHit(Node item) throws XPathExpressionException {
    	Hit hit = new Hit();
    	for(TextExtractor extractor: this.extractors) {
			extractor.extract(item, hit);
    	}
		return hit;
	}

	private Document parseDocumentHTML(String htmlString, String urlString) throws IOException {
        org.jsoup.nodes.Document jsoupDoc = Jsoup.parse(htmlString, urlString);
        return DOMBuilder.jsoup2DOM(jsoupDoc);
    }

	/**
	 * From a Javascript callback result, get out all JSON objects and put them in a single object
	 * @param scriptString 
	 * @return Document
	 * @throws IOException
	 */
	private Document parseDocumentJavascript(String scriptString) throws IOException {
		int nrOfCurly = 0;
		int first = -1;
		JSONArray array = new JSONArray();
		for (int i = 0; i < scriptString.length(); i++){
		    char c = scriptString.charAt(i);        
		    if (c == '{') {
		    	if (nrOfCurly == 0) { first = i; }
		    	nrOfCurly += 1;
		    } else if (c == '}') {
		    	nrOfCurly -= 1;
		    	if (nrOfCurly == 0) {
		    		String subString = scriptString.substring(first, i + 1);
		        	subString = subString.replaceAll("\"([0-9][^\"]*)\":", "\"t$1\":"); // tags starting with a number are not well-formed XML
                    try {
                    	array.put(new JSONObject(subString));
                    } catch (JSONException e) { }
		    	}
		    }
		}
		JSONObject object = new JSONObject();
		object.put("list", array);
		String xml = "<root>" + XML.toString(object) + "</root>";
        return DOMBuilder.string2DOM(xml);
	}
	
    private Document parseDocumentJSON(String jsonString) throws IOException {
        jsonString = jsonString.replaceAll("\"[^\"]*[/<>' =][^\"]*\":[ \n\r]*\"[^\"]*\",?", ""); // completely remove data with keys that have one of: /<>' =
    	jsonString = jsonString.replaceAll("\"([0-9][^\"]*)\"[ \n\r]*:", "\"t$1\":"); // tags starting with a number are not well-formed XML
        jsonString = jsonString.replaceAll("\"content\":", "\"searsia_org_json_content\":"); // work around. org.json.XML is broken: https://github.com/stleary/JSON-java/issues/286
    	if (jsonString.startsWith("[")) {  // turn lists into objects
        	jsonString = "{\"list\":" + jsonString + "}";
        }
        String xml = "<root>" + XML.toString(new JSONObject(jsonString)) + "</root>";
        xml = xml.replaceAll("searsia_org_json_content>", "content>");  // use a constant for 'searsia_org_json_content'? see 5 lines above
        return DOMBuilder.string2DOM(xml);
    }
	
    private Document parseDocumentXML(String xmlString) throws IOException {
        return DOMBuilder.string2DOM(xmlString);
    }

    private String fillTemplate(String template, String query) throws IOException {
  		String url = template;
   		for (String param: getPrivateParameterKeys()) {
   			url = url.replaceAll("\\{" + param + "\\??\\}", getPrivateParameter(param));
   		}
   		url = url.replaceAll("\\{q\\??\\}", query);
        url = url.replaceAll("\\{[0-9A-Za-z\\-_]+\\?\\}", ""); // remove optional parameters
		if (url.matches(".*\\{[0-9A-Za-z\\-_]+\\}.*")) {
		    String param = url.substring(url.indexOf("{"), url.indexOf("}") + 1);
			throw new IOException("Missing url parameter " + param);
		}        
        return url;
	}

    private SearchException createPrivateSearchException(Exception e) {
  		String message = e.toString();
   		for (String param: getPrivateParameterKeys()) {
   			message = message.replaceAll(getPrivateParameter(param), "{" + param + "}");
   		}
        return new SearchException(message);
	}
    
    /**
     * Rate limiting based on:
     * http://stackoverflow.com/questions/667508/whats-a-good-rate-limiting-algorithm
     */
    private boolean rateLimitReached() {
        Long now = new Date().getTime();
        Long timePassed = now - this.lastUsedCheck;
        this.lastUsedCheck = now;
        this.allowance += (((double) timePassed / defaultPER)) * this.rate;
        if (this.allowance > this.rate) { 
        	this.allowance = this.rate;
        } 
        if (this.allowance > 1) {
        	this.allowance -= 1;
        	return false;
        } else {
      	    return true;
        }
    }


    private URLConnection setConnectionProperties(URL url, Map<String, String> headers) throws IOException {
        URLConnection connection = url.openConnection();
        connection.setRequestProperty("User-Agent", "Searsia/1.0");
        connection.setRequestProperty("Accept", this.mimeType); //TODO: "*/*"
        connection.setRequestProperty("Accept-Language", "en-US,en;q=0.5"); // TODO: from browser?
        for (Map.Entry<String, String> entry : headers.entrySet()) {
        	String value = entry.getValue();
            if (value.contains("{")) {
        		for (String param: getPrivateParameterKeys()) {
    	    		value = value.replace("{" + param + "}", getPrivateParameter(param));
    		    }
        		if (value.contains("{")) {
    	            String param = value.substring(value.indexOf("{"), value.indexOf("}") + 1);
	    			throw new IOException("Missing header parameter " + param);
        		}
			}
        	connection.setRequestProperty(entry.getKey(), value);
    	}
        connection.setReadTimeout(9000);
        connection.setConnectTimeout(9000);
        return connection;
    }
    
    private InputStream httpConnect(URLConnection connection, String postString) throws IOException {
    	HttpURLConnection http = (HttpURLConnection) connection;
        http.setInstanceFollowRedirects(true);
        if (postString != null && !postString.equals("")) {
            http.setRequestMethod("POST");
            http.setRequestProperty("Content-Length", "" + Integer.toString(postString.getBytes().length));
            http.setDoOutput(true);
    		DataOutputStream wr = new DataOutputStream(http.getOutputStream());
    		wr.writeBytes(postString);
    		wr.flush();
    		wr.close();
        } else {
            http.setRequestMethod("GET");
            http.connect();
        }
        //int responseCode = http.getResponseCode();
        return http.getInputStream();
    }
    
    private InputStream fileConnect(URLConnection connection) throws IOException {
    	String fileName = connection.getURL().getFile();
    	return new FileInputStream(new File(fileName));
    }

    private String getCompletePage(String urlString, String postString, Map<String, String> headers) throws IOException {
        if (rateLimitReached()) {
        	throw new IOException("Rate limited");
        }
        URL url = new URL(urlString);
        URLConnection connection = setConnectionProperties(url, headers);
        InputStream stream;
        if (url.getProtocol().equals("file")) {
        	stream = fileConnect(connection);
        } else {
        	stream = httpConnect(connection, postString);
        }
        BufferedReader in = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
        StringBuilder page = new StringBuilder();
        if (in != null) {
            String inputLine; 
            while ((inputLine = in.readLine()) != null) {
                page.append(inputLine);
                page.append("\n");
            }
            in.close();
        }
        return page.toString();
    }


	public String getId() {
		return this.id;
	}
	
	public String getName() {
		return this.name;
	}
	
	public String getUserTemplate() {
		return this.urlUserTemplate;
	}	

	public String getAPITemplate() {
		return this.urlAPITemplate;
	}	

	public String getSuggestTemplate() {
		return this.urlSuggestTemplate;
	}	

	public String getMimeType() {
		return this.mimeType;
	}

	public String getFavicon() {
		return this.favicon;
	}

	public String getBanner() {
		return this.banner;
	}

	public String getTestQuery() {
		return this.testQuery;
	}

	public String getRerank() {
		return this.rerank;
	}

	public String getPostString() {
		return this.postString;
	}

	public String getPostQueryEncode() {
		return this.postQueryEncode;
	}

	public String getItemXpath() {
		return this.itemXpath;
	}
	
	public String getPrivateParameter(String param) {
	    return this.privateParameters.get(param);
	}

    public Set<String> getPrivateParameterKeys() {
	    return this.privateParameters.keySet();
	}

	
	public List<TextExtractor> getExtractors() {
		return this.extractors;
	}

	public Map<String, String> getHeaders() {
		return this.headers;
	}
	
	public JSONObject getJsonPrivateParameters() {
		if (privateParameters != null && privateParameters.size() > 0) {
			JSONObject json = new JSONObject();
			for (String parameter: privateParameters.keySet()) {
				json.put(parameter, privateParameters.get(parameter));
			}
			return json;
		} else {
			return null;
		}
	}

	public int getRate() {
		return this.rate;
	}
	
	public float getPrior() {
		if (this.prior == null) {
			return 0.0f; 
		} else {
			return this.prior;
		}
	}

	
	private Long secondsAgo(Long last) {
	    if (last == null) { 
	        return null;
	    } else {
    	    Long now = new Date().getTime();
            Long ago = 1 + (now - last) / 1000;
            if (ago < 0 || ago > 8640000l) { // 100 days...
                ago = 8640000l;
            }
            return  ago;
	    }
	}


	public Long getLastUpdatedSecondsAgo() {
        return secondsAgo(this.lastUpdatedCheck);
	}


    public Long getLastUsedSecondsAgo() {
        return secondsAgo(this.lastUsedCheck);
    }

    
    public float score(String query) {
		float score = 0.0f;
		Map<String, Boolean> nameTerm = new HashMap<String, Boolean>();
		String name = getName();
		if (name != null && query != null) {
			nameTerm.put(getId().toLowerCase(), true);
    		for (String term: name.toLowerCase().split("[^0-9a-z]+")) {
	    		nameTerm.put(term, true);
		    }
    		for (String term: query.toLowerCase().split("[^0-9a-z]+")) {
	    		if (nameTerm.containsKey(term)) {
	    		    score += 2.0f; // some arbitrary number	
	    		}
		    }
         }
		return score;
	}
	
	
	public Resource deepcopy() {
		try {
			return new Resource(this.toJson());
		} catch (XPathExpressionException | JSONException e) {
			throw new RuntimeException(e);
		}
	}
	

	public JSONObject toJson() {
		JSONObject engine = new JSONObject();
		if (id != null)                  engine.put("id", id);
		if (name != null)                engine.put("name", name);
		if (urlUserTemplate != null)     engine.put("urltemplate", urlUserTemplate);
		if (favicon != null)             engine.put("favicon", favicon);
		if (banner != null)              engine.put("banner", banner);
		if (urlAPITemplate != null)      engine.put("apitemplate", urlAPITemplate);
		if (urlSuggestTemplate != null)  engine.put("suggesttemplate", urlSuggestTemplate);
		if (mimeType != null)            engine.put("mimetype", mimeType);
		if (rerank != null)              engine.put("rerank", rerank);
		if (postString != null)          engine.put("post", postString);
		if (postQueryEncode != null)     engine.put("postencode", postQueryEncode);
		if (testQuery != null)           engine.put("testquery", testQuery);
		if (prior != null)               engine.put("prior", prior);
		if (rate != defaultRATE)         engine.put("maxqueriesperday", rate);
		if (itemXpath != null)           engine.put("itempath", itemXpath);
		if (extractors != null && extractors.size() > 0) {
			JSONObject json = new JSONObject();
			for (TextExtractor e: extractors) {
				json.put(e.getField(), e.getPath());
			}
			engine.put("extractors", json); 
		}
		if (headers != null && headers.size() > 0) {
			JSONObject json = new JSONObject();
			for (String header: headers.keySet()) {
				json.put(header, headers.get(header));
			}
			engine.put("headers", json); 
		}
		Long ago = this.getLastUpdatedSecondsAgo();
		if (ago != null) engine.put("updatedsecondsago", ago);
        ago = this.getLastUsedSecondsAgo();
        if (ago != null) engine.put("usedsecondsago", this.getLastUsedSecondsAgo());
		return engine;
	}

    @Override
    public boolean equals(Object o) {  // TODO: AARGH, can't this be done simpler?
    	if (o == null) return false;
    	Resource e = (Resource) o;
    	if (!stringEquals(this.getId(), e.getId())) return false;
    	if (!stringEquals(this.getName(), e.getName())) return false;
    	if (!stringEquals(this.getMimeType(), e.getMimeType())) return false;
        if (!stringEquals(this.getRerank(), e.getRerank())) return false;
    	if (!stringEquals(this.getFavicon(), e.getFavicon())) return false;
    	if (!stringEquals(this.getBanner(), e.getBanner())) return false;
    	if (!stringEquals(this.getPostString(), e.getPostString())) return false;
    	if (!stringEquals(this.getPostQueryEncode(), e.getPostQueryEncode())) return false;
    	if (!stringEquals(this.getTestQuery(), e.getTestQuery())) return false;
    	if (!stringEquals(this.getItemXpath(), e.getItemXpath())) return false;
    	if (!stringEquals(this.getAPITemplate(), e.getAPITemplate())) return false;
    	if (!stringEquals(this.getUserTemplate(), e.getUserTemplate())) return false;
    	if (!stringEquals(this.getSuggestTemplate(), e.getSuggestTemplate())) return false;
    	if (this.getRate() != e.getRate()) return false;
        if (Math.abs(this.getPrior() - e.getPrior()) > 0.0001f) return false;
    	if (!listEquals(this.getExtractors(), e.getExtractors())) return false; 
    	if (!mapEquals(this.getHeaders(), e.getHeaders())) return false; 
    	return true;
    }
    
    @Override
    public int compareTo(Resource e2) {
    	Float score1 = getPrior();
    	Float score2 = e2.getPrior();
   		return score1.compareTo(score2);
    }

    
    private boolean listEquals(List<?> a, List<?> b) {
        if (a == null && b == null)
     	   return true;
        if (a == null || b == null)
     	   return false;
    	if (a.size() != b.size()) 
    		return false;
    	return a.containsAll(b);
    }
    
    private boolean mapEquals(Map<String, String> a, Map<String, String> b) {
       if (a == null && b == null)
    	   return true;
       if (a == null || b == null)
    	   return false;
       if (a.size() != b.size())
    	   return false;
       for (String key: a.keySet())
          if (!a.get(key).equals(b.get(key)))
             return false;
       return true;
    }

    private boolean stringEquals(String a, String b) {
        if (a == null && b == null)
     	   return true;
        if (a == null || b == null)
     	   return false;
    	return a.equals(b);
    }

}
