package org.searsia.web;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;

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


@Path("{resourceid}")
public class Proxy {

    private ResourceIndex engines;
	private Map<String,byte[]> iconStore = new HashMap<String,byte[]>();

    public Proxy(ResourceIndex engines) throws IOException {
        this.engines  = engines;
    }

    @GET @Path("proxy")
    public Response query(@PathParam("resourceid") String resourceid, @QueryParam("url") String url, @Context HttpHeaders headers) {
        try {
            if (headers.getRequestHeader("If-Modified-Since") != null || headers.getRequestHeader("If-None-Match") != null) {
                return Response.notModified().build();
            } else {
                return getWebResponse(url);
            }
        } catch (Exception e) {
            return Response.status(503).build();
        }
    }
    
	@GET @Path("icon")
	public Response icon(@PathParam("resourceid") String resourceid, @Context HttpHeaders headers) {
		try {
        	if (headers.getRequestHeader("If-Modified-Since") != null || headers.getRequestHeader("If-None-Match") != null) {
        		return Response.notModified().build();
        	} else {
            	return getWebIcon(resourceid);
            }      
        } catch (Exception e) {
            return Response.status(503).build(); // unavailable
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
        return getIconResponse(iconFile);
    }

    private Response getIconResponse(String urlString) throws IOException {
    	HttpURLConnection http = getHttp(urlString);
        InputStream stream = http.getInputStream();
    	ByteArrayOutputStream baos = new ByteArrayOutputStream();
    	byte[] buffer = new byte[1024]; // Experiment with this value
    	int bytesRead;
        while ((bytesRead = stream.read(buffer)) != -1) {
  	        baos.write(buffer, 0, bytesRead);
  	    }
  	    return Response.ok(baos.toByteArray(), "image/png").build();
    }


    private Response getWebResponse(String urlString) throws IOException {
    	HttpURLConnection http = getHttp(urlString);
        InputStream stream = http.getInputStream();
    	return responseWithHeaders(http, stream).build();
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

    private ResponseBuilder responseWithHeaders(HttpURLConnection http, InputStream stream) {
        ResponseBuilder builder = Response.ok(stream);
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
   
}