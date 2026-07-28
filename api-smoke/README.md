# API smoke (Hurl)

Service-owned HTTP smoke checks run after ECS deploy against the ALB.

## Local

```bash
brew install hurl   # or see https://hurl.dev
hurl --test --variable "BASE_URL=https://api.umameats.com" api-smoke/*.hurl
# or against local:
hurl --test --variable "BASE_URL=http://localhost:8080" api-smoke/*.hurl
```

## CI

`.github/workflows` **Post-deploy ALB smoke** installs Hurl and runs `api-smoke/*.hurl` after ECS is stable.
