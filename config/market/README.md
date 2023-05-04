This folder contains configuration for market API to call the petstore API with market API endpoint is accessed. This demonstrates the outbound capability of the http-sidecar with modules of the sidecar in the light-4j. 

This sidecar will be deployed along with market API running locally for testing. The real deployment will be a sidecar in a K8s cluster.

To simply the local setup, it connect to the petstore API directly without the petstore API sidecar instance. So the petstore port will be 9443. 


