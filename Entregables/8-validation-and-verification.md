# Validation and Verification (Point 8)

## 1. Overview of Quality Assurance
An infrastructure and pipeline design is only as good as its verification mechanisms. We implemented a rigorous, multi-tiered validation approach ensuring that code, containers, and deployment configurations are robust before they ever reach a runtime environment.

## 2. Local Validation Emulation
To ensure high developer velocity and reduce reliance on Git pushes to find syntax errors, the repository is configured to be fully verifiable locally:
- **Linting:** Configurations can be validated pre-commit by running `yamllint .` to catch whitespace or indentation issues.
- **Helm Dry-Run:** The infrastructure chart was validated using `helm lint infrastructure/` and `helm template`, resolving nested nil-pointer exceptions before pushing.
- **Checkstyle & GoLint:** Code styling rules can be verified locally via `mvn checkstyle:check` and `golangci-lint run`.

## 3. Automated CI/CD Verification
The GitHub Actions pipelines serve as continuous verification gates:
- **Application Validation:** Unit tests are automatically compiled and executed. For Java (`vote`), the Maven test phase guarantees that business logic holds. For Node and Go, equivalent steps ensure runtime safety.
- **Build Verification:** The process of successfully building intermediate Docker multi-stage images serves as an implicit compiler and dependency resolution check.
- **Code Style Verification:** Actions strictly halt if `checkstyle` or `golangci-lint` fail, enforcing a repository-wide standard. We actively tuned these constraints (e.g., deprecating `rand.Seed` in Go) to maintain a passing build matrix.

## 4. Infrastructure & Security Verification
- **Trivy Vulnerability Scans:** Implemented directly into the Composite Action, running on the final Docker image layer. Verification occurs mathematically via CVE database matching.
- **IaC Checkov Audits:** Verification of Kubernetes and Helm files. Instead of trusting that a YAML file "looks secure," Checkov programmatically verifies attributes like `runAsNonRoot: true`. 
- **Validation of Fixes:** Through iterative commits, we demonstrably moved from dozens of Checkov and Yamllint violations to 0 active errors in the master branch, proving the verification process works.

## 5. Runtime Verification Strategy
Verification does not stop after deployment. The Kubernetes manifests implement active, running verification:
- **Liveness Probes:** Kublet periodically executes commands (like `pg_isready` for Postgres) or hits HTTP endpoints to verify the internal application state hasn't deadlocked.
- **Readiness Probes:** The Service load balancers verify that a pod is fully initialized before routing traffic to it, preventing 502 Bad Gateway errors during rollouts.
