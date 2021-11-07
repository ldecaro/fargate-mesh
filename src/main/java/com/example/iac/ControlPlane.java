package com.example.iac;

import java.util.Map;

import org.jetbrains.annotations.Nullable;

import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Environment;
import software.amazon.awscdk.core.Stack;
import software.amazon.awscdk.core.StackProps;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ecr.assets.DockerImageAsset;
import software.amazon.awscdk.services.ecs.Cluster;
import software.amazon.awscdk.services.iam.Role;

public class ControlPlane extends Stack {

     String APP_NAME    =   null;
     String DOMAIN_NAME =   null;
     String ACCOUNT     =   null;
     String REGION      =   null;
     String meshName    =   null;
     Role taskRole              =   null;
     Role taskExecutionRole     =   null;
     String ROUTER_SVC_NAME =   null;
     final int ROUTER_PORT=    8080;

    ControlPlane(Construct scope, String id, ControlPlaneProps props){

        super(scope, id, props);
        //ecs
        ECSLayer ecs    =   new ECSLayer(this, props.getAppName()+"-ecs-layer", props);
        //mesh
        new MeshLayer(this, 
            props.getAppName()+"-mesh-layer", 
            ecs.getV1Fargate(), 
            ecs.getV2Fargate(), 
            ecs.getUiFargate(), 
            ecs.getNamespaceName(), 
            props );

    }


    public static class ControlPlaneProps implements StackProps {

        private String appName = null;
        private Environment environment = null;
        private Boolean terminationProtection = Boolean.FALSE;
        private DockerImageAsset v1ImageAsset = null;
        private DockerImageAsset v2ImageAsset = null;
        private DockerImageAsset imageUI = null;
        private String domainName   =   null;
        private Cluster cluster =   null;
        private Vpc vpc =   null;
        
        public String getAppName(){
            return this.appName;
        }        
        
        public Environment getEnv(){
            if(environment == null ){
                return Util.makeEnv(null, null);
            }else{
                return environment;
            }
        }

        public String getStackName(){
            return appName+"-cp";
        }   

        @Override
        public @Nullable String getDescription() {
            return "Control Plane Stack of the app "+getAppName();
        }      

        @Override
        public @Nullable Map<String, String> getTags() {
            return StackProps.super.getTags();
        }  

        @Override
        public @Nullable Boolean getTerminationProtection() {
            return this.terminationProtection;
        }        
        
        public DockerImageAsset getV1ImageAsset(){
            return this.v1ImageAsset;
        }

        public DockerImageAsset getV2ImageAsset(){
            return this.v2ImageAsset;
        }

        public DockerImageAsset getImageUIAsset(){
            return this.imageUI;
        }

        public String getDomainName(){
            return this.domainName;
        }

        public Cluster getCluster() {
            return cluster;
        }

        public void setCluster(Cluster cluster) {
            this.cluster = cluster;
        }

        public Vpc getVpc() {
            return vpc;
        }

        public void setVpc(Vpc vpc) {
            this.vpc = vpc;
        }

        public ControlPlaneProps(String appName, DockerImageAsset v1, DockerImageAsset v2, DockerImageAsset ui, Map<String, String> tags, Boolean terminationProtection, Environment env, String domainName, Vpc vpc, Cluster cluster){

            this.appName = appName;
            this.v1ImageAsset = v1;
            this.v2ImageAsset = v2;
            if(tags!=null)
            StackProps.super.getTags().putAll(tags);
            this.terminationProtection = terminationProtection;
            this.environment = env;
            this.domainName=    domainName;
            this.imageUI = ui;
            this.vpc = vpc;
            this.cluster = cluster;
        }

        public static Builder builder(){
            return new Builder();
        }

        public static class Builder{

            private Map<String,String> tags = null;
            String appName = null;
            private Boolean terminationProtection = Boolean.FALSE;
            private DockerImageAsset v1 = null;
            private DockerImageAsset v2 = null;
            private DockerImageAsset ui = null;
            private Environment environment = null;
            private String domain   =   null;
            private Vpc vpc =   null;
            private Cluster cluster =   null;

            public Builder appName(String appName){
                this.appName = appName;
                return this;
            }

            public Builder env(Environment environment){
                this.environment = environment;
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
            
            public Builder imageMorning(DockerImageAsset v1){
                this.v1 = v1;
                return this;
            } 

            public Builder imageAfternoon(DockerImageAsset v2){
                this.v2 = v2;
                return this;
            } 

            public Builder imageUI(DockerImageAsset imageUI){
                this.ui = imageUI;
                return this;
            }

            public Builder domain(String domain){
                this.domain =   domain;
                return this;
            }

            public Builder vpc(Vpc vpc){
                this.vpc = vpc;
                return this;
            }

            public Builder cluster(Cluster cluster){
                this.cluster = cluster;
                return this;
            }

            public ControlPlaneProps build(){
                return new ControlPlaneProps(appName, v1, v2, ui, tags, terminationProtection, environment, domain, vpc, cluster);
            }            
        }
    }
}
