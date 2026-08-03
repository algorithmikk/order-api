#!/usr/bin/env node
/**
 * Provision UmaMeats Demo Kitchen + storeview@umameats.com restaurant login.
 *
 * Steps:
 *   1. Create/login restaurant user via user-api
 *   2. Create Demo Kitchen near 250 Front St W (or reuse existing)
 *   3. Ensure store.userId is linked (PATCH)
 *   4. Seed 2–3 menu items if store has none
 *
 * Usage:
 *   ASC_DEMO_PASSWORD='StoreReview2026!' node scripts/seed-demo-restaurant.js
 *   node scripts/seed-demo-restaurant.js --dry-run
 *
 * Env:
 *   API_BASE_URL (default https://api.umameats.com)
 *   STORE_EMAIL / RESTAURANT_EMAIL (default storeview@umameats.com)
 *   STORE_PASSWORD / ASC_DEMO_PASSWORD
 *   DEMO_KITCHEN_STORE_ID (optional — reuse existing store UUID)
 */

const { execFileSync } = require('child_process');
const fs = require('fs');
const path = require('path');

const DRY_RUN = process.argv.includes('--dry-run');
const API = (process.env.API_BASE_URL || 'https://api.umameats.com').replace(/\/$/, '');
const email = process.env.STORE_EMAIL || process.env.RESTAURANT_EMAIL || 'storeview@umameats.com';
const password =
  process.env.STORE_PASSWORD ||
  process.env.ASC_DEMO_PASSWORD ||
  'StoreReview2026!';

const STATE_FILE = path.join(__dirname, '.demo-kitchen-store-id');
/** Deterministic UUID so re-runs overwrite the same Demo Kitchen row. */
const DEMO_STORE_ID =
  process.env.DEMO_KITCHEN_STORE_ID || 'a0000000-e2e0-4000-8000-0000000000d1';
const STORES_TABLE = process.env.STORES_TABLE || 'umameats-stores';

const DEMO_STORE = {
  name: 'UmaMeats Demo Kitchen',
  description: 'Staging kitchen for Toronto E2E demos — not a live merchant',
  phoneNumber: '+14165550199',
  email,
  address: '250 Front St W',
  city: 'Toronto',
  state: 'ON',
  zipCode: 'M5V 3G5',
  country: 'CA',
  latitude: 43.6426,
  longitude: -79.3871,
  category: 'Canadian',
  merchantType: 'RESTAURANT',
  deliveryPreference: 'PLATFORM_ONLY',
  isOpen: true,
};

const MENU = [
  {
    name: 'Demo Breakfast Burrito',
    description: 'Eggs, cheddar, salsa — staging item',
    price: 12.99,
    priceCents: 1299,
    category: 'Mains',
    available: true,
  },
  {
    name: 'Demo Chicken Bowl',
    description: 'Rice, greens, grilled chicken — staging item',
    price: 15.5,
    priceCents: 1550,
    category: 'Mains',
    available: true,
  },
  {
    name: 'Demo Sparkling Water',
    description: 'Cold drink — staging item',
    price: 2.5,
    priceCents: 250,
    category: 'Drinks',
    available: true,
  },
];

async function api(method, pathname, body) {
  const url = `${API}${pathname}`;
  if (DRY_RUN) {
    console.log('[dry-run]', method, url, body ? JSON.stringify(body).slice(0, 120) : '');
    return { ok: true, status: 200, json: async () => ({}) };
  }
  const res = await fetch(url, {
    method,
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/json',
    },
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
  return res;
}

async function ensureUser() {
  console.log(`API: ${API}`);
  console.log(`Restaurant email: ${email}`);

  const loginRes = await api('POST', '/api/v1/auth/login', { email, password });
  if (loginRes.ok) {
    const user = await loginRes.json();
    console.log('Logged in existing user', user.id);
    return user;
  }

  console.log('Login failed — creating user…');
  const createRes = await api('POST', '/api/v1/users', {
    email,
    name: 'Store Review',
    password,
    country: 'CA',
  });
  if (!createRes.ok && !DRY_RUN) {
    const text = await createRes.text();
    // May already exist with different password — try login again after create fail
    const retry = await api('POST', '/api/v1/auth/login', { email, password });
    if (retry.ok) {
      return retry.json();
    }
    throw new Error(`Failed to create user: ${createRes.status} ${text.slice(0, 300)}`);
  }
  if (DRY_RUN) return { id: 'dry-run-user-id', email };

  const created = await createRes.json();
  console.log('Created user', created.id);

  // Confirm login works
  const login2 = await api('POST', '/api/v1/auth/login', { email, password });
  if (login2.ok) return login2.json();
  return created;
}

async function findDemoStore(userId) {
  const candidates = [
    process.env.DEMO_KITCHEN_STORE_ID,
    DEMO_STORE_ID,
    fs.existsSync(STATE_FILE) ? fs.readFileSync(STATE_FILE, 'utf8').trim() : '',
  ].filter(Boolean);

  for (const id of [...new Set(candidates)]) {
    const res = await api('GET', `/api/v1/stores/${id}`);
    if (res.ok) return res.json();
  }

  const byUser = await api('GET', `/api/v1/stores/user/${encodeURIComponent(userId)}`);
  if (byUser.ok) {
    const stores = await byUser.json();
    const match = (Array.isArray(stores) ? stores : []).find(
      (s) => s.name === DEMO_STORE.name || (s.name || '').includes('Demo Kitchen')
    );
    if (match) return match;
  }

  return null;
}

function putStoreViaDynamo(userId) {
  const now = Date.now();
  const item = {
    PK: { S: `STORE#${DEMO_STORE_ID}` },
    SK: { S: `STORE#${DEMO_STORE_ID}` },
    uuid: { S: DEMO_STORE_ID },
    storeId: { S: DEMO_STORE_ID },
    title: { S: DEMO_STORE.name },
    name: { S: DEMO_STORE.name },
    sanitizedTitle: { S: 'umameats-demo-kitchen' },
    phoneNumber: { S: DEMO_STORE.phoneNumber },
    email: { S: DEMO_STORE.email },
    emails: { L: [{ S: DEMO_STORE.email }] },
    userId: { S: userId },
    category: { S: DEMO_STORE.category },
    merchantType: { S: DEMO_STORE.merchantType },
    deliveryPreference: { S: DEMO_STORE.deliveryPreference },
    status: { S: 'PARTNERED' },
    subscriptionStatus: { S: 'ACTIVE_SUBSCRIBER' },
    onboardingSource: { S: 'E2E_SEED' },
    isOpen: { BOOL: true },
    storeAvailablityStatus: { S: 'OPEN' },
    country: { S: DEMO_STORE.country },
    city: { S: DEMO_STORE.city },
    address: { S: DEMO_STORE.address },
    latitude: { N: String(DEMO_STORE.latitude) },
    longitude: { N: String(DEMO_STORE.longitude) },
    zonePk: { S: 'ZONE#OLD_TORONTO' },
    zoneSk: { S: `STORE#${DEMO_STORE_ID}` },
    borough: { S: 'Old Toronto' },
    neighborhood: { S: 'Entertainment District' },
    createdAt: { N: String(now) },
    updatedAt: { N: String(now) },
    location: {
      M: {
        address: { S: DEMO_STORE.address },
        city: { S: DEMO_STORE.city },
        region: { S: DEMO_STORE.state },
        postalCode: { S: DEMO_STORE.zipCode },
        country: { S: DEMO_STORE.country },
        latitude: { N: String(DEMO_STORE.latitude) },
        longitude: { N: String(DEMO_STORE.longitude) },
      },
    },
  };

  if (DRY_RUN) {
    console.log('[dry-run] dynamodb put-item', DEMO_STORE_ID);
    return { storeId: DEMO_STORE_ID, name: DEMO_STORE.name, userId };
  }

  execFileSync(
    'aws',
    ['dynamodb', 'put-item', '--table-name', STORES_TABLE, '--item', JSON.stringify(item)],
    { encoding: 'utf8' }
  );
  console.log('Wrote Demo Kitchen via DynamoDB', DEMO_STORE_ID);
  return { storeId: DEMO_STORE_ID, name: DEMO_STORE.name, userId, isOpen: true };
}

async function ensureStore(userId) {
  let store = await findDemoStore(userId);
  if (store && store.storeId) {
    console.log('Reusing store', store.storeId, store.name);
    if (store.userId !== userId) {
      console.log('Linking store.userId →', userId);
      const patch = await api('PUT', `/api/v1/stores/${store.storeId}`, {
        ...DEMO_STORE,
        userId,
        name: DEMO_STORE.name,
      });
      if (!patch.ok && !DRY_RUN) {
        console.warn('API link failed — writing userId via Dynamo');
        store = putStoreViaDynamo(userId);
      } else if (patch.ok && !DRY_RUN) {
        store = await patch.json();
      }
    }
    persistStoreId(store.storeId);
    return store;
  }

  console.log('Creating Demo Kitchen…');
  const createRes = await api('POST', '/api/v1/stores', {
    ...DEMO_STORE,
    userId,
  });
  if (createRes.ok && !DRY_RUN) {
    store = await createRes.json();
    console.log('Created store via API', store.storeId);
    if (!store.userId || store.userId !== userId) {
      const patch = await api('PUT', `/api/v1/stores/${store.storeId}`, {
        ...DEMO_STORE,
        userId,
        name: DEMO_STORE.name,
      });
      if (patch.ok) store = await patch.json();
      else store = putStoreViaDynamo(userId);
    }
  } else {
    if (!DRY_RUN) {
      const t = await createRes.text();
      console.warn('POST /stores failed', createRes.status, t.slice(0, 200), '— using Dynamo fallback');
    }
    store = putStoreViaDynamo(userId);
  }

  persistStoreId(store.storeId);
  return store;
}

function persistStoreId(storeId) {
  if (DRY_RUN || !storeId) return;
  fs.writeFileSync(STATE_FILE, storeId + '\n', 'utf8');
  console.log('Wrote', STATE_FILE);
}

async function ensureMenu(storeId) {
  const listRes = await api('GET', `/api/v1/menu-items/store/${storeId}`);
  let existing = [];
  if (listRes.ok && !DRY_RUN) {
    existing = await listRes.json();
  }
  if (Array.isArray(existing) && existing.length > 0) {
    console.log(`Menu already has ${existing.length} items — skipping seed`);
    return existing;
  }

  console.log('Seeding menu items…');
  const created = [];
  for (const item of MENU) {
    const res = await api('POST', '/api/v1/menu-items', {
      ...item,
      storeId,
    });
    if (!res.ok && !DRY_RUN) {
      const t = await res.text();
      console.warn('Menu item failed', item.name, res.status, t.slice(0, 200));
      continue;
    }
    if (!DRY_RUN) created.push(await res.json());
    else created.push(item);
    console.log('  +', item.name);
  }
  return created;
}

async function main() {
  const user = await ensureUser();
  if (!user?.id && !DRY_RUN) {
    throw new Error('No user id');
  }
  const userId = user.id || 'dry-run-user-id';
  const store = await ensureStore(userId);
  const menu = await ensureMenu(store.storeId);

  console.log('');
  console.log(DRY_RUN ? 'Dry run complete.' : 'Demo restaurant ready.');
  console.log('  Email:   ', email);
  console.log('  Password:', password.replace(/./g, '*').slice(0, 4) + '…');
  console.log('  UserId:  ', userId);
  console.log('  StoreId: ', store.storeId);
  console.log('  Store:   ', store.name || DEMO_STORE.name);
  console.log('  Menu:    ', Array.isArray(menu) ? menu.length : 0, 'items');
  console.log('');
  console.log('Login: https://umameats-landing-saas.vercel.app/login');
  console.log('Export: DEMO_KITCHEN_STORE_ID=' + store.storeId);

  return { userId, storeId: store.storeId, email };
}

if (require.main === module) {
  main().catch((err) => {
    console.error(err);
    process.exit(1);
  });
}

module.exports = { main, DEMO_STORE };
