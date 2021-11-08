package com.example.iac;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Environment;
import software.amazon.awscdk.core.Stack;
import software.amazon.awscdk.core.StackProps;
import software.amazon.awscdk.services.ecr.assets.DockerImageAsset;

public class DataPlane extends Stack {

    private DockerImageAsset v1Asset  =   null;
    private DockerImageAsset v2Asset=   null;
    private DockerImageAsset uiImageAsset       =   null;
    
    DataPlane(Construct scope, String id, DataPlaneProps props){

        super(scope, id, props);

        //Create the 3 containers, one for each project: Gateway (Envoy only), Morning and Afternoon Services.  
        DockerImageAsset morning = DockerImageAsset.Builder
                        .create(this, "greeting/morning")
                        .directory("./greeting-morning")
                        .build();

        DockerImageAsset afternoon = DockerImageAsset.Builder
                        .create(this, "greeting/afternoon")
                        .directory("./greeting-afternoon")
                        .build();        
        
        DockerImageAsset ui = DockerImageAsset.Builder
                        .create(this, "greeting/ui")
                        .directory("./")
                        .build();  

        this.v1Asset  =   morning;
        this.v2Asset    =   afternoon;
        this.uiImageAsset   =   ui;
    }


    public DockerImageAsset getV1Asset() {
        return this.v1Asset;
    }

    public void setV1Asset(DockerImageAsset v1Asset) {
        this.v1Asset = v1Asset;
    }

    public DockerImageAsset getV2Asset() {
        return this.v2Asset;
    }

    public void setV2Asset(DockerImageAsset v2Asset) {
        this.v2Asset = v2Asset;
    }

    public DockerImageAsset getUiImageAsset() {
        return uiImageAsset;
    }

    public void setUiImageAsset(DockerImageAsset uiImageAsset) {
        this.uiImageAsset = uiImageAsset;
    }


    private static Boolean deployMorning(){

        Runtime runtime = Runtime.getRuntime();
        int     exitValue   =   0;
        try {

            Process ipProcess = runtime.exec("mvn --settings settings.xml -f greeting-morning/pom.xml clean deploy");
            //  exitValue  = ipProcess.waitFor();
            BufferedReader stdInput = new BufferedReader(new 
            InputStreamReader(ipProcess.getInputStream()));

            BufferedReader stdError = new BufferedReader(new 
                InputStreamReader(ipProcess.getErrorStream()));

            // Read the output from the command
            System.out.println("Here is the standard output of the command:\n");
            String s = null;
            while ((s = stdInput.readLine()) != null) {
                System.out.println(s);
            }

            // Read any errors from the attempted command
            System.out.println("Here is the standard error of the command (if any):\n");
            while ((s = stdError.readLine()) != null) {
                System.out.println(s);
            }


        } catch (IOException e)          { e.printStackTrace(); } 

        return (exitValue == 0);
    }

    private static Boolean deployAfternoon(){

        Runtime runtime = Runtime.getRuntime();
        int     exitValue   =   0;
        try {

            Process ipProcess = runtime.exec("mvn --settings settings.xml -f greeting-afternoon/pom.xml deploy");
            exitValue = ipProcess.waitFor();
            

        } catch (IOException e)          { e.printStackTrace(); } 
        catch (InterruptedException e) { e.printStackTrace(); }

        return (exitValue == 0);
    }

    private static String ecrPassword(final String REGION){

        Runtime runtime = Runtime.getRuntime();
        String password =   null;
        try {

            Process ipProcess = runtime.exec("aws ecr get-login-password --region "+REGION);
            BufferedReader stdInput = new BufferedReader(new 
            InputStreamReader(ipProcess.getInputStream()));
       
            // Read the output from the command
            String s = null;
            while ((s = stdInput.readLine()) != null) {
                password=   s;
            }
            stdInput.close();
            ipProcess.destroy();

        } catch (IOException e)          { e.printStackTrace(); } 
        
        return password;
    }

    static class DataPlaneProps implements StackProps{


        String appName;
        Map<String,String> tags;
        Boolean terminationProtection = Boolean.FALSE;

        public String getAppName(){
            return this.appName;
        }

        public Environment getEnv(){
            return Util.makeEnv(null, null);
        }
			
        public DataPlaneProps(String appName, Map<String,String> tags, Boolean terminationProtection){
            this.appName = appName;
            this.tags = tags;
            this.terminationProtection = terminationProtection;
        }

        @Override
        public @Nullable Map<String, String> getTags() {
            return StackProps.super.getTags();
        }

        @Override
        public @Nullable Boolean getTerminationProtection() {
            return this.terminationProtection;
        }

        public String getStackName(){
            return appName+"-dp";
        }

        @Override
        public @Nullable String getDescription() {
            return "Data plane stack of the app "+getAppName();
        }

        public static Builder builder(){
            return new Builder();
        }
        static class Builder{

            private String appName;
            private Map<String,String> tags;
            private Boolean terminationProtection = Boolean.FALSE;


            public Builder appName(String appName){
                this.appName = appName;
                return this;
            }

            public Builder tags(Map<String, String> tags){
                this.tags = tags;
                return this;
            }

            public Builder terminationProtection(Boolean terminationProtection){
                this.terminationProtection = terminationProtection;
                return this;
            }

            public DataPlaneProps build(){
                return new DataPlaneProps(appName, tags, terminationProtection);
            }
        }			        
    }
}
