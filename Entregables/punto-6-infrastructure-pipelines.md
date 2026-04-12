# Infrastructure Pipelines (Point 6)

## 1. Purpose and Separation of Concerns
Infrastructure as Code (IaC) is software and must be tested as rigorously as application logic. However, infrastructure changes have different blast radiuses and lifecycles than application code. 

To reflect this, we created a dedicated `infrastructure.yml` pipeline. This pipeline is completely decoupled from the CI/CD application pipelines. 

**Trigger Strategy:** It runs exclusively when changes are made to structural files:
- Kubernetes manifests (`k8s/**`)
- Helm charts (`infrastructure/**`)
- `docker-compose.yml`
- Root config files (`okteto.yml`, `.yamllint.yaml`)

## 2. Validation & Quality Gates
The infrastructure pipeline implements multiple layers of preventative validation:

### A. Yamllint (Syntax Governance)
We use `yamllint` with a custom `.yamllint.yaml` ruleset. YAML relies heavily on whitespaces, making it prone to subtle, hard-to-debug errors. The linter enforces consistent indentation and structure, ensuring configuration files are machine-readable and developer-friendly. We scoped the linter to explicitly ignore `node_modules` and Helm `templates/` (since Helm uses Go-templating which breaks standard YAML parsers).

### B. Hadolint (Container Best Practices)
`hadolint` parses all `Dockerfile`s across the repository to enforce Docker community file best practices. It prevents anti-patterns such as running as root or missing cache-busting mechanisms.

### C. Docker Compose Validation
We run `docker compose config -q` to validate the structural integrity of the local runtime environment.

### D. Helm Chart Validation
We use the native `helm lint` command to validate the syntax and values structure of our supporting stateful services (Kafka, PostgreSQL). The charts are then packaged as artifacts for potential registry publishing.

## 3. IaC Security Scanning (Checkov)
The crown jewel of the infrastructure pipeline is **Checkov** (`bridgecrewio/checkov-action`). 

Before any infrastructure code is merged, Checkov statically analyzes the Kubernetes manifests and Helm configurations against hundreds of industry security policies (CIS Kubernetes Benchmarks). It checks for:
- Containers running as root
- Missing resource limits (CPU/Memory)
- Lack of Network policies
- Hardcoded secrets
- Missing Liveness/Readiness probes

### Governance and Policy Exceptions
In an academic context, some strict production rules (like requiring SHA-256 Image Digests instead of tags) harm readability. Instead of removing the scanner, we implemented professional **Exception Management**. We used inline annotations (e.g., `checkov.io/skip=CKV_K8S_43`) to document *why* a specific policy is bypassed. This demonstrates mature security governance.

## 4. Technical Defense Highlights
When presenting this pipeline, emphasize:
> "We treat infrastructure as first-class code. By separating the infrastructure pipeline from the application CI, we avoid redundant cloud deployments. We utilize Checkov to catch critical Kubernetes misconfigurations (like root-access or missing probes) at the Pull Request level, ensuring our cluster is secure by design before a single pod is deployed."
