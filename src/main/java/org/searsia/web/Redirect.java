package org.searsia.web;

import java.io.IOException;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;


@Path("/")
public class Redirect {
    
    String id;

    public Redirect(String id) throws IOException {
        this.id = id;
    }

    @GET
    public Response redirect() {
        return  Response
                .status(301)
                .entity("")
                .header("Access-Control-Allow-Origin", "*")
                .header("Location", this.id + ".json")
                .build();
    }
    
}