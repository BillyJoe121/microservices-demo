# Development Pipelines (Point 5)

## 1. Architectural Overview
The Development Pipelines are structured around the principle of **independent microservice lifecycles**. In a microservices architecture, a change to the `vote` service should not trigger tests and builds for the `result` or `worker` services. 

To achieve this, we implemented three distinct Continuous Integration (CI) workflows:
- `ci-vote.yml`
- `ci-result.yml`
- `ci-worker.yml`

This separation adheres to the DevOps best practice of minimizing build times and isolating deployment blasts radius.

## 2. Trigger Strategy
Each pipeline implements strict **path filtering**. The workflows are triggered on `push` and `pull_request` to the `main` branch ONLY if changes occur within the respective microservice directory (e.g., `paths: - 'vote/**'`). This ensures CPU minutes and CI resources are utilized efficiently, proving an enterprise-grade approach to repository management.

## 3. Reusable Pipeline Logic (DRY Principle)
Building, scanning, and pushing Docker images is a repeated concern across all three services. Instead of duplicating this logic, we engineered a **Composite GitHub Action** located at `.github/actions/docker-build-scan-push/action.yml`.

**Why this approach?**
- **Maintainability:** If we need to update our Docker build strategy or swap our security scanner, we update the logic in one single file.
- **Consistency:** Ensures every microservice is built and scanned using the exact same standard.

## 4. Quality Gates and Security Scanning (DevSecOps)
The CI pipelines act as rigorous quality gates before any code is packaged:
1. **Linting & Formatting:** Code is statically analyzed (e.g., `golangci-lint` for Go, customized `checkstyle` for Java). We explicitly tuned the linters to balance academic velocity with structural quality (e.g., disabling overly strict Javadoc checks while enforcing code structure).
2. **Unit Testing:** Executed per service (`mvn test`, `go test`, logic verification).
3. **Container Security:** Inside the composite action, we integrated **Trivy** (`aquasecurity/trivy-action`). Every image is scanned for CVEs *before* it is pushed to the Docker registry. If a critical vulnerability is found, the build fails, embodying the "Shift-Left" security philosophy.

## 5. Continuous Deployment Strategy
We implemented an overarching `cd.yml` workflow to represent the CD phase. 

**Workflow Characteristics:**
- **Trigger:** It uses `workflow_run` to trigger only when the upstream CI pipelines (`vote`, `result`, or `worker`) complete successfully.
- **Environment Promotion:** It simulates a promotion flow from `staging` to `production` using GitHub Environments. This demonstrates knowledge of deployment gates.
- **Mocked Execution:** Because actual cloud target environments vary, the pipeline simulates the execution of `kubectl apply -k k8s/overlays/...` and image tagging operations. Despite being mocked, the *architectural design* is fully production-ready.

## 6. Technical Defense Highlights
When presenting this setup, emphasize:
> "We decoupled the CI lifecycles to match the microservices paradigm using path filters. We achieved DRY (Don't Repeat Yourself) code in our automation via Composite Actions, and we enforced a Shift-Left DevSecOps pipeline where images do not reach the registry unless they pass Trivy vulnerability scans."
