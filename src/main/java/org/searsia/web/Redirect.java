package org.searsia.web;

import java.io.IOException;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import org.searsia.SearchResult;


@Path("searsia")
public class Redirect {
    
    String id;

    public Redirect(String id) throws IOException {
        this.id = id;
    }

    @GET
    @Produces(SearchResult.SEARSIA_MIME_ENCODING)
    public Response notFound() {
        return SearsiaApplication.responseError(404, "Not found");
    }

    /**
     * Redirect, not used because it does not always behave well in
     * case web servers do a simple rewrite of URLs.
     * @return
     */
    public Response redirect() {
        return  Response
                .status(301)
                .entity("")
                .header("Access-Control-Allow-Origin", "*")
                .header("Location", this.id + ".json")
                .build();
    }
    
}