#!/usr/bin/env node
/**
 * Unified Toronto E2E seed: customer check + restaurant kitchen + driver + ops seed-demo order.
 *
 * Usage:
 *   ORDER_API_ADMIN_TOKEN=... ASC_DEMO_PASSWORD=... node scripts/seed-e2e-toronto.js
 *   node scripts/seed-e2e-toronto.js --ready     # skip kitchen; order READY_FOR_PICKUP
 *   node scripts/seed-e2e-toronto.js --created   # default CREATED for full restaurant path
 *   node scripts/seed-e2e-toronto.js --skip-driver
 *   node scripts/seed-e2e-toronto.js --dry-run
 *
 * Env:
 *   API_BASE_URL / ORDER_API_BASE_URL
 *   ORDER_API_ADMIN_TOKEN / OPS_ADMIN_TOKEN
 *   ASC_DEMO_PASSWORD (customer + store + driver family)
 *   DEMO_KITCHEN_STORE_ID (optional)
 */

const { spawnSync } = require('child_process');
const fs = require('fs');
const path = require('path');

const ROOT = process.env.UMAMEATS_ROOT
  ? path.resolve(process.env.UMAMEATS_ROOT)
  : (fs.existsSync(path.join(__dirname, '../../mobile'))
      ? path.join(__dirname, '../..')
      : path.join(__dirname, '../../..'));
const DRY_RUN = process.argv.includes('--dry-run');
const READY = process.argv.includes('--ready');
const SKIP_DRIVER = process.argv.includes('--skip-driver');
const SKIP_CUSTOMER = process.argv.includes('--skip-customer');

const API = (process.env.ORDER_API_BASE_URL || process.env.API_BASE_URL || 'https://api.umameats.com').replace(
  /\/$/,
  ''
);
const token = process.env.ORDER_API_ADMIN_TOKEN || process.env.OPS_ADMIN_TOKEN;
const CUSTOMER_ID = process.env.CUSTOMER_ID || '7cc3f702-ba3d-48a8-8238-263fd3f31eab';

function runNode(scriptRel, env = {}) {
  const script = path.join(ROOT, scriptRel);
  if (!fs.existsSync(script)) {
    console.warn('Skip missing script', scriptRel);
    return { status: 0 };
  }
  console.log('\n===', scriptRel, '===');
  if (DRY_RUN) {
    console.log('[dry-run] would run', script);
    return { status: 0 };
  }
  const result = spawnSync(process.execPath, [script, ...(DRY_RUN ? ['--dry-run'] : [])], {
    cwd: path.dirname(script),
    env: { ...process.env, ...env },
    stdio: 'inherit',
  });
  return result;
}

async function ensureRestaurant() {
  const { main } = require(path.join(__dirname, 'seed-demo-restaurant.js'));
  return main();
}

function putOrderViaDynamo(storeId, mode) {
  const { execFileSync } = require('child_process');
  const orderId = require('crypto').randomUUID();
  const status = mode === 'READY' || mode === 'READY_FOR_PICKUP' ? 'READY_FOR_PICKUP' : 'CREATED';
  const now = new Date().toISOString().replace(/\.\d{3}Z$/, '');
  const tip = 300;
  const subtotal = 1299;
  const deliveryFee = 499;
  const total = subtotal + deliveryFee + tip;
  const item = {
    orderId: { S: orderId },
    customerId: { S: CUSTOMER_ID },
    storeId: { S: storeId },
    storeName: { S: 'UmaMeats Demo Kitchen' },
    storePhone: { S: '+14165550199' },
    pickupAddress: { S: '250 Front St W, Toronto, ON M5V 3G5' },
    restaurantLat: { N: '43.6426' },
    restaurantLng: { N: '-79.3871' },
    status: { S: status },
    deliveryStatus: { S: 'UNASSIGNED' },
    orderDate: { S: now },
    paymentMethod: { S: 'CARD' },
    paymentMethodId: { S: 'ops_seed_demo_dynamo' },
    deliveryFee: { N: String(deliveryFee) },
    tip: { N: String(tip) },
    subtotal: { N: String(subtotal) },
    totalAmount: { N: String(total) },
    specialInstructions: { S: 'E2E demo seed (Dynamo fallback until ops seed-demo is deployed)' },
    deliveryAddress: {
      M: {
        fullName: { S: 'App Review Customer' },
        phone: { S: '4165550137' },
        street: { S: '100 Queens Park' },
        city: { S: 'Toronto' },
        state: { S: 'ON' },
        zipCode: { S: 'M5S 2C6' },
        country: { S: 'CA' },
        latitude: { N: '43.6677' },
        longitude: { N: '-79.3948' },
      },
    },
    items: {
      L: [
        {
          M: {
            itemId: { S: 'demo-burrito' },
            itemName: { S: 'Demo Breakfast Burrito' },
            quantity: { N: '1' },
            price: { N: String(subtotal) },
          },
        },
      ],
    },
  };

  if (DRY_RUN) {
    console.log('[dry-run] dynamodb put-item order', orderId, status);
    return { orderId, storeId, status, deliveryStatus: 'UNASSIGNED' };
  }

  execFileSync(
    'aws',
    ['dynamodb', 'put-item', '--table-name', 'umameats-orders', '--item', JSON.stringify(item)],
    { encoding: 'utf8' }
  );
  console.log('Wrote order via DynamoDB', orderId, status);
  return { orderId, storeId, status, deliveryStatus: 'UNASSIGNED', customerId: CUSTOMER_ID, totalAmount: total };
}

async function seedDemoOrder(storeId) {
  const mode = READY ? 'READY' : 'CREATED';
  const url = `${API}/api/v1/ops/orders/seed-demo`;
  console.log('\n=== ops seed-demo ===');
  console.log(mode, '→', url);

  if (DRY_RUN) {
    console.log('[dry-run] POST', url, { storeId, mode, customerId: CUSTOMER_ID });
    return { orderId: 'dry-run-order', storeId, status: mode };
  }

  if (token) {
    const res = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'application/json',
        'X-Admin-Token': token,
      },
      body: JSON.stringify({
        customerId: CUSTOMER_ID,
        storeId,
        mode,
        tipCents: 300,
      }),
    });
    const text = await res.text();
    if (res.ok) {
      const body = JSON.parse(text);
      console.log('Seeded order via ops API', body);
      return body;
    }
    console.warn('ops seed-demo failed', res.status, text.slice(0, 200), '— Dynamo fallback');
  } else {
    console.warn('ORDER_API_ADMIN_TOKEN not set — Dynamo fallback');
  }

  return putOrderViaDynamo(storeId, mode);
}

async function main() {
  console.log('UmaMeats Toronto E2E seed');
  console.log('API:', API);
  console.log('Mode:', READY ? 'READY (skip kitchen)' : 'CREATED (full restaurant path)');

  if (!SKIP_CUSTOMER) {
    runNode('mobile/umameats-customer-mobile/scripts/seed-review-account.js');
  }

  const restaurant = await ensureRestaurant();
  const storeId = restaurant.storeId || process.env.DEMO_KITCHEN_STORE_ID;
  if (!storeId) throw new Error('No Demo Kitchen storeId');

  process.env.DEMO_KITCHEN_STORE_ID = storeId;

  if (!SKIP_DRIVER) {
    runNode('mobile/umameats-driver-mobile/scripts/seed-review-driver.js', {
      DEMO_KITCHEN_STORE_ID: storeId,
    });
  }

  const order = await seedDemoOrder(storeId);

  console.log('\n========== NEXT STEPS ==========');
  console.log('1. Restaurant: https://umameats-landing-saas.vercel.app/login');
  console.log('   storeview@umameats.com → Orders → advance CREATED → PREPARING → READY');
  console.log('2. Driver: TestFlight driverreview@umameats.com → Online → accept → POD');
  console.log('3. Customer (optional): appreview@umameats.com → Orders for', order.orderId);
  console.log('OrderId:', order.orderId);
  console.log('StoreId:', storeId);
  console.log('See docs/E2E_DEMO_TORONTO.md');
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
