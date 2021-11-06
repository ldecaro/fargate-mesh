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

    private DockerImageAsset morningImageAsset  =   null;
    private DockerImageAsset afternoonImageAsset=   null;
    private DockerImageAsset uiImageAsset       =   null;
    
    DataPlane(Construct scope, String id, DataPlaneProps props){

        super(scope, id, props);

        final String APP_NAME   =   props.getAppName();
        final String ACCOUNT    =   props.getEnv().getAccount();
        final String REGION     =   props.getEnv().getRegion();
        Util    util            =   new Util();

        //Create the ECR using the CLI because I'm uploading the containers in ECR using the command line also (ECR deployment exists as a project in typescript)

        
        //Repository ecr  =   Repository.Builder.create(this, APP_NAME+"-repo").imageScanOnPush(Boolean.FALSE).repositoryName(APP_NAME).build();

        //get ECR Password and create the settings.xml with the temporary password
        // String ecrPassword = DataPlane.ecrPassword(REGION);
        // String settingsTemplate =   util.getFile("settings-template.xml");
        // String settings =   MessageFormat.format(settingsTemplate, ACCOUNT, REGION, ecrPassword);
        // try{
        //     Files.deleteIfExists( (new File("settings.xml")).toPath() );
        //     PrintWriter writer = new PrintWriter("settings.xml");
        //     writer.println(settings);
        //     writer.close();
        // }catch(Exception e){
        //     throw new RuntimeException (e);
        // }

        // //Compile all the three projects using the maven deploy command and Runtime.exec.
        // if( !deployMorning() ){ 
        //     throw new RuntimeException ("Could not deploy the morning service");
        // }
        // if( !deployAfternoon() ){
        //     throw new RuntimeException ("Could not deploy the afternoon service");
        // }


        //Add the envoy and x-ray containers into this repository.


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

        this.morningImageAsset  =   morning;
        this.afternoonImageAsset    =   afternoon;
        this.uiImageAsset   =   ui;

        // System.out.println("Morning URI: "+morning.getImageUri()+", Repository: "+afternoon.getRepository().getRepositoryName());
        // System.out.println("Afternoon URI: "+afternoon.getImageUri()+", Repository: "+afternoon.getRepository().getRepositoryName());
        // System.out.println("UI URI: "+ui.getImageUri()+", Repository: "+ui.getRepository().getRepositoryName());

        // Repository ecrRepo  =   Repository.Builder.create(this, APP_NAME+"-repo").repositoryName(APP_NAME+"-repo").build();
        

        // se eu for com o DockerImageAsset eu consigo referenciar a imagem via ContainerImage.fromDockerImageAseet
        // fazer o deploy dos containers e das imagens usando o mecanismo comentado acima. 
        // tem que comentar o último plugin dos 3 pom.xml
    }


    public DockerImageAsset getMorningImageAsset() {
        return this.morningImageAsset;
    }

    public void setMorningImageAsset(DockerImageAsset morningImageAsset) {
        this.morningImageAsset = morningImageAsset;
    }

    public DockerImageAsset getAfternoonImageAsset() {
        return this.afternoonImageAsset;
    }

    public void setAfternoonImageAsset(DockerImageAsset afternoonImageAsset) {
        this.afternoonImageAsset = afternoonImageAsset;
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
