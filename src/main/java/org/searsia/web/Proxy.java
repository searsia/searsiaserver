package org.searsia.web;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;

import org.searsia.index.ResourceIndex;


@Path("{resourceid}/proxy")
public class Proxy {

    private ResourceIndex engines;

    public Proxy(ResourceIndex engines) throws IOException {
        this.engines  = engines;
    }

    @GET
    public Response query(@PathParam("resourceid") String resourceid, @QueryParam("url") String url, @Context HttpHeaders headers) {
        try {
            if (url != null && (engines.getMyself().getId().equals(resourceid) || engines.get(resourceid) != null)) {
                if (headers.getRequestHeader("If-Modified-Since") != null || headers.getRequestHeader("If-None-Match") != null) {
                    return Response.status(304).build(); // cheating! Maybe really check if it is modified?
                } else {
                    return getWebResponse(url);
                }
            } else {
                return SearsiaApplication.responseError(404, "Resource not found: " + resourceid);
            }      
        } catch (Exception e) {
            return SearsiaApplication.responseError(503, "Unavailable: " + e.getMessage());
        }
    }
    
    private Response getWebResponse(String urlString) throws IOException {
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
        InputStream stream = http.getInputStream();
        return responseWithHeaders(http, stream).build();
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
