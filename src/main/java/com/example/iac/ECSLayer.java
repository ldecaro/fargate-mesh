package com.example.iac;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import com.example.iac.ControlPlane.ControlPlaneProps;

import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Duration;
import software.amazon.awscdk.services.ec2.Peer;
import software.amazon.awscdk.services.ec2.Port;
import software.amazon.awscdk.services.ec2.SecurityGroup;
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
import software.amazon.awscdk.services.servicediscovery.INamespace;
import software.amazon.awscdk.services.servicediscovery.PrivateDnsNamespace;

/**
 * Creates the ECS Containers
 */
public class ECSLayer extends Construct {
    
    String routerService         =   null;
    final int ROUTER_SVC_PORT    =   8080;
    String namespaceName    =   null;
    private FargateService v1Fargate = null;
    private FargateService v2Fargate = null;
    private FargateService uiFargate = null;
    private FargateService gwFargate = null;

    public ECSLayer(Construct scope, String id, final ControlPlaneProps props ){

        super(scope, id);
              
        final String SVC_A_NAME =   props.getAppName()+"-svc";
        final String SVC_B_NAME =   props.getAppName()+"-afternoon";
        final String SVC_UI_NAME=   props.getAppName()+"-ui";
        final String SVC_GW_NAME=   props.getAppName()+"-gw";
        this.routerService         =   SVC_A_NAME+"."+props.getDomainName();
        
        if( props.getVpc() == null ){
            props.setVpc( Vpc.Builder.create(this, props.getAppName()+"-vpc") 
            .cidr("10.0.50.0/24")
            .maxAzs(1)
            .enableDnsHostnames(Boolean.TRUE)
            .enableDnsSupport(Boolean.TRUE)
            .build());
        }

        SecurityGroup sg    =   SecurityGroup.Builder.create(this, props.getAppName()+"-sg").vpc(props.getVpc()).allowAllOutbound(Boolean.TRUE).build();
        sg.addIngressRule(Peer.anyIpv4(), Port.allTcp());
        sg.addIngressRule(Peer.anyIpv4(), Port.allUdp());

        if( props.getCluster() == null ){
            // ICluster cluster = Cluster.fromClusterAttributes(this, "my-test-cluster", ClusterAttributes.builder().vpc(vpc).securityGroups(Arrays.asList(sg)).clusterName("my-test-cluster").build());
            props.setCluster( Cluster.Builder.create(this, props.getAppName()+"-cluster")
                                            .vpc(props.getVpc())
                                            .clusterName(props.getAppName())
                                            .containerInsights(Boolean.TRUE)
                                            .build());
        }
                                        
        PrivateDnsNamespace namespace = PrivateDnsNamespace.Builder.create(this, props.getAppName()+"-namespace")
        .vpc(props.getVpc())
        .name( props.getDomainName() )
        .build();   

        this.namespaceName = namespace.getNamespaceName();

        Role taskRole = Role.Builder.create(this, props.getAppName()+"-ecsTaskRole")
        .assumedBy(ServicePrincipal.Builder.create("ecs-tasks.amazonaws.com").build())
        .managedPolicies(Arrays.asList(
            ManagedPolicy.fromAwsManagedPolicyName("CloudWatchFullAccess"), 
            ManagedPolicy.fromAwsManagedPolicyName("AWSXRayDaemonWriteAccess"), 
            ManagedPolicy.fromAwsManagedPolicyName("AWSAppMeshEnvoyAccess"), 
            ManagedPolicy.fromAwsManagedPolicyName("CloudWatchAgentServerPolicy")))
        .build();

        Role executionRole = Role.Builder.create(this, props.getAppName()+"-ecsExecutionRole")
        .assumedBy(ServicePrincipal.Builder.create("ecs-tasks.amazonaws.com").build())
        .managedPolicies(Arrays.asList(
            ManagedPolicy.fromManagedPolicyArn(this, "ecsTaskExecutionManagedPolicy", "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"),
            ManagedPolicy.fromAwsManagedPolicyName("CloudWatchAgentServerPolicy")
        )).build();

        FargateService  v1Fargate   =   createFargateService( Boolean.FALSE, props, props.getV1ImageAsset(), SVC_A_NAME, namespace, taskRole, executionRole);
        FargateService  v2Fargate   =   createFargateService( Boolean.FALSE, props, props.getV2ImageAsset(), SVC_B_NAME, namespace, taskRole, executionRole);
        FargateService  uiFargate   =   null;
        if( props.getImageUIAsset()!= null ){
           uiFargate =  createFargateService( Boolean.FALSE, props, props.getImageUIAsset(), SVC_UI_NAME, namespace, taskRole, executionRole);
        }
        FargateService  gwFargate   =   createFargateService( Boolean.TRUE, props, null, SVC_GW_NAME, namespace, taskRole, executionRole);

        props.getV1ImageAsset().getRepository().grantPull(v1Fargate.getTaskDefinition().obtainExecutionRole());
        props.getV2ImageAsset().getRepository().grantPull(v2Fargate.getTaskDefinition().obtainExecutionRole());
        props.getImageUIAsset().getRepository().grantPull(uiFargate.getTaskDefinition().obtainExecutionRole());

        this.v1Fargate = v1Fargate;
        this.v2Fargate = v2Fargate;
        this.uiFargate = uiFargate;
    }


    private FargateService createFargateService(Boolean IS_GATEWAY, ControlPlane.ControlPlaneProps props, DockerImageAsset appContainer, String serviceName, INamespace namespace, Role taskRole, Role executionRole ){


        SecurityGroup sg    =   SecurityGroup.Builder.create(this, serviceName+"-sg").vpc(props.getCluster().getVpc()).allowAllOutbound(Boolean.TRUE).build();
        sg.addIngressRule(Peer.anyIpv4(), Port.allTcp());
        sg.addIngressRule(Peer.anyIpv4(), Port.allUdp());

        return FargateService.Builder.create(this, serviceName+"-fargateSvc")
        .desiredCount(1)
        .cluster(props.getCluster())
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
        .taskDefinition(createECSTask(IS_GATEWAY, props, appContainer, new HashMap<String,String>(), serviceName, taskRole, executionRole))
        .build();
    }

    private TaskDefinition createECSTask(final boolean IS_GATEWAY, ControlPlaneProps props, DockerImageAsset appContainer, Map<String, String> env, String serviceName, Role taskRole, Role executionRole){

        if( serviceName.contains("ui") ){
            System.out.println("UI is acessing the backend service at "+routerService+":"+ROUTER_SVC_PORT);
            env.put("GREETING_SERVICE", routerService+":"+ROUTER_SVC_PORT);
            env.put("GREETING_SERVICE_PROTO", "http");
        }
        //if appContainer == null create only a task for the envoy proxy, used to implement the appmesh gateway
        TaskDefinition taskDef =    TaskDefinition.Builder.create(this, serviceName+"-ecsTaskDef")
        .taskRole(taskRole)
        .executionRole(executionRole)
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

        ILogGroup logGroup=  LogGroup.fromLogGroupName(this, props.getAppName()+"-"+serviceName+"-logGroup", props.getAppName());
        if( logGroup == null ){
            System.out.println("Creating log group..."+props.getAppName());
            logGroup    =   LogGroup.Builder.create(this, serviceName+"-logGroup").logGroupName(props.getAppName()).retention(RetentionDays.ONE_MONTH).build();
        }else{
            System.out.println("Reusing log group "+props.getAppName());
        }

        //adding envoy
        String resourceArn = null;
        if( IS_GATEWAY ){
            resourceArn = "arn:aws:appmesh:"+props.getEnv().getRegion()+":"+props.getEnv().getAccount()+":mesh/"+props.getAppName()+"/virtualGateway/"+serviceName;
        } else {
            resourceArn = "arn:aws:appmesh:"+props.getEnv().getRegion()+":"+props.getEnv().getAccount()+":mesh/"+props.getAppName()+"/virtualNode/"+serviceName+"-vn";
        }
        final String arn = resourceArn;
        
        ContainerDefinition envoyContainer  =   taskDef.addContainer(serviceName+"-envoy", ContainerDefinitionOptions.builder()
        .containerName("envoy")
        .image(ContainerImage.fromRegistry("public.ecr.aws/appmesh/aws-appmesh-envoy:v1.19.1.1-prod"))
        .essential(Boolean.TRUE)
        .memoryReservationMiB(256)
        .cpu(512)
        .memoryLimitMiB(512)
        .user("1337")
        .environment(new HashMap<String, String>() {{   
            put("APPMESH_RESOURCE_ARN", arn);
            put("REGION", props.getEnv().getRegion());
            put("ENVOY_LOG_LEVEL", "debug");
            put("ENABLE_ENVOY_XRAY_TRACING", "1");
            put("ENABLE_ENVOY_STATS_TAGS", "1");
            put("ENABLE_ENVOY_DOG_STATSD", "1");
        }})
        .logging(FireLensLogDriver.Builder.create().options(new HashMap<String, String>() {{
            put("Name", "cloudwatch");
            put("region", props.getEnv().getRegion());
            put("log_group_name", props.getAppName());
            put("log_stream_prefix", serviceName+"/");
        }}).build())
        .healthCheck(software.amazon.awscdk.services.ecs.HealthCheck.builder().command(Arrays.asList("CMD-SHELL", "curl -s http://localhost:9901/server_info | grep state | grep -q LIVE")).interval(Duration.seconds(5)).timeout(Duration.seconds(2)).retries(3).startPeriod(Duration.seconds(10)).build())
        .portMappings(Arrays.asList(
            PortMapping.builder().containerPort(15000).hostPort(15000).protocol(Protocol.TCP).build(), 
            PortMapping.builder().containerPort(15001).hostPort(15001).protocol(Protocol.TCP).build(), 
            PortMapping.builder().containerPort(9901).hostPort(9901).protocol(Protocol.TCP).build()))                
        .build());

        envoyContainer.addUlimits(Ulimit.builder().hardLimit(15000).softLimit(15000).name(UlimitName.NOFILE).build());

        //adding xray
        taskDef.addContainer(serviceName+"-x-ray", ContainerDefinitionOptions.builder()
        .containerName("x-ray")
        .image(ContainerImage.fromRegistry("public.ecr.aws/xray/aws-xray-daemon:latest"))
        .essential(Boolean.TRUE)
        .user("1337")
        // .healthCheck(software.amazon.awscdk.services.ecs.HealthCheck.builder().command(Arrays.asList("CMD-SHELL", "timeout 1 /bin/bash -c \"</dev/udp/localhost/2000\"")).interval(Duration.seconds(5)).timeout(Duration.seconds(2)).retries(3).startPeriod(Duration.seconds(10)).build())
        .portMappings(Arrays.asList(PortMapping.builder().containerPort(2000).hostPort(2000).protocol(Protocol.UDP).build()))
        .logging(LogDriver.awsLogs(AwsLogDriverProps.builder()
            .logGroup(logGroup)
            .streamPrefix(serviceName).build()))
        .build());

        if( appContainer != null ){
            //adding application container
            ContainerDefinition app = taskDef.addContainer( serviceName+"-app", ContainerDefinitionOptions.builder()
            .containerName(serviceName)
            .memoryReservationMiB(256)
            .memoryLimitMiB(512)
            .image(ContainerImage.fromDockerImageAsset(appContainer))
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
        }
        return taskDef;
    }    

    public String getNamespaceName(){
        return this.namespaceName;
    }

    public FargateService getV1Fargate() {
        return v1Fargate;
    }

    public void setV1Fargate(FargateService v1Fargate) {
        this.v1Fargate = v1Fargate;
    }

    public FargateService getV2Fargate() {
        return v2Fargate;
    }

    public void setV2Fargate(FargateService v2Fargate) {
        this.v2Fargate = v2Fargate;
    }

    public FargateService getUiFargate() {
        return uiFargate;
    }

    public void setUiFargate(FargateService uiFargate) {
        this.uiFargate = uiFargate;
    }
}