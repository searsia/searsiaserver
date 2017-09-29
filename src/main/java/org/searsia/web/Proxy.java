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

package org.searsia.web;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;

import org.searsia.engine.Resource;
import org.searsia.index.ResourceIndex;

/**
 * Provides a proxy for any (image) url 
 * and a special caching proxy for the resources' fav icons.
 */
@Path("images")
public class Proxy {

    private ResourceIndex engines;
	private Map<String,ResponseBuilder> iconStore = new HashMap<String,ResponseBuilder>();
    private String lastModified = null;
	
    public Proxy(ResourceIndex engines) throws IOException {
        DateFormat dateFormat = new SimpleDateFormat("EEE, FF MMM yyyy hh:mm:ss zzz", Locale.ROOT);		
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        this.lastModified = dateFormat.format(new Date());
        this.engines  = engines;
    }

    @GET
    public Response query(@QueryParam("url") String url,
    		              @Context HttpHeaders headers) {
    	if (url == null) {
    		return Response.status(404).build();
    	}
        try {
            if (headers.getRequestHeader("If-Modified-Since") != null 
                || headers.getRequestHeader("If-None-Match") != null) {
                return Response.notModified().build();
            } else {
                return getStreamBuilder(url).build();
            }
        } catch (Exception e) {
            return Response.status(503).build();  // 503 = unavailable
        }
    }
    
	@GET @Path("{resourceid}")
	public Response icon(@PathParam("resourceid") String resourceid, 
			             @Context HttpHeaders headers) {
		try {
        	if (headers.getRequestHeader("If-Modified-Since") != null 
        		|| headers.getRequestHeader("If-None-Match") != null) {
        		return Response.notModified().build();
        	} else {
            	return getWebIcon(resourceid);
            }      
        } catch (Exception e) {
            return Response.status(503).build();
        }
    }
    
    private Response getWebIcon(String resourceid) throws IOException {
        Resource engine = engines.get(resourceid);
        if (engine == null) {
        	engine = engines.getMyself();
        	if (engine == null || !engine.getId().equals(resourceid)) {
        		return Response.status(404).build();
        	}
        }
        String iconFile = engine.getFavicon();
        if (iconFile == null) {
            return Response.status(503).build();
        }
        ResponseBuilder builder = iconStore.get(resourceid);
        if (builder == null) {
        	builder = getCachedBuilder(iconFile);
        	iconStore.put(resourceid, builder);
        }
        return builder.build();
    }

    private ResponseBuilder getCachedBuilder(String urlString) throws IOException {
    	HttpURLConnection http = getHttp(urlString);
        String contentType = http.getHeaderField("Content-Type");
        if (contentType == null) {
        	contentType = "image/png";
        }
        InputStream stream = http.getInputStream();
    	ByteArrayOutputStream baos = new ByteArrayOutputStream();
    	byte[] buffer = new byte[1024]; 
    	int bytesRead;
        while ((bytesRead = stream.read(buffer)) != -1) {
  	        baos.write(buffer, 0, bytesRead);
  	    }
  	    return Response.ok(baos.toByteArray(), contentType)
  	    	           .header("Last-Modified", lastModified);
    }

    private ResponseBuilder getStreamBuilder(String urlString) throws IOException {
    	HttpURLConnection http = getHttp(urlString);
        ResponseBuilder builder = Response.ok(http.getInputStream());
        String field = http.getHeaderField("Content-Type");
        if (field != null) builder.header("Content-Type", field);
        field = http.getHeaderField("Content-Length");
        if (field != null) builder.header("Content-Length", field);
        field = http.getHeaderField("Expires");
        if (field != null) builder.header("Expires", field);
        field = http.getHeaderField("Cache-Control");
        if (field != null) builder.header("Cache-Control", field);
        field = http.getHeaderField("Last-Modified");
        if (field != null) builder.header("Last-Modified", field);
        return builder;
	}

    private HttpURLConnection getHttp(String urlString) throws IOException {
        URL url = new URL(urlString);
        URLConnection connection = url.openConnection();
        connection.setRequestProperty("User-Agent", "Searsia/1.0");
        connection.setRequestProperty("Accept", "*/*");
        connection.setReadTimeout(4000);
        connection.setConnectTimeout(4000);
        HttpURLConnection http = (HttpURLConnection) connection;
        http.setInstanceFollowRedirects(true);
        http.setRequestMethod("GET");
        http.connect();
        return http;
    }
   
}