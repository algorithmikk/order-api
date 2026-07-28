# API smoke (Hurl)

Service-owned HTTP smoke checks under `src/test/api-smoke/`.
Run after ECS deploy against the ALB (see Post-deploy ALB smoke in GitHub Actions).

## Local

```bash
brew install hurl   # or see https://hurl.dev
hurl --test --variable "BASE_URL=https://api.umameats.com" src/test/api-smoke/*.hurl
# or against local:
hurl --test --variable "BASE_URL=http://localhost:8080" src/test/api-smoke/*.hurl
```
