# Infrastructure Implementation (Point 7)

## 1. Complete Infrastructure Architecture
The runtime infrastructure is modeled to support a polyglot microservices application consisting of:
- **Stateless Services (Custom Logic):** `vote` (Java/Spring), `result` (Node.js/Express), `worker` (Go).
- **Stateful Backing Services:** `postgresql` (Database), `kafka` (Message Broker).

We support two distinct runtimes: a `docker-compose.yml` for rapid local development, and a hardened Kubernetes abstraction layer for staging/production (or cloud-dev via Okteto).

## 2. Containerization Strategy
Our Dockerfiles follow high-performance, secure paradigms:
- **Vote & Worker:** Implemented **multi-stage builds**. The first stage compiles the code with all heavy toolchains (Maven, Go SDK), and the final stage copies only the built binary/JAR. The `worker` image utilizes a `scratch` base image, producing a microscopic, highly secure footprint with zero OS vulnerabilities.
- **Result:** Uses `tini` as an init process (`PID 1`) to ensure signals (like `SIGTERM`) are properly handled by Node.js, preventing zombie processes and ensuring graceful shutdowns.

## 3. Kubernetes Deployment Model (Kustomize + Helm)
We made a conscious architectural decision to split our IaC management:
1. **Helm (`infrastructure/`):** Used strictly for third-party, stateful services (Postgres, Kafka). Helm excels at managing complex, templated third-party deployments.
2. **Kustomize (`k8s/`):** Used as our **Single Source of Truth** for our proprietary microservices. Kustomize enables patch-based environment management (dev/staging/prod) without the complex templating overhead of Helm, preserving the readability of plain YAML.

## 4. Security Hardening (Zero Trust)
The Kubernetes manifests do not just "run" the apps; they run them *defensively*:
- **Pod Security Contexts:** Every deployment forces containers to run as a non-root user (`runAsUser: 10001` or `1001`), completely mitigating host-takeover risks if a container escape vulnerability is found.
- **Immutable Filesystems:** Microservices run with `readOnlyRootFilesystem: true`. If an attacker breaches the app, they cannot download scripts or alter binaries. We mount an `emptyDir` at `/tmp` only where strictly required.
- **Seccomp & Capabilities:** `allowPrivilegeEscalation` is disabled, all Linux capabilities are explicitly dropped (`drop: - ALL`), and the `RuntimeDefault` seccomp profile is applied.
- **Network Policies:** A `NetworkPolicy` establishing a default-deny posture enables a "Zero Trust" internal network, meaning pods only communicate on explicitly allowed ports.
- **Decoupled Secrets:** Database credentials are not hardcoded in deployments; they are injected securely via `secretKeyRef`.

## 5. Persistence and Reliability
- **Probes:** Every service has `livenessProbe` and `readinessProbe` configured. The cluster will logically route traffic away from unhealthy instances and restart crashed pods automatically.
- **Data Persistence:** We implemented a `PersistentVolumeClaim` (PVC) for PostgreSQL. This guarantees that voting data outlives pod lifecycles, surviving cluster upgrades or pod eviction.

## 6. Unified Okteto Runtime
The cloud-native development environment (`okteto.yml`) bridges the gap between dev and production. It has been strictly aligned to deploy using `kubectl apply -k k8s`, guaranteeing that developers test against the exact same hardened SecurityContexts and NetworkPolicies that will be used in Production.

## 7. Technical Defense Highlights
When presenting this architecture, emphasize:
> "Our infrastructure is designed for a Zero-Trust environment. We didn't just deploy the pods; we stripped them of root access, made their disks immutable, and locked down the network. Furthermore, by orchestrating apps with Kustomize and third-party DBs with Helm, we demonstrate a nuanced, Senior-level understanding of when to use which IaC tool to maximize maintainability."
