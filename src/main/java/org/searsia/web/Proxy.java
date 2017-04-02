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
import javax.ws.rs.core.Response;

import org.searsia.index.ResourceIndex;


@Path("{resourceid}/proxy")
public class Proxy {

    private ResourceIndex engines;

    public Proxy(ResourceIndex engines) throws IOException {
        this.engines  = engines;
    }

    @GET
    public Response query(@PathParam("resourceid") String resourceid, @QueryParam("url") String url) {
        try {
            if (url != null && (engines.getMyself().getId().equals(resourceid) || engines.get(resourceid) != null)) {
                return getWebData(url);
            } else {
                return SearsiaApplication.responseError(404, "Resource not found: " + resourceid);
            }      
        } catch (Exception e) {
            return SearsiaApplication.responseError(503, "Unavailable: " + e.getMessage());
        }
    }
    
    private Response getWebData(String urlString) throws IOException {
        URL url = new URL(urlString);
        URLConnection connection = url.openConnection();
        connection.setRequestProperty("User-Agent", "Searsia/1.0");
        connection.setRequestProperty("Accept", "*/*");  // TODO If-Modified-Since:
        connection.setReadTimeout(4000);
        connection.setConnectTimeout(4000);
        HttpURLConnection http = (HttpURLConnection) connection;
        http.setInstanceFollowRedirects(true);
        http.setRequestMethod("GET");
        http.connect();
        String contentType = http.getHeaderField("Content-Type");
        InputStream stream = http.getInputStream();
        return Response.ok(stream).header("Content-Type", contentType).build();
    }
}
