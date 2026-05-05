# http-sidecar Kubernetes contract

This folder documents the reusable Kubernetes contract for adding
`http-sidecar` to an API pod.

The sidecar repository owns sidecar-specific defaults and examples only. A
concrete API repository owns the actual Deployment and Service that compose the
sidecar with the backend API.

## Sidecar contract

Default image for the first `openapi-petstore` rollout:

```text
networknt/http-sidecar:2.2.1
```

Default ports:

| Port | Name | Purpose |
| --- | --- | --- |
| `9445` | `sidecar-https` | HTTPS ingress exposed through the API Service |
| `9080` | `sidecar-http` | HTTP sidecar port exposed through the API Service |

The backend API should be pod-local. For the petstore sidecar deployment, the
sidecar routes to:

```yaml
proxy.hosts: http://127.0.0.1:8080
```

## Runtime identity

For `openapi-petstore`, the sidecar runtime identity is:

```text
serviceId: com.networknt.petstore-lpsc-3.1.0
envTag: dev
```

The sidecar token must match this identity. Do not reuse the backend petstore
token for the sidecar.

## Template fragments

The files in `k8s/base` are reusable fragments for API repositories:

- `startup-configmap.yaml`: config-server startup identity for the sidecar
- `bootstrap-secret.yaml`: shared config-server bootstrap secret keys
- `sidecar-container.yaml`: container block for embedding into an API
  Deployment

These fragments are not a complete deployable application on their own.
