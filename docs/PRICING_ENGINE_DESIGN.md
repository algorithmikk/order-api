# UMAMEATS Pricing Engine Design

## Overview

This document describes the pricing engine architecture for UMAMEATS, responsible for calculating delivery fees, tips, service fees, and platform fees.

## Current State (Problems)

```mermaid
flowchart TB
    subgraph Customer["Customer App (checkout)"]
        C1["deliveryFee = 0.00 ❌ HARDCODED"]
        C2["tip = NOT SENT ❌ MISSING"]
        C3["totalAmount = subtotal only"]
    end
    
    subgraph OrderAPI["Order API (createOrder)"]
        O1["Receives order from customer"]
        O2["deliveryFee = NOT SET ❌"]
        O3["tip = NOT SET ❌"]
        O4["Saves to DynamoDB"]
    end
    
    subgraph DynamoDB["DynamoDB (umameats-orders)"]
        D1["deliveryFee: null ❌"]
        D2["tip: null ❌"]
    end
    
    subgraph DriverAPI["Driver API (processDriverPayout)"]
        DR1["calculateDeliveryFee() - 10% min $3 max $10"]
        DR2["calculateTip() - FIXED 15% ❌ WRONG"]
        DR3["Only called on DELIVERED status"]
        DR4["Values NOT saved to order ❌"]
    end
    
    subgraph DriverApp["Driver App (EarningsService)"]
        DA1["Uses order.deliveryFee"]
        DA2["Uses order.tip"]
        DA3["Falls back to calculation if null"]
    end
    
    C1 --> O1
    C2 --> O1
    O1 --> O2
    O1 --> O3
    O2 --> O4
    O3 --> O4
    O4 --> D1
    O4 --> D2
    D1 --> DA1
    D2 --> DA2
    DR1 -.->|"Used for payout only"| DR3
    DR2 -.->|"Used for payout only"| DR3
```

### Problems Identified

| Component | What It Does | Problem |
|-----------|--------------|---------|
| **Customer App** (`checkout/page.tsx` line 146) | `deliveryFee = 0.00` hardcoded | Never sends real delivery fee |
| **Customer App** | No tip field in checkout | Customer can't tip |
| **Order API** (`createOrder`) | Just saves what it receives | Doesn't calculate or set deliveryFee/tip |
| **Driver API** (`processDriverPayout`) | Calculates fee/tip for payout only | Values NOT saved to order |
| **Driver App** (`EarningsService`) | Reads from order, falls back | Always falls back because order has null |

## Target Architecture

```mermaid
flowchart TB
    subgraph PricingEngine["Pricing Engine Service (NEW)"]
        PE1["calculateDeliveryFee(distance, orderTotal, zone)"]
        PE2["calculateServiceFee(orderTotal)"]
        PE3["calculatePlatformFee(orderTotal)"]
        PE4["calculateDriverPayout(deliveryFee, tip)"]
        PE5["Zone-based pricing rules"]
        PE6["Surge pricing logic"]
        PE7["Promo code validation"]
    end
    
    subgraph CustomerApp["Customer App"]
        CA1["Show delivery fee before checkout"]
        CA2["Tip selector: 15% / 20% / 25% / Custom"]
        CA3["totalAmount = subtotal + deliveryFee + serviceFee + tip"]
        CA4["Send all values to backend"]
    end
    
    subgraph OrderAPI["Order API"]
        OA1["Validate pricing from frontend"]
        OA2["Recalculate server-side for security"]
        OA3["Save deliveryFee, tip, serviceFee to order"]
        OA4["Split payment: restaurant / driver / platform"]
    end
    
    subgraph PaymentAPI["Payment API"]
        PA1["Charge customer: subtotal + fees + tip"]
        PA2["Transfer to restaurant: subtotal - platformFee"]
        PA3["Transfer to driver: deliveryFee + tip"]
        PA4["Platform keeps: platformFee + serviceFee"]
    end
    
    CustomerApp -->|"GET /pricing/calculate"| PricingEngine
    PricingEngine -->|"Returns fees"| CustomerApp
    CustomerApp -->|"POST /orders with all fees"| OrderAPI
    OrderAPI -->|"Validate & save"| PaymentAPI
```

## Pricing Rules

### Delivery Fee Calculation

```
Base Fee: $2.99
Distance Fee: $0.50 per km (after first 2km free)
Minimum: $2.99
Maximum: $9.99

Formula:
deliveryFee = max($2.99, min($9.99, $2.99 + max(0, distance - 2) * $0.50))
```

### Service Fee

```
Service Fee: 5% of subtotal
Minimum: $0.99
Maximum: $4.99

Formula:
serviceFee = max($0.99, min($4.99, subtotal * 0.05))
```

### Platform Fee (from Restaurant)

```
Platform Fee: 15% of subtotal (taken from restaurant payout)
```

### Tip

```
Customer selects: 15%, 18%, 20%, 25%, or custom amount
Tip goes 100% to driver
```

## Order Fields

| Field | Type | Description |
|-------|------|-------------|
| `subtotal` | Long (cents) | Sum of item prices |
| `deliveryFee` | Long (cents) | Calculated delivery fee |
| `serviceFee` | Long (cents) | Platform service fee |
| `tip` | Long (cents) | Customer tip for driver |
| `totalAmount` | Long (cents) | subtotal + deliveryFee + serviceFee + tip |
| `platformFee` | Long (cents) | 15% of subtotal (for accounting) |

## Payment Split

When order is paid:

1. **Customer pays**: `totalAmount` (subtotal + deliveryFee + serviceFee + tip)
2. **Restaurant receives**: `subtotal - platformFee` (85% of food cost)
3. **Driver receives**: `deliveryFee + tip` (on delivery completion)
4. **Platform keeps**: `platformFee + serviceFee`

## Implementation Checklist

- [x] Document current state and problems
- [ ] Create PricingService in order-api
- [ ] Update Order model with new fields
- [ ] Update createOrder to calculate and persist fees
- [ ] Add tip selector UI to customer-app
- [ ] Add delivery fee display to customer-app
- [ ] Update driver-api to use persisted values
- [ ] Test end-to-end flow

## Date Created

2026-01-11

