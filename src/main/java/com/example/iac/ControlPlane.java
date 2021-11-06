package com.example.iac;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Duration;
import software.amazon.awscdk.core.Environment;
import software.amazon.awscdk.core.Stack;
import software.amazon.awscdk.core.StackProps;
import software.amazon.awscdk.services.appmesh.AccessLog;
import software.amazon.awscdk.services.appmesh.Backend;
import software.amazon.awscdk.services.appmesh.HealthCheck;
import software.amazon.awscdk.services.appmesh.HttpHealthCheckOptions;
import software.amazon.awscdk.services.appmesh.HttpRouteSpecOptions;
import software.amazon.awscdk.services.appmesh.HttpVirtualNodeListenerOptions;
import software.amazon.awscdk.services.appmesh.Mesh;
import software.amazon.awscdk.services.appmesh.RouteBaseProps;
import software.amazon.awscdk.services.appmesh.RouteSpec;
import software.amazon.awscdk.services.appmesh.ServiceDiscovery;
import software.amazon.awscdk.services.appmesh.VirtualNode;
import software.amazon.awscdk.services.appmesh.VirtualNodeListener;
import software.amazon.awscdk.services.appmesh.VirtualRouter;
import software.amazon.awscdk.services.appmesh.VirtualRouterListener;
import software.amazon.awscdk.services.appmesh.VirtualService;
import software.amazon.awscdk.services.appmesh.VirtualServiceProvider;
import software.amazon.awscdk.services.appmesh.WeightedTarget;
import software.amazon.awscdk.services.ec2.BastionHostLinux;
import software.amazon.awscdk.services.ec2.Peer;
import software.amazon.awscdk.services.ec2.Port;
import software.amazon.awscdk.services.ec2.SecurityGroup;
import software.amazon.awscdk.services.ec2.SubnetSelection;
import software.amazon.awscdk.services.ec2.SubnetType;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ecr.assets.DockerImageAsset;
import software.amazon.awscdk.services.ecs.AppMeshProxyConfiguration;
import software.amazon.awscdk.services.ecs.AppMeshProxyConfigurationProps;
import software.amazon.awscdk.services.ecs.AwsLogDriverProps;
import software.amazon.awscdk.services.ecs.CloudMapOptions;
import software.amazon.awscdk.services.ecs.Cluster;
import software.amazon.awscdk.services.ecs.Compatibility;
import software.amazon.awscdk.services.ecs.ContainerDefinition;
import software.amazon.awscdk.services.ecs.ContainerDefinitionOptions;
import software.amazon.awscdk.services.ecs.ContainerDependency;
import software.amazon.awscdk.services.ecs.ContainerDependencyCondition;
import software.amazon.awscdk.services.ecs.ContainerImage;
import software.amazon.awscdk.services.ecs.DeploymentController;
import software.amazon.awscdk.services.ecs.DeploymentControllerType;
import software.amazon.awscdk.services.ecs.FargateService;
import software.amazon.awscdk.services.ecs.FireLensLogDriver;
import software.amazon.awscdk.services.ecs.ICluster;
import software.amazon.awscdk.services.ecs.LogDriver;
import software.amazon.awscdk.services.ecs.NetworkMode;
import software.amazon.awscdk.services.ecs.PortMapping;
import software.amazon.awscdk.services.ecs.Protocol;
import software.amazon.awscdk.services.ecs.TaskDefinition;
import software.amazon.awscdk.services.ecs.Ulimit;
import software.amazon.awscdk.services.ecs.UlimitName;
import software.amazon.awscdk.services.iam.ManagedPolicy;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.ServicePrincipal;
import software.amazon.awscdk.services.logs.ILogGroup;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.logs.RetentionDays;
import software.amazon.awscdk.services.servicediscovery.DnsRecordType;
import software.amazon.awscdk.services.servicediscovery.IPrivateDnsNamespace;
import software.amazon.awscdk.services.servicediscovery.IService;
import software.amazon.awscdk.services.servicediscovery.PrivateDnsNamespace;

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
                    
        APP_NAME   =   props.getAppName();
        DOMAIN_NAME=   props.getDomainName();
        ACCOUNT    =   props.getEnv().getAccount();
        REGION     =   props.getEnv().getRegion();        
        final String SVC_A_NAME =   APP_NAME+"-svc";
        final String SVC_B_NAME =   APP_NAME+"-afternoon";
        final String SVC_UI_NAME=   APP_NAME+"-ui";
        ROUTER_SVC_NAME         =   SVC_A_NAME+"."+DOMAIN_NAME;

        DockerImageAsset   morningImageAsset    =   props.getImageMorningService();
        DockerImageAsset   afternoonImageAsset  =   props.getImageAfternoonService();
        DockerImageAsset   uiImageAsset          =   props.getImageUIService();

        meshName   =   APP_NAME;
        taskRole   =   Role.Builder.create(this, APP_NAME+"-ecsTaskRole")
                            .assumedBy(ServicePrincipal.Builder.create("ecs-tasks.amazonaws.com").build())
                            .managedPolicies(Arrays.asList(
                                ManagedPolicy.fromAwsManagedPolicyName("CloudWatchFullAccess"), 
                                ManagedPolicy.fromAwsManagedPolicyName("AWSXRayDaemonWriteAccess"), 
                                ManagedPolicy.fromAwsManagedPolicyName("AWSAppMeshEnvoyAccess"), 
                                ManagedPolicy.fromAwsManagedPolicyName("CloudWatchAgentServerPolicy")))
                            .build();

        taskExecutionRole =   Role.Builder.create(this, APP_NAME+"-ecsExecutionRole")
                            .assumedBy(ServicePrincipal.Builder.create("ecs-tasks.amazonaws.com").build())
                            .managedPolicies(Arrays.asList(
                                ManagedPolicy.fromManagedPolicyArn(this, "ecsTaskExecutionManagedPolicy", "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"),
                                ManagedPolicy.fromAwsManagedPolicyName("CloudWatchAgentServerPolicy")
                            ))
                            .build();

        morningImageAsset.getRepository().grantPull(taskExecutionRole);
        afternoonImageAsset.getRepository().grantPull(taskExecutionRole);
        uiImageAsset.getRepository().grantPull(taskExecutionRole);

        // Vpc.fromVpcAttributes(this, "my-id", VpcAttributes.builder().vpcId(vpcId))
        // IVpc vpc = Vpc.fromLookup(this, APP_NAME+"-vpc", VpcLookupOptions.builder().vpcId("vpc-04b6bfe08aa121cac").build());
        //TODO expose this CIDR and the IP ADDRESS of the dummy-service
        Vpc vpc = Vpc.Builder.create(this, APP_NAME+"-vpc")
        .cidr("10.0.50.0/24")
        .maxAzs(1)
        .enableDnsHostnames(Boolean.TRUE)
        .enableDnsSupport(Boolean.TRUE)
        // .natGateways(1)
        // .subnetConfiguration(Arrays.asList(SubnetConfiguration.builder().name("subnet-public-1").subnetType(SubnetType.PUBLIC).cidrMask(26).build()))
        .build();        
        
        PrivateDnsNamespace namespace = PrivateDnsNamespace.Builder.create(this, "example-namespace")
        .vpc(vpc)
        .name(DOMAIN_NAME)
        .build();   

        //https://docs.aws.amazon.com/app-mesh/latest/userguide/troubleshooting-connectivity.html #Unable to resolve DNS name for a virtual service
        // Service serviceA = Service.Builder.create(this, APP_NAME+"-svc-dummy")
        // .namespace(namespace)
        // .name( SVC_A_NAME )
        // .dnsRecordType(DnsRecordType.A)
        // .dnsTtl(Duration.seconds(60))
        // .customHealthCheck(HealthCheckCustomConfig.builder().failureThreshold(2).build())
        // .description("Backend service for "+APP_NAME)        
        // .build();        

        SecurityGroup sg    =   SecurityGroup.Builder.create(this, APP_NAME+"-sg").vpc(vpc).allowAllOutbound(Boolean.TRUE).build();
        sg.addIngressRule(Peer.anyIpv4(), Port.allTcp());
        sg.addIngressRule(Peer.anyIpv4(), Port.allUdp());
        // ICluster cluster = Cluster.fromClusterAttributes(this, "my-test-cluster", ClusterAttributes.builder().vpc(vpc).securityGroups(Arrays.asList(sg)).clusterName("my-test-cluster").build());
        Cluster cluster = Cluster.Builder.create(this, APP_NAME+"-cluster")
                                        .vpc(vpc)
                                        .clusterName(APP_NAME)
                                        .containerInsights(Boolean.TRUE)
                                        .build();
        
        BastionHostLinux.Builder.create(this, "bastion-test").vpc(vpc).subnetSelection(SubnetSelection.builder().subnetType(SubnetType.PUBLIC).build()).securityGroup(sg).instanceName(APP_NAME+"-bastion").build();


        FargateService  morningFargate      =   createFargateService( cluster, namespace, morningImageAsset,  SVC_A_NAME );
        FargateService  afternoonFargate    =   createFargateService( cluster, namespace, afternoonImageAsset, SVC_B_NAME);
        FargateService  greetingUIFargate   =   createFargateService( cluster, namespace, uiImageAsset, SVC_UI_NAME);


        IService greetingMorning    =   morningFargate.getCloudMapService();
        IService greetingAfternoon  =   afternoonFargate.getCloudMapService();
        IService greetingUI         =   greetingUIFargate.getCloudMapService();

        //we create the namespace and the services and ECS and AppMesh's virtual node will reference them


        //mesh 
        Mesh mesh = Mesh.Builder.create(this, "AppMesh")
        .meshName(meshName)
        .build();

        //virtual node
        VirtualNode morningVirtualNode      = createVirtualNode(mesh, greetingMorning, SVC_A_NAME);
        VirtualNode afternoonVirtualNode    = createVirtualNode(mesh, greetingAfternoon, SVC_B_NAME);
        VirtualNode uiVirtualNode           = createVirtualNode(mesh, greetingUI, SVC_UI_NAME);

        // VirtualService morningVS   =   createVirtualService(mesh, namespace, SVC_A_NAME , VirtualServiceProvider.virtualNode(morningVirtualNode));
        // VirtualService afternoonVS =   createVirtualService(mesh, namespace, SVC_B_NAME , VirtualServiceProvider.virtualNode(afternoonVirtualNode));

        //virtual router
        VirtualRouter greetingVR    =   VirtualRouter.Builder.create(this, APP_NAME+"-vr")
                                                    .mesh(mesh)
                                                    .virtualRouterName(APP_NAME+"-vr")
                                                    .listeners(Arrays.asList(VirtualRouterListener.http(ROUTER_PORT)))
                                                    .build();

        greetingVR.addRoute(SVC_A_NAME+"-route", RouteBaseProps.builder()
                                                .routeName(SVC_A_NAME+"-route")
                                                .routeSpec(RouteSpec
                                                            .http(HttpRouteSpecOptions
                                                                    .builder()
                                                                    .weightedTargets(Arrays.asList(
                                                                                    WeightedTarget.builder().virtualNode(morningVirtualNode).weight(1).build(),
                                                                                    WeightedTarget.builder().virtualNode(afternoonVirtualNode).weight(2).build()
                                                                                    ))
                                                                    .build()))
                                                .build());

        //virtual service
        VirtualService greetingVS   =  createVirtualService(mesh, namespace, SVC_A_NAME, VirtualServiceProvider.virtualRouter(greetingVR));
        
        // serviceA.registerIpInstance(APP_NAME+"svc-ip-dummy",IpInstanceBaseProps.builder().ipv4("10.0.50.10").build());        
        uiVirtualNode.addBackend(Backend.virtualService(greetingVS));
        VirtualService greetingUIVS    =   createVirtualService(mesh, namespace, SVC_UI_NAME,VirtualServiceProvider.virtualNode(uiVirtualNode));

        // createFargateService( cluster, namespace, morningImageAsset,  SVC_A_NAME ).associateCloudMapService(AssociateCloudMapServiceOptions.builder().service(serviceA).build());
        // createFargateService( cluster, namespace, afternoonImageAsset, SVC_B_NAME).associateCloudMapService(AssociateCloudMapServiceOptions.builder().service(serviceB).build());
        // createFargateService( cluster, namespace, uiImageAsset, SVC_UI_NAME).associateCloudMapService(AssociateCloudMapServiceOptions.builder().service(serviceUI).build());


        // VirtualService greetingUIVS   =   VirtualService.Builder.create(this, APP_NAME+"ui-vs")
        //                                     .virtualServiceName(APP_NAME+"-ui."+DOMAIN_NAME)
        //                                     .virtualServiceProvider(VirtualServiceProvider.virtualNode(uiVirtualNode))
        //                                     .build();
        //virtual gateway
        //ECS + Fargate Cluster + Tasks (3)

    }

    private VirtualNode createVirtualNode(Mesh mesh, IService service, String serviceName){
       return VirtualNode.Builder.create(this, serviceName+"-vn")
        .mesh(mesh)
        .virtualNodeName(serviceName+"-vn")
        // .serviceDiscovery(ServiceDiscovery.cloudMap(service))
        .serviceDiscovery(ServiceDiscovery.dns(serviceName+"."+DOMAIN_NAME)) //if you want to switch from cloudmap to dns for discovery change it here and comment the cloudMapOptions inside the FargateService
        .accessLog(AccessLog.fromFilePath("/dev/stdout"))
        .listeners(Arrays.asList(VirtualNodeListener
                                .http(HttpVirtualNodeListenerOptions.builder()
                                    .port(8080)
                                    .healthCheck(HealthCheck.http(HttpHealthCheckOptions.builder().healthyThreshold(2).unhealthyThreshold(2).timeout(Duration.millis(2000)).interval(Duration.millis(5000)).path("HealthCheck").build())).build()   ))).build();
    }

    private VirtualService createVirtualService(Mesh mesh, PrivateDnsNamespace namespace, String serviceName, VirtualServiceProvider provider){// VirtualNode vNode){
        // System.out.println("Creating AppMesh Service: "+(serviceName+"."+namespace.getNamespaceName()));
        return VirtualService.Builder.create(this, serviceName+"-mesh-vs")
                        .virtualServiceName(serviceName+"."+namespace.getNamespaceName())
                        .virtualServiceProvider(provider)
                        .build();
    }
    // private Service createService(PrivateDnsNamespace namespace, String serviceName){

    //     return namespace.createService(serviceName, DnsServiceProps.builder()
    //     .name(serviceName)
    //     .description("This is the "+APP_NAME+" service from the "+DOMAIN_NAME+" company")
    //     .routingPolicy(RoutingPolicy.MULTIVALUE)
    //     .dnsRecordType(DnsRecordType.A)
    //     .dnsTtl(Duration.seconds(300))
    //     .customHealthCheck(HealthCheckCustomConfig.builder().failureThreshold(2).build())
    //     .build());
    // }    

    private FargateService createFargateService(ICluster cluster, IPrivateDnsNamespace namespace, DockerImageAsset appContainer, String serviceName){

        SecurityGroup sg    =   SecurityGroup.Builder.create(this, serviceName+"-sg").vpc(cluster.getVpc()).allowAllOutbound(Boolean.TRUE).build();
        sg.addIngressRule(Peer.anyIpv4(), Port.allTcp());
        sg.addIngressRule(Peer.anyIpv4(), Port.allUdp());
        

        return FargateService.Builder.create(this, serviceName+"-fargateSvc")
        .desiredCount(1)
        .cluster(cluster)
        .serviceName(serviceName)
        .deploymentController(DeploymentController.builder().type(DeploymentControllerType.ECS).build())
        // .circuitBreaker(DeploymentCircuitBreaker.builder().rollback(Boolean.TRUE).build())
        .securityGroups(Arrays.asList(sg))
        .cloudMapOptions(CloudMapOptions.builder()
            .cloudMapNamespace(namespace)
            .name(serviceName)
            .dnsRecordType(DnsRecordType.A)
            .failureThreshold(5)
            .dnsTtl(Duration.seconds(60)).build())
        .taskDefinition(createECSTask(cluster, appContainer, new HashMap<String,String>(), serviceName))
        .build();
    }

    private TaskDefinition createECSTask(ICluster cluster, DockerImageAsset appContainer, Map<String, String> env, String serviceName){

        if( serviceName.contains("ui") ){
            System.out.println("UI is acessing the following backend service..."+ROUTER_SVC_NAME+":"+ROUTER_PORT);
            env.put("GREETING_SERVICE", ROUTER_SVC_NAME+":"+ROUTER_PORT);
            env.put("GREETING_SERVICE_PROTO", "http");
        }
        //if appContainer == null create only a task for the envoy proxy, used to implement the appmesh gateway
        TaskDefinition taskDef =    TaskDefinition.Builder.create(this, serviceName+"-ecsTaskDef")
        .taskRole(this.taskRole)
        .executionRole(this.taskExecutionRole)
        .networkMode(NetworkMode.AWS_VPC)
        .cpu("1024")
        .memoryMiB("2048")
        .family(serviceName)
        .compatibility(Compatibility.FARGATE)
        .proxyConfiguration(AppMeshProxyConfiguration.Builder.create()
            .containerName("envoy")
            .properties(AppMeshProxyConfigurationProps.builder()
                .appPorts(Arrays.asList(8080))
                .proxyIngressPort(15000)
                .proxyEgressPort(15001)
                .ignoredUid(1337)
                .egressIgnoredIPs(Arrays.asList("169.254.170.2", "169.254.169.254"))
                .egressIgnoredPorts(Arrays.asList(443))
                .build())
            .build())
        .build();

        ILogGroup logGroup=  LogGroup.fromLogGroupName(this, APP_NAME+"-"+serviceName+"-logGroup", APP_NAME);
        if( logGroup == null ){
            System.out.println("Creating log group..."+APP_NAME);
            logGroup    =   LogGroup.Builder.create(this, serviceName+"-logGroup").logGroupName(APP_NAME).retention(RetentionDays.ONE_MONTH).build();
        }else{
            System.out.println("Reusing log group "+APP_NAME);
        }

        //adding envoy
        ContainerDefinition envoyContainer  =   taskDef.addContainer(serviceName+"-envoy", ContainerDefinitionOptions.builder()
        .containerName("envoy")
        // .image(ContainerImage.fromRegistry("840364872350.dkr.ecr."+REGION+".amazonaws.com/aws-appmesh-envoy:v1.15.1.0-prod"))
        .image(ContainerImage.fromRegistry("public.ecr.aws/appmesh/aws-appmesh-envoy:v1.19.1.1-prod"))
        .essential(Boolean.TRUE)
        .memoryReservationMiB(256)
        .cpu(512)
        .memoryLimitMiB(512)
        .user("1337")
        .environment(new HashMap<String, String>() {{
            //put("APPMESH_VIRTUAL_NODE_NAME", "mesh/"+meshName+"/virtualNode/"+serviceName+"-vn");            
            put("APPMESH_RESOURCE_ARN", "arn:aws:appmesh:"+REGION+":"+ACCOUNT+":mesh/"+APP_NAME+"/virtualNode/"+serviceName+"-vn");
            put("REGION", REGION);
            put("ENVOY_LOG_LEVEL", "debug");
            put("ENABLE_ENVOY_XRAY_TRACING", "1");
            put("ENABLE_ENVOY_STATS_TAGS", "1");
            put("ENABLE_ENVOY_DOG_STATSD", "1");
        }})
        .logging(FireLensLogDriver.Builder.create().options(new HashMap<String, String>() {{
            put("Name", "cloudwatch");
            put("region", REGION);
            put("log_group_name", APP_NAME);
            // put("auto_create_group", "true");
            put("log_stream_prefix", serviceName+"/");
        }}).build())
        .healthCheck(software.amazon.awscdk.services.ecs.HealthCheck.builder().command(Arrays.asList("CMD-SHELL", "curl -s http://localhost:9901/server_info | grep state | grep -q LIVE")).interval(Duration.seconds(5)).timeout(Duration.seconds(2)).retries(3).startPeriod(Duration.seconds(10)).build())
        .portMappings(Arrays.asList(
            PortMapping.builder().containerPort(15000).hostPort(15000).protocol(Protocol.TCP).build(), 
            PortMapping.builder().containerPort(15001).hostPort(15001).protocol(Protocol.TCP).build(), 
            PortMapping.builder().containerPort(9901).hostPort(9901).protocol(Protocol.TCP).build()))                
        .build());

        envoyContainer.addUlimits(Ulimit.builder().hardLimit(15000).softLimit(15000).name(UlimitName.NOFILE).build());

        //adding logrouter
        // https://github.com/aws/aws-for-fluent-bit
        // taskDef.addFirelensLogRouter( serviceName+"-log-router", FirelensLogRouterDefinitionOptions.builder()
        // .containerName("log_router")
        // .memoryReservationMiB(256)
        // .memoryLimitMiB(512)
        // .image(ContainerImage.fromRegistry("906394416424.dkr.ecr."+REGION+".amazonaws.com/aws-for-fluent-bit:latest") )
        // .firelensConfig(FirelensConfig.builder().type(FirelensLogRouterType.FLUENTBIT).options(FirelensOptions.builder().enableEcsLogMetadata(Boolean.TRUE).configFileType(FirelensConfigFileType.FILE).configFileValue("/fluent-bit/conf/parse_envoy.conf").build()).build())
        // .build());

        //adding xray
        taskDef.addContainer(serviceName+"-x-ray", ContainerDefinitionOptions.builder()
        .containerName("x-ray")
        .image(ContainerImage.fromRegistry("public.ecr.aws/xray/aws-xray-daemon:latest"))//Repository.fromRepositoryArn(this, serviceDiscovery.getServiceName()+"log-router", "906394416424.dkr.ecr."+REGION+".amazonaws.com/aws-for-fluent-bit"), "2.21.0"))
        .essential(Boolean.TRUE)
        .user("1337")
        // .healthCheck(software.amazon.awscdk.services.ecs.HealthCheck.builder().command(Arrays.asList("CMD-SHELL", "timeout 1 /bin/bash -c \"</dev/udp/localhost/2000\"")).interval(Duration.seconds(5)).timeout(Duration.seconds(2)).retries(3).startPeriod(Duration.seconds(10)).build())
        .portMappings(Arrays.asList(PortMapping.builder().containerPort(2000).hostPort(2000).protocol(Protocol.UDP).build()))
        .logging(LogDriver.awsLogs(AwsLogDriverProps.builder()
            .logGroup(logGroup)
            .streamPrefix(serviceName).build()))
        .build());

        //adding application container
        ContainerDefinition app = taskDef.addContainer( serviceName+"-app", ContainerDefinitionOptions.builder()
        .containerName(serviceName)
        .memoryReservationMiB(256)
        .memoryLimitMiB(512)
        .image(ContainerImage.fromDockerImageAsset(appContainer))
        // .memoryReservationMiB(1024)
        .essential(Boolean.TRUE)
        .healthCheck(software.amazon.awscdk.services.ecs.HealthCheck.builder().command(Arrays.asList("CMD-SHELL", "curl -s http://localhost:8080/Luiz | grep name")).interval(Duration.seconds(5)).timeout(Duration.seconds(2)).retries(3).startPeriod(Duration.seconds(10)).build())
        .portMappings(Arrays.asList(
            PortMapping.builder().containerPort(8080).hostPort(8080).protocol(Protocol.TCP).build()))
        .logging(LogDriver.awsLogs(AwsLogDriverProps.builder()
            .logGroup(logGroup)
            .streamPrefix(serviceName+"/app").build()))            
        .environment(env)
        .build());

        app.addContainerDependencies(ContainerDependency.builder().condition(ContainerDependencyCondition.HEALTHY).container(envoyContainer).build());
        
        return taskDef;
    }

    public static class ControlPlaneProps implements StackProps {


        private String appName = null;
        private Environment environment = null;
        private Boolean terminationProtection = Boolean.FALSE;
        private DockerImageAsset imageMorningService = null;
        private DockerImageAsset imageAfternoonService = null;
        private DockerImageAsset imageUI = null;
        private String domainName   =   null;
        
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
        
        public DockerImageAsset getImageMorningService(){
            return this.imageMorningService;
        }

        public DockerImageAsset getImageAfternoonService(){
            return this.imageAfternoonService;
        }

        public DockerImageAsset getImageUIService(){
            return this.imageUI;
        }

        public String getDomainName(){
            return this.domainName;
        }

        public ControlPlaneProps(String appName, DockerImageAsset morning, DockerImageAsset afternoon, DockerImageAsset ui, Map<String, String> tags, Boolean terminationProtection, Environment env, String domainName){

            this.appName = appName;
            this.imageMorningService = morning;
            this.imageAfternoonService = afternoon;
            if(tags!=null)
            StackProps.super.getTags().putAll(tags);
            this.terminationProtection = terminationProtection;
            this.environment = env;
            this.domainName=    domainName;
            this.imageUI = ui;
        }

        public static Builder builder(){
            return new Builder();
        }

        public static class Builder{

            private Map<String,String> tags = null;
            String appName = null;
            private Boolean terminationProtection = Boolean.FALSE;
            private DockerImageAsset morning = null;
            private DockerImageAsset afternoon = null;
            private DockerImageAsset ui = null;
            private Environment environment = null;
            private String domain   =   null;
            


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
            
            public Builder imageMorning(DockerImageAsset imageMorning){
                this.morning = imageMorning;
                return this;
            } 

            public Builder imageAfternoon(DockerImageAsset imageAfternoon){
                this.afternoon = imageAfternoon;
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

            public ControlPlaneProps build(){
                return new ControlPlaneProps(appName, morning, afternoon, ui, tags, terminationProtection, environment, domain);
            }            
        }
    }
}
