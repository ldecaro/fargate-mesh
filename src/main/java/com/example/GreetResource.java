package com.example;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.MediaType;

/**
 * @author Luiz Decaro
 */
@Path("/")
public class GreetResource {

    private String GREETING_SERVICE = null;
	private String GREETING_SERVICE_PROTO	=	"http";
    private final String webPage    =   getFile("greet.html");

    public GreetResource(){

        if (System.getenv("GREETING_SERVICE") == null ){
            throw new RuntimeException("Environment variable GREETING_SERVICE not found. It must be a URL to the backend service");
        }else{
            GREETING_SERVICE = System.getenv("GREETING_SERVICE");
            System.out.println("Using greeting service: "+GREETING_SERVICE);
        }

        if (System.getenv("GREETING_SERVICE_PROTO") == null ){
            System.out.println("Calling backend greeting service using protocol: "+GREETING_SERVICE_PROTO);
        }else{
            GREETING_SERVICE_PROTO = System.getenv("GREETING_SERVICE_PROTO");
            System.out.println("Using greeting service proto: "+GREETING_SERVICE_PROTO);
        }		
    }
		
    @GET
	@Produces("text/html; charset=UTF-8")
	@Path("/{name}")
    public InputStream getGreet( @PathParam("name") String name) {

		String path = name;
		if(path == null || "".equals(path)){
			path = "Visitor";
			System.out.println("No user found in the path param. Going with Visitor");
		}
		final String backendService	=	GREETING_SERVICE_PROTO+"://"+GREETING_SERVICE+"/"+path;

    	System.out.println("Fetching greet from backend: "+backendService);
		Client client = ClientBuilder.newBuilder()
									.connectTimeout(2000, TimeUnit.MILLISECONDS)
									.readTimeout(20000, TimeUnit.MILLISECONDS)
									.build();          
        String json =    null;
        try {
            // String data 
            json = client.target( backendService )
                                .request(MediaType.APPLICATION_JSON)
                                .get(String.class);

            System.out.println("Greeting found: "+json);
        }catch(Exception e) {
			e.printStackTrace();
            System.out.println("Error rendering web page greeting-ui. Msg: "+e.getMessage());
        }                                    

    	return new ByteArrayInputStream(String.format(webPage, json).getBytes());
    }

	private String getFile(String filename) {
    	
    	try {    		
        	URI uri	=	this.getClass().getResource(filename).toURI();
            if( uri == null ){
                System.out.println("Could not find the greet.html page. failing..");
                throw new RuntimeException("Could not find file "+filename);
            }

        	if( uri.toString().indexOf("jar:file") == -1) {
        		return new String ( Files.readAllBytes( Paths.get(uri) ) );
        	}

    		FileSystem fs	=	GreetResource.initFileSystem(uri);    		
    		String	s	=	new String(Files.readAllBytes( Paths.get(uri) ));
    		fs.close();
    		return s;
    	} catch (URISyntaxException e) {
			System.out.println("Cannot load  file "+filename+". URISyntaxException:"+e.getMessage());
		} catch( IOException ioe) {
			System.out.println("Cannot load file "+filename+". IOException:"+ioe.getMessage());			
		}
    	return "";
	}
	
	private static FileSystem initFileSystem(URI uri) throws IOException{
	    try{
	        return FileSystems.getFileSystem(uri);
	    }catch( FileSystemNotFoundException e ){
	        Map<String, String> env = new HashMap<>();
	        env.put("create", "true");
	        return FileSystems.newFileSystem(uri, env);
	    }
    }    
}
