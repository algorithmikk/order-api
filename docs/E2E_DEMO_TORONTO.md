# Toronto E2E Demo — Customer + Restaurant + Driver

Repeatable dry-run across three surfaces with a dedicated **UmaMeats Demo Kitchen** (not a live merchant).

## Accounts

| Role | App | Email | Password env |
|------|-----|-------|----------------|
| Customer | `umameats-customer-mobile` | `appreview@umameats.com` | `ASC_DEMO_PASSWORD` |
| Restaurant | `umameats-landing-saas` | `storeview@umameats.com` | `ASC_DEMO_PASSWORD` / `StoreReview2026!` |
| Driver | `umameats-driver-mobile` | `driverreview@umameats.com` | `ASC_DEMO_PASSWORD` / `DRIVER_PASSWORD` |

Customer id (fixed): `7cc3f702-ba3d-48a8-8238-263fd3f31eab`

## Prerequisites

1. Deploy **store-api-rest** (persists `userId` / `isOpen` on create+update).
2. Deploy **order-api** (`POST /api/v1/ops/orders/seed-demo`, `PATCH /api/v1/ops/orders/{id}/status`).
3. Env: `ORDER_API_ADMIN_TOKEN` (or `OPS_ADMIN_TOKEN`) matching `APP_ADMIN_TOKEN` on order-api.
4. AWS CLI creds for Dynamo (customer/driver review seeds).

## One-command seed

```bash
cd UmaMeats
export ASC_DEMO_PASSWORD='…'
export ORDER_API_ADMIN_TOKEN='…'
node scripts/seed-e2e-toronto.js          # CREATED order (full kitchen path)
node scripts/seed-e2e-toronto.js --ready  # READY_FOR_PICKUP (driver-only loop)
```

This runs:

1. Customer review seed (optional)
2. `scripts/seed-demo-restaurant.js` → user + Demo Kitchen + menu
3. Driver review seed (offer tied to Demo Kitchen storeId)
4. Ops `seed-demo` → prints `orderId`

Store id is cached in `scripts/.demo-kitchen-store-id`.

## Manual dry-run checklist

Open three surfaces:

1. **Restaurant** — [landing-saas login](https://umameats-landing-saas.vercel.app/login)  
   `storeview@umameats.com` → Orders → see seeded `CREATED` order → Accept / PREPARING → READY_FOR_PICKUP  
   - New-order sound/banner/sidebar badge  
   - Kitchen ticket print works on tablet width  
   - Status errors show toast (not full-page wipe)

2. **Driver** — TestFlight  
   `driverreview@umameats.com` → Go Online → accept offer → status swipes → POD photo/PIN → Earnings

3. **Customer** (optional) —  
   `appreview@umameats.com` → Orders → same `orderId` progression

4. **Ops fallback** — ops-control-center assign/unassign if marketplace offer missing

## Dashboard verification (prod readiness)

- [ ] `storeview@` sees only Demo Kitchen; Menu edits that store
- [ ] Integrations tab loads stores via `userId` (not email)
- [ ] No `/profile` or `/settings` 404 from header
- [ ] Unauthenticated visitors cannot enumerate all stores via dashboard
- [ ] Store can toggle `isOpen` closed
- [ ] EN strings on Orders/Menu; FR keys intact
- [ ] Home shows live active-order count with link to Orders

## Individual scripts

| Script | Purpose |
|--------|---------|
| `scripts/seed-demo-restaurant.js` | User + Demo Kitchen + menu |
| `scripts/seed-e2e-toronto.js` | Full orchestrator |
| `mobile/umameats-driver-mobile/scripts/seed-review-driver.js` | Driver vouch + history + offer |
| `mobile/umameats-driver-mobile/scripts/seed-toronto-ready-order.js` | Ops READY seed or PATCH |
| `mobile/umameats-customer-mobile/scripts/seed-review-account.js` | Customer order history |

## Deploy order

1. **store-api-rest** — push `main` to trigger ECS (persists `userId` / `isOpen`; `StoreConverter` returns them). Until deployed, `scripts/seed-demo-restaurant.js` uses a DynamoDB put fallback (already works for UserStoresIndex).
2. **order-api** — push `main` to deploy `POST /ops/orders/seed-demo` + `PATCH /ops/orders/{id}/status`. Until deployed, `seed-e2e-toronto.js` writes a CREATED/READY order via DynamoDB so the restaurant board still lights up.
3. **umameats-landing-saas** — deploy Vercel (P0/P1 dashboard changes).
4. Run `node scripts/seed-e2e-toronto.js`
5. Human click-through across three surfaces

Local Docker/ECR deploy may fail on this machine (mvnw/JDK 26 host mismatch); prefer GitHub Actions on push to `main`.

## Out of scope

- Real Stripe checkout for the demo loop (ops seed bypasses payment)
- POS OAuth, KDS kanban, self-serve signup
