package com.example;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * @author Luiz Decaro
 */
@Path("/")
public class GreetResource {
		
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{name}")
    public Response getGreet( @DefaultValue("Visitor") @PathParam("name") String name) {

    	System.out.println("Let's greet "+name);
    	String json	=	"{\"name\":\""+name+"\", \"period\":\"morning\"}";
        System.out.println(json);
    	return Response.ok(json).build() ;
    }
}
