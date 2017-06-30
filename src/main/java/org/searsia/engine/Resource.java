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

package org.searsia.engine;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.searsia.Hit;
import org.searsia.SearchResult;

/**
 * A Searsia Resource: A wrapper for external search engines. It can read results from
 * engines that produce results in: HTML, XML, JSON, and SEARSIA JSON.
 * 
 * @author Djoerd Hiemstra and Dolf Trieschnigg
 */
public class Resource implements Comparable<Resource> {

    public final static String defaultTestQuery = "searsia";
    public final static String goneErrorMessage = "Searsia Gone";

    // For rate limiting: Default = 1000 queries per day
    private final static int defaultRATE = 1000;    // unit: queries
    private final static int defaultPER = 86400000; // unit: miliseconds (86400000 miliseconds is one day)
    private final static DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT);
    
	// TODO: private static final Pattern queryPattern = Pattern.compile("\\{q\\??\\}");

    // data to be set by JSON
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
	private Map<String, String> headers    = new LinkedHashMap<>();
	private Map<String, String> privateParameters = new LinkedHashMap<>();
	private Float prior = null;
	private String rerank = null;
	private int rate = defaultRATE;
	private boolean deleted = false;
	
	// internal data shared for health report
	private String   nextQuery = null;
    private String   lastMessage = null;
	private double   allowance = defaultRATE / 2;
	private long      lastUsed = new Date().getTime(); // Unix time
    private long    lastUsedOk = lastUsed; 
    private long lastUsedError = lastUsed; 
    private long   lastUpdated = lastUsed;
    private long       upsince = lastUsed;
    private int      nrOfError = 0; 
    private int         nrOfOk = 0; 

	public Resource(String urlAPITemplate) {
		this.urlAPITemplate = urlAPITemplate;
		this.id = null;
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
        if (jo.has("deleted"))         this.deleted         = jo.getBoolean("deleted");
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


	/* 
	 * Setters no longer used: Everything now via JSON Objects
	 * 

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
	
	public void setPrior(float prior) {
		this.prior = prior;
	}
	
	public void setRate(int maxQueriesPerDay) {
		this.rate = maxQueriesPerDay;
	}

	public void setRerank(String rerank) {
		this.rerank  = rerank;
	}
*/
    private void addExtractor(TextExtractor ... e) {
        for (TextExtractor ee: e) {
            this.extractors.add(ee);
        }
    }

    private void addHeader(String key, String value) {
        this.headers.put(key, value);
    }

    private void addPrivateParameter(String key, String value) {
        this.privateParameters.put(key, value);
    }

	public void setLastUpdatedToNow() {
	    this.lastUpdated = new Date().getTime();
	}

    public void setLastUpdatedToDateString(String date) {
        try {
            this.lastUpdated = dateFormat.parse(date).getTime();
        } catch (ParseException e) { }
    }

    public void setUpSinceToNow() {
        this.upsince = new Date().getTime();
    }

    public void setUpSinceDateString(String date) {
        try {
            this.upsince = dateFormat.parse(date).getTime();
        } catch (ParseException e) { }
    }


    public Resource updateFromAPI() throws SearchException {
        SearchResult result = searchWithoutQuery();
        if (result == null) { throw new SearchException("No results."); }
        Resource resource = result.getResource(); 
        if (resource == null) { throw new SearchException("Object \"resource\" not found."); }
        updateWith(resource);
        return this;
    }
    

	public SearchResult randomSearch() throws SearchException {
		if (this.nextQuery == null) {
			this.nextQuery = this.testQuery;
		}
		String thisQuery = this.nextQuery;
		this.nextQuery = null; // so, nextQuery will be null in case of a searchexception
		SearchResult result = search(thisQuery, null);
		if (this.testQuery.equals(thisQuery) && result.getHits().isEmpty()) {
	        this.nrOfError += 1;
	        this.lastUsedError = new Date().getTime();
	        this.lastMessage = "No results for test query: " + thisQuery;
		    throw new SearchException(this.lastMessage);
		} else {
		    this.nextQuery = result.randomTerm(thisQuery);
		}		
		return result;
	}


	public SearchResult search(String query) throws SearchException {
        return search(query, null);
	}


	public SearchResult search(String query, String debug) throws SearchException {
        if (rateLimitReached()) {
            this.lastMessage = "Too many queries";
            this.lastUsedError = new Date().getTime();
            throw new SearchException(this.lastMessage);
        }
        SearchResult result;
		try {
		    if (this.urlAPITemplate == null) {
		        throw new SearchException("No API Template");
		    }
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
            if (this.mimeType != null && this.mimeType.equals(SearchResult.SEARSIA_MIME_TYPE)) {
            	result = searsiaSearch(page, debug);
            } else {
            	result = xpathSearch(url, page, debug);
            }
            if (this.rerank != null && query != null) {
                result.scoreReranking(query, this.rerank);
            }
            if (!result.getHits().isEmpty()) {
                this.nrOfOk += 1; // only success if at least one result
                this.lastUsedOk = new Date().getTime();                
            }
		} catch (Exception e) {  // catch all, also runtime exceptions
	        this.nrOfError += 1;
	        this.lastUsedError = new Date().getTime();
	        SearchException se = createPrivateSearchException(e);
		    this.lastMessage = se.getMessage();
			throw se;
		}
		result.setQuery(query);
        result.setResourceId(this.getId());
        return result;
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
		} catch (IOException e) {
		    String message = e.getMessage();
		    if (message != null && message.equals(goneErrorMessage)) { // TODO: not using error message like this??
		        engine = deletedResource(resourceid);
		    } else {
    			throw createPrivateSearchException(e);
		    }
		} catch (Exception e) {
		    throw createPrivateSearchException(e);
		}
        return engine;
	}
	
	private Resource deletedResource(String resourceid) throws SearchException {
	    Resource engine = null;
	    JSONObject json = new JSONObject();
        json.put("id", resourceid);
        json.put("deleted", true);
        try {
            engine = new Resource(json);
        } catch (XPathExpressionException e) {
            throw new SearchException(e);
        }
        return engine;
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
		} else if (this.mimeType.equals("text/html")) {
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

	private Document parseDocumentHTML(String htmlString, String urlString) {
        org.jsoup.nodes.Document jsoupDoc = Jsoup.parse(htmlString, urlString);
        return DOMBuilder.jsoup2DOM(jsoupDoc);
    }

	/**
	 * From a Javascript callback result, get out all JSON objects and put them in a single object
	 * @param scriptString 
	 * @return Document
	 * @throws IOException
	 */
	private Document parseDocumentJavascript(String scriptString) {
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
                    try {
                    	array.put(new JSONObject(subString));
                    } catch (JSONException e) { }
		    	}
		    }
		}
		JSONObject object = new JSONObject();
		object.put("list", array);
        return DOMBuilder.json2DOM(object);
	}
	
    private Document parseDocumentJSON(String jsonString) {
        if (jsonString.startsWith("[")) {  // turn lists into objects    
        	jsonString = "{\"list\":" + jsonString + "}";
        }
        return DOMBuilder.json2DOM(new JSONObject(jsonString));
    }
	
    private Document parseDocumentXML(String xmlString) {
        return DOMBuilder.string2DOM(xmlString);
    }

    private String fillTemplate(String template, String query) throws SearchException {
  		String url = template;
   		for (String param: getPrivateParameterKeys()) {
   			url = url.replaceAll("\\{" + param + "\\??\\}", getPrivateParameter(param));
   		}
   		url = url.replaceAll("\\{q\\??\\}", query);
        url = url.replaceAll("\\{[0-9A-Za-z\\-_]+\\?\\}", ""); // remove optional parameters
		if (url.matches(".*\\{[0-9A-Za-z\\-_]+\\}.*")) {
		    String param = url.substring(url.indexOf("{"), url.indexOf("}") + 1);
			throw new SearchException("Missing url parameter " + param);
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
        Long timePassed = now - this.lastUsed;
        this.lastUsed = now;
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
    
    private String correctContentType(String contentType) { // TODO more charsets
        if (contentType != null && contentType.toLowerCase().contains("charset=iso-8859-1")) {
            contentType = "ISO-8859-1";
        } else {
            contentType = "UTF-8";
        }
        return contentType;
    }
    
    private InputStreamReader httpConnect(URLConnection connection, String postString) throws IOException {
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
        int responseCode = http.getResponseCode();
        if (responseCode == 301) { // FollowRedirects did not work?!        
            throw new IOException("Moved permanently");
        }
        if (responseCode == 410) { // Gone: we will use this special error message elsewhere in this code.
            throw new IOException(goneErrorMessage);
        }
        String contentType = correctContentType(http.getHeaderField("Content-Type"));
        return new InputStreamReader(http.getInputStream(), contentType);
    }
        
    private InputStreamReader fileConnect(URLConnection connection) throws IOException {
    	String fileName = connection.getURL().getFile();
    	return new InputStreamReader(new FileInputStream(new File(fileName)), "UTF-8");
    }

    private String getCompletePage(String urlString, String postString, Map<String, String> headers) throws IOException {
        URL url = new URL(urlString);
        URLConnection connection = setConnectionProperties(url, headers);
        InputStreamReader reader;
        if (url.getProtocol().equals("file")) {
        	reader = fileConnect(connection);
        } else {
        	reader = httpConnect(connection, postString);
        }
        BufferedReader in = new BufferedReader(reader);
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
	
	public boolean isDeleted() {
	    return this.deleted;
	}
	
	public int getAllowance() {
        long timePassed = new Date().getTime() - this.lastUsed;
        double currentAllowance = this.allowance + (((double) timePassed / defaultPER)) * this.rate;
        if (currentAllowance > this.rate) {
            return this.rate;
        }
        return (int) currentAllowance;
	}
	
	public float getPrior() {
		if (this.prior == null) {
			return 0.0f; 
		} else {
			return this.prior;
		}
	}

	public int getNrOfErrors() {
	    return this.nrOfError;
	}
	
	public int getNrOfSuccess() {
	    return this.nrOfOk;
	}
	
	private long secondsAgo(long last) {
 	    long now = new Date().getTime();
        long ago = 1 + (now - last) / 1000;
        if (ago < 0 || ago > 8640000l) { // 100 days...
            ago = 8640000l;
        }
        return  ago;
	}

    public String getLastError() {
        return this.lastMessage;
    }
	
    public String getLastUsedString() {
        return dateFormat.format(new Date(this.lastUsed));
    }

    public String getLastSuccessDate() {
        return dateFormat.format(new Date(this.lastUsedOk));
    }

    public String getLastErrorDate() {
        return dateFormat.format(new Date(this.lastUsedError));
    }

	public String getLastUpdatedString() {
        return dateFormat.format(new Date(this.lastUpdated));
	}

    public String getUpSinceString() {
        return dateFormat.format(new Date(this.upsince));
    }

    public long getLastUpdatedSecondsAgo() {
	    return secondsAgo(this.lastUpdated);
	}

    public Long getLastUsedSecondsAgo() {
        return secondsAgo(this.lastUsed);
    }
    
    public boolean isHealthy() {
        return this.lastUsedOk >= this.lastUsedError;
    }


    public Resource getLocalResource() {
        JSONObject json = new JSONObject();
        Resource result = null;
        json.put("id", this.getId());
        json.put("mimetype", SearchResult.SEARSIA_MIME_TYPE);
        String value = this.getName();
        if (value != null) { json.put("name", value); }
        json.put("name",  this.getName());
        value = this.getBanner();
        if (value != null) { json.put("banner", value); }
        value = this.getFavicon();
        if (value != null) { json.put("favicon", value); }
        value = this.getSuggestTemplate();
        if (value != null) { json.put("suggesttemplate", value); }
        value = this.getTestQuery();
        if (value != null) { json.put("testquery", value); }
        try {
            result = new Resource(json);
        } catch (XPathExpressionException e) { }
        return result;
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
			throw new RuntimeException(e.getMessage());
		}
	}

	/**
	 * Update resource
	 * @param e2
	 */
	public void updateWith(Resource e2) { // TODO: bad idea in multi-threaded app!?
        setLastUpdatedToNow();
        if (!equals(e2)) {
            if (this.id != null && !this.id.equals(e2.id)) throw new RuntimeException("Cannot update resource ID.");
            setUpSinceToNow();
            this.nrOfOk = 0;
            this.nrOfError = 0;
            this.lastMessage = null;
            this.id       = e2.id;
            this.deleted  = e2.deleted;
            this.name     = e2.name;
            this.urlUserTemplate = e2.urlUserTemplate;
            this.favicon  = e2.favicon;
            this.banner   = e2.banner;
            this.urlAPITemplate = e2.urlAPITemplate;
            this.urlSuggestTemplate = e2.urlSuggestTemplate;
            if (e2.mimeType == null) { this.mimeType = SearchResult.SEARSIA_MIME_TYPE; }
            else { this.mimeType = e2.mimeType; }
            this.rerank   = e2.rerank;
            this.postString = e2.postString;
            this.postQueryEncode = e2.postQueryEncode;
            if (e2.testQuery == null) { this.testQuery = defaultTestQuery; } else { this.testQuery = e2.testQuery; }
            this.prior = e2.prior;
            this.rate = e2.rate;
            this.itemXpath = e2.itemXpath;
            this.extractors = e2.extractors;
            this.headers   = e2.headers;
            this.privateParameters = e2.privateParameters;          
        }
	}
	
	public void updateAllowance(Resource e2) {
	    if (this.id != null && !this.id.equals(e2.id)) throw new RuntimeException("Cannot update resource ID.");
	    this.allowance = e2.allowance;
	}


    public JSONObject toJson() {
        return toJsonEngine();
    }

    public JSONObject toJsonEngine() {
        JSONObject engine = new JSONObject();
        if (id != null) engine.put("id", id);
        if (deleted) {
            engine.put("deleted", true);
        } else {
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
        }
        return engine;
    }


    public JSONObject toJsonEngineDontShare() {
        JSONObject engine = new JSONObject();
        if (id != null)                  engine.put("id", id);
        if (deleted) {
            engine.put("deleted", true);
        } else {
            if (name != null)                engine.put("name", name);
            if (urlUserTemplate != null)     engine.put("urltemplate", urlUserTemplate);
            if (favicon != null)             engine.put("favicon", favicon);
            if (banner != null)              engine.put("banner", banner);
            if (mimeType != null && !mimeType.equals(SearchResult.SEARSIA_MIME_TYPE))
                                             engine.put("mimetype", mimeType);
            if (rerank != null)              engine.put("rerank", rerank);
            if (rate != defaultRATE)         engine.put("maxqueriesperday", rate);
        }
        return engine;
    }


    public JSONObject toJsonHealth() {
        JSONObject health = new JSONObject();
        health.put("dayallowance", getAllowance());
        health.put("requestsok",   this.nrOfOk); 
        health.put("requestserr",  this.nrOfError); 
        health.put("lastsuccess",  getLastSuccessDate());
        health.put("lasterror",    getLastErrorDate());
        health.put("lastupdated",  getLastUpdatedString());
        health.put("upsince",      getUpSinceString());
        if (this.lastMessage != null) health.put("lastmessage", this.lastMessage);
        return health;
    }


    /**
     * Only used at startup when reading resources from disk
     * @param health
     * @throws ParseException
     */
    public void updateHealth(JSONObject health) throws ParseException {
        //try {
            Integer num = health.getInt("requestsok");
            if (num != null) this.nrOfOk = num;
            num = health.getInt("requestserr");
            if (num != null) this.nrOfError = num;
            this.lastUsedOk  = dateFormat.parse(health.getString("lastsuccess")).getTime();
            this.lastUsedError = dateFormat.parse(health.getString("lasterror")).getTime();
            this.lastUpdated = dateFormat.parse(health.getString("lastupdated")).getTime();
            this.upsince = dateFormat.parse(health.getString("upsince")).getTime();
            if (health.has("lastmessage")) this.lastMessage   = health.getString("lastmessage");
       // } catch (Exception e) { } // TODO: woops?
    }

    
    @Override
    public int compareTo(Resource e2) {
        Float score1 = getPrior();
        Float score2 = e2.getPrior();
        return score1.compareTo(score2);
    }

   
    @Override
    public boolean equals(Object o) {  // TODO: AARGH, can't this be done simpler?
    	if (o == null) return false;
    	Resource e = (Resource) o;
    	if (!stringEquals(this.getId(), e.getId())) return false;
    	if (this.isDeleted() != e.isDeleted()) return false;
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
