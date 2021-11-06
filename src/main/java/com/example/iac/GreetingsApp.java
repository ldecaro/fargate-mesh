package com.example.iac;

import software.amazon.awscdk.core.App;
import software.amazon.awscdk.core.Construct;

public class GreetingsApp {
    

static class Greetings extends Construct{

    Greetings(Construct scope, String id, String appName){
        super(scope, id);

        // Containers/Services/Data Plane
        DataPlane dp    =   new DataPlane(this, appName+"-dp", DataPlane.DataPlaneProps.builder()
                                            .appName(appName)
                                            .terminationProtection(Boolean.FALSE)
                                            .build());

        // Network/Infrastructure/ControlPlane
        ControlPlane cp =   new ControlPlane(this, appName+"-cp", ControlPlane.ControlPlaneProps.builder()
                                            .appName(appName)
                                            .domain("example.com")
                                            .imageMorning(dp.getMorningImageAsset())
                                            .imageAfternoon(dp.getAfternoonImageAsset())
                                            .imageUI(dp.getUiImageAsset())
                                            .terminationProtection(Boolean.FALSE)
                                            .build());        
        // Monitoring...
        cp.addDependency(dp);

    }
}

    public static void main(String args[]){

        App greetingsApp = new App();
        new Greetings(greetingsApp, "greetings", "greetings");
        greetingsApp.synth();
    }
}
