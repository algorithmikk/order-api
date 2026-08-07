# order-api

Spring Boot microservice that manages the full order lifecycle for UmaMeats — from placement through delivery. It owns the `umameats-orders` DynamoDB table and coordinates with the payment, driver, and events APIs via Kafka.

## Stack

| Layer | Technology |
|---|---|
| Runtime | Java 17 / Spring Boot 3.3.5 |
| Database | AWS DynamoDB (`umameats-orders`) |
| Messaging | Apache Kafka |
| HTTP client | Spring WebFlux `WebClient` |
| Notifications | Twilio (WhatsApp) |
| Secrets | AWS Secrets Manager |
| Container | Docker (Eclipse Temurin 17) |

---

## API Reference

All endpoints aree prefixed with `/api/v1`.

### Orders — `POST /orders`

Create a new order. The service sets `customerId` from the `X-Customer-Id` header, calculates all fees server-side, and triggers a Kafka `ORDER_CREATED` event.

**Headers:** `X-Customer-Id: <uuid>`

**Request body:**
```json
{
  "storeId": "uuid",
  "items": [{ "menuItemId": "uuid", "name": "Jerk Chicken", "price": 1499, "quantity": 2 }],
  "deliveryAddress": {
    "fullName": "Jane Doe", "phone": "+1-555-0100",
    "street": "100 King St W", "city": "Toronto", "state": "ON",
    "zipCode": "M5X 1A9", "country": "CA",
    "latitude": 43.6481, "longitude": -79.3820,
    "specialInstructions": "Ring buzzer 302"
  },
  "paymentMethod": "card",
  "paymentMethodId": "pm_xxx",
  "totalAmount": 3500,
  "tip": 300
}
```

### Orders — `GET /orders/{orderId}`

Fetch a single order. Ownership check: the `X-Customer-Id` header must match `order.customerId`.

### Orders — `GET /orders/customer/{customerId}`

Return all orders for a customer (requires matching `X-Customer-Id` header).

### Orders — `GET /orders/store/{storeId}?status=PREPARING`

Return orders for a store, optionally filtered by status.

### Orders — `PATCH /orders/{orderId}/status`

Customer-initiated status update (e.g. cancel). Body: `"CANCELLED"`.

### Orders — `PATCH /orders/{orderId}/status/restaurant`

Restaurant-initiated status update. Header: `X-Store-Id`. Common transitions:
`CREATED → CONFIRMED → PREPARING → READY_FOR_PICKUP`

---

### Pricing — `POST /pricing/calculate`

Called by the frontend at checkout to display exact fees before payment.

**Request body:**
```json
{ "storeId": "uuid", "items": [...], "deliveryDistanceKm": 3.2, "tipCents": 300 }
```

**Response:**
```json
{
  "subtotal": 2990, "deliveryFee": 359, "serviceFee": 150,
  "tip": 300, "totalAmount": 3799,
  "platformFee": 449, "restaurantPayout": 2541, "driverPayout": 659
}
```

### Pricing — `GET /pricing/tips?subtotalCents=2990`

Returns suggested tip amounts at 15 / 18 / 20 / 25 %.

---

### Promos — `POST /promo/validate`

Validate a promo code before applying it at checkout.

**Request body:** `{ "code": "WELCOME15", "subtotal": 2990 }`

Active codes (MVP hardcoded, DynamoDB migration pending):

| Code | Type | Value | Min order |
|---|---|---|---|
| `WELCOME15` | percent | 15 % | any |
| `UMAMEATS20` | percent | 20 % | $5 |
| `FREE5` | fixed | $5 off | $25 |

---

## Order Status Flow

```
PENDING_PAYMENT → PAYMENT_FAILED
                ↓
             CREATED → CONFIRMED → PREPARING → READY_FOR_PICKUP
                                                      ↓
                                         DRIVER_EN_ROUTE_TO_STORE
                                                      ↓
                                                  PICKED_UP
                                                      ↓
                                             OUT_FOR_DELIVERY
                                                      ↓
                                                  DELIVERED
             (any state) → CANCELLED
```

---

## Pricing Engine

All monetary values are stored and transferred in **cents** (integers) to avoid floating-point precision issues.

| Fee | Formula | Min | Max |
|---|---|---|---|
| Delivery fee | $2.99 base + $0.50/km after first 2 km | $2.99 | $9.99 |
| Delivery (no GPS) | 10 % of subtotal | $2.99 | $9.99 |
| Service fee | 5 % of subtotal | $0.99 | $4.99 |
| Platform fee | 15 % of subtotal (from restaurant) | — | — |
| Tip | Customer-selected | $0 | $500 |

**Payment split per order:**
- Customer pays: `subtotal + deliveryFee + serviceFee + tip`
- Restaurant receives: `subtotal − platformFee`
- Driver receives: `deliveryFee + tip`
- Platform keeps: `platformFee + serviceFee`

---

## Kafka Topics

| Topic | Producer | Consumer | Purpose |
|---|---|---|---|
| `umameats.order.events` | order-api | delivery-orchestration-api | New order created |
| `umameats.order.status` | order-api | driver-api, customer-api | Status change |
| `umameats.store.notifications` | order-api | store dashboard | New order alert |
| `umameats.customer.notifications` | order-api | customer-api | Order updates |
| `umameats.payment.events` | payment-api | order-api (consumer) | Payment confirmed |

---

## DynamoDB Indexes

Table: `umameats-orders` (hash key: `orderId`)

| GSI | Hash key | Used by |
|---|---|---|
| `customer-orders-index` | `customerId` | `GET /orders/customer/{id}` |
| `store-orders-index` | `storeId` | `GET /orders/store/{id}` |
| `driver-orders-index` | `driverId` | Driver API order lookup |

---

## Local Development

```bash
# Prerequisites: Java 17, Docker (for DynamoDB Local)

# Environment variables
export AWS_REGION=us-east-1
export AWS_ACCESS_KEY_ID=local
export AWS_SECRET_ACCESS_KEY=local
export GOOGLE_MAPS_API_KEY=<your-key>   # used by GeocodingService

./mvnw spring-boot:run
# → http://localhost:8080
# → http://localhost:8080/actuator/health
```

Kafka is optional locally — the service logs a warning and continues if the broker is unreachable (Kafka health check is disabled in `application.properties`).

---

## Build & Deploy

```bash
# Build JAR
./mvnw clean package -DskipTests

# Build Docker image
docker build -t order-api .

# Deploy to ECS (see deploy.sh)
./deploy.sh
```
