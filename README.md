# fargate-mesh
An ECS Fargate and service mesh jump-start with CDK and Java/Jersey

![Architecture](/images/architecture.png)

Ideally, each of your microservices could be built using a similar approach. This approach also prepares the field for a Canary deployment that can be implemented using a step function.

`# cdk deploy --all --require-approval never`

`# wget http://greetings-ui.example.com:8080/Visitor`

As a convenience you could add this as a product on service catalog. To accomplish this, you should execute the stack named `ServiceCatalog` (not ready yet)
