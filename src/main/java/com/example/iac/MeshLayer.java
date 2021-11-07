package com.example.iac;

import java.util.Arrays;

import com.example.iac.ControlPlane.ControlPlaneProps;

import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Duration;
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
import software.amazon.awscdk.services.ecs.FargateService;
import software.amazon.awscdk.services.servicediscovery.IService;

public class MeshLayer extends Construct {
    
    public MeshLayer(Construct scope, String id, FargateService v1 , FargateService v2, FargateService ui, String namespaceName, ControlPlaneProps props){

        super(scope, id);
        final String SVC_A_NAME =   props.getAppName()+"-svc";
        final String SVC_B_NAME =   props.getAppName()+"-afternoon";
        final String SVC_UI_NAME=   props.getAppName()+"-ui";

        //mesh 
        Mesh mesh = Mesh.Builder.create(this, "AppMesh")
        .meshName(props.getAppName())
        .build();

        //virtual node
        VirtualNode morningVirtualNode      = createVirtualNode(mesh, v1.getCloudMapService() , SVC_A_NAME, props.getDomainName());
        VirtualNode afternoonVirtualNode    = createVirtualNode(mesh, v2.getCloudMapService(), SVC_B_NAME, props.getDomainName());
        VirtualNode uiVirtualNode           = createVirtualNode(mesh, ui.getCloudMapService(), SVC_UI_NAME, props.getDomainName());        
              
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
                                                                                    WeightedTarget.builder().virtualNode(morningVirtualNode).weight(1).build(),
                                                                                    WeightedTarget.builder().virtualNode(afternoonVirtualNode).weight(2).build()
                                                                                    ))
                                                                    .build()))
                                                .build());

        //virtual service       
        uiVirtualNode.addBackend(Backend.virtualService(createVirtualService(mesh, namespaceName , SVC_A_NAME, VirtualServiceProvider.virtualRouter(greetingVR))));
        createVirtualService(mesh, namespaceName, SVC_UI_NAME,VirtualServiceProvider.virtualNode(uiVirtualNode));
    }

    private VirtualNode createVirtualNode(Mesh mesh, IService service, String serviceName, String domainName){
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

     private VirtualService createVirtualService(Mesh mesh, String namespaceName, String serviceName, VirtualServiceProvider provider){// VirtualNode vNode){
        // System.out.println("Creating AppMesh Service: "+(serviceName+"."+namespace.getNamespaceName()));
        return VirtualService.Builder.create(this, serviceName+"-mesh-vs")
                        .virtualServiceName(serviceName+"."+namespaceName)
                        .virtualServiceProvider(provider)
                        .build();
    }     
}
