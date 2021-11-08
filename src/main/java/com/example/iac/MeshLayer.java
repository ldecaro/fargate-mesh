package com.example.iac;

import java.util.Arrays;

import com.example.iac.ControlPlane.ControlPlaneProps;

import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Duration;
import software.amazon.awscdk.services.appmesh.AccessLog;
import software.amazon.awscdk.services.appmesh.Backend;
import software.amazon.awscdk.services.appmesh.GatewayRouteBaseProps;
import software.amazon.awscdk.services.appmesh.GatewayRouteSpec;
import software.amazon.awscdk.services.appmesh.HealthCheck;
import software.amazon.awscdk.services.appmesh.HttpConnectionPool;
import software.amazon.awscdk.services.appmesh.HttpGatewayListenerOptions;
import software.amazon.awscdk.services.appmesh.HttpGatewayRouteMatch;
import software.amazon.awscdk.services.appmesh.HttpGatewayRoutePathMatch;
import software.amazon.awscdk.services.appmesh.HttpGatewayRouteSpecOptions;
import software.amazon.awscdk.services.appmesh.HttpHealthCheckOptions;
import software.amazon.awscdk.services.appmesh.HttpRouteSpecOptions;
import software.amazon.awscdk.services.appmesh.HttpVirtualNodeListenerOptions;
import software.amazon.awscdk.services.appmesh.Mesh;
import software.amazon.awscdk.services.appmesh.RouteBaseProps;
import software.amazon.awscdk.services.appmesh.RouteSpec;
import software.amazon.awscdk.services.appmesh.ServiceDiscovery;
import software.amazon.awscdk.services.appmesh.VirtualGateway;
import software.amazon.awscdk.services.appmesh.VirtualGatewayListener;
import software.amazon.awscdk.services.appmesh.VirtualNode;
import software.amazon.awscdk.services.appmesh.VirtualNodeListener;
import software.amazon.awscdk.services.appmesh.VirtualRouter;
import software.amazon.awscdk.services.appmesh.VirtualRouterListener;
import software.amazon.awscdk.services.appmesh.VirtualService;
import software.amazon.awscdk.services.appmesh.VirtualServiceProvider;
import software.amazon.awscdk.services.appmesh.WeightedTarget;
import software.amazon.awscdk.services.ecs.FargateService;
import software.amazon.awscdk.services.servicediscovery.IService;

public class MeshLayer extends Construct {
    
    public MeshLayer(Construct scope, String id, FargateService v1 , FargateService v2, FargateService ui, String namespaceName, ControlPlaneProps props){

        super(scope, id);
        final String SVC_A_NAME =   props.getAppName()+"-svc";
        final String SVC_B_NAME =   props.getAppName()+"-afternoon";
        final String SVC_UI_NAME=   props.getAppName()+"-ui";
        final String SVC_GW_NAME=   props.getAppName()+"-gw";

        //mesh 
        Mesh mesh = Mesh.Builder.create(this, "AppMesh")
        .meshName(props.getAppName())
        .build();

        //virtual node
        VirtualNode v1Node  = createVirtualNode(mesh, v1.getCloudMapService() , SVC_A_NAME, props.getDomainName());
        VirtualNode v2Node  = createVirtualNode(mesh, v2.getCloudMapService(), SVC_B_NAME, props.getDomainName());
        VirtualNode uiNode  = null;
        if( ui != null )
            uiNode  = createVirtualNode(mesh, ui.getCloudMapService(), SVC_UI_NAME, props.getDomainName());        

        //virtual router
        VirtualRouter greetingVR    =   VirtualRouter.Builder.create(this, props.getAppName()+"-vr")
                                                    .mesh(mesh)
                                                    .virtualRouterName(props.getAppName()+"-vr")
                                                    .listeners(Arrays.asList(VirtualRouterListener.http(8080)))
                                                    .build();

        greetingVR.addRoute(SVC_A_NAME+"-route", RouteBaseProps.builder()
                                                .routeName(SVC_A_NAME+"-route")
                                                .routeSpec(RouteSpec
                                                            .http(HttpRouteSpecOptions
                                                                    .builder()
                                                                    .weightedTargets(Arrays.asList(
                                                                                    WeightedTarget.builder().virtualNode(v1Node).weight(1).build(),
                                                                                    WeightedTarget.builder().virtualNode(v2Node).weight(2).build()
                                                                                    ))
                                                                    .build()))
                                                .build());

        //virtual service     
        VirtualService backend  =   createVirtualService(mesh, namespaceName , SVC_A_NAME, VirtualServiceProvider.virtualRouter(greetingVR));
        if( uiNode != null ){
            uiNode.addBackend(Backend.virtualService(backend));
            VirtualService vService =    createVirtualService(mesh, namespaceName, SVC_UI_NAME,VirtualServiceProvider.virtualNode(uiNode));
            createVirtualGateway(mesh, SVC_GW_NAME,  vService);
        }else{
            createVirtualGateway(mesh, SVC_GW_NAME, backend);
        }
    }

    private VirtualNode createVirtualNode(Mesh mesh, IService service, String serviceName, String domainName){

        if( service == null ){
            return null;
        }

        return VirtualNode.Builder.create(this, serviceName+"-vn")
         .mesh(mesh)
         .virtualNodeName(serviceName+"-vn")
         // .serviceDiscovery(ServiceDiscovery.cloudMap(service))
         .serviceDiscovery(ServiceDiscovery.dns(serviceName+"."+domainName)) //if you want to switch from cloudmap to dns for discovery change it here and comment the cloudMapOptions inside the FargateService
         .accessLog(AccessLog.fromFilePath("/dev/stdout"))
         .listeners(Arrays.asList(VirtualNodeListener
                                 .http(HttpVirtualNodeListenerOptions.builder()
                                     .port(8080)
                                     .healthCheck(HealthCheck.http(HttpHealthCheckOptions.builder().healthyThreshold(2).unhealthyThreshold(2).timeout(Duration.millis(2000)).interval(Duration.millis(5000)).path("HealthCheck").build())).build()   ))).build();
     }

     private VirtualService createVirtualService(final Mesh mesh, final String namespaceName, final String serviceName, final VirtualServiceProvider provider){
        return VirtualService.Builder.create(this, serviceName+"-mesh-vs")
                        .virtualServiceName(serviceName+"."+namespaceName)
                        .virtualServiceProvider(provider)
                        .build();
     }

    private VirtualGateway createVirtualGateway(final Mesh mesh,  final String serviceName, VirtualService service ){

        VirtualGateway gw =  VirtualGateway.Builder.create(this, serviceName)
        .virtualGatewayName(serviceName)
        .accessLog(AccessLog.fromFilePath("/dev/stdout"))
        .listeners(Arrays.asList(VirtualGatewayListener.http(
            HttpGatewayListenerOptions.builder()
            .port(8080)
            .connectionPool(HttpConnectionPool.builder().maxConnections(1024).maxPendingRequests(1024).build())
            .healthCheck(HealthCheck.http(HttpHealthCheckOptions.builder()
                .healthyThreshold(5)
                .unhealthyThreshold(3)                
                .interval(Duration.seconds(30))
                .path("/")
                .timeout(Duration.millis(5000))
                .build())).build())
            ))
        .mesh(mesh)
        .build();

        gw.addGatewayRoute(serviceName+"-default", GatewayRouteBaseProps.builder()
        .gatewayRouteName(mesh.getMeshName()+"-default")
        .routeSpec(GatewayRouteSpec.http(HttpGatewayRouteSpecOptions.builder()
            .match(HttpGatewayRouteMatch.builder()
                .path(HttpGatewayRoutePathMatch
                .startsWith("/")).build())
            .routeTarget(service)
            .build()))
        .build());

        return gw;
    }
}
