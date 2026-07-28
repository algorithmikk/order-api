# E2E smoke (Hurl)

HTTP smoke/e2e checks for this API. Lived under `src/test/smoke/e2e` (Spring Boot test tree).

## Local

```bash
hurl --test --variable "BASE_URL=https://api.umameats.com" src/test/smoke/e2e/*.hurl
```

## CI

Post-deploy ALB smoke runs these after ECS is stable.
