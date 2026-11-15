const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const DATABASE_ID = "ai-studio-android-sweeppul-2ff82bc9-0dc3-4d17-a291-2c1be102d76e";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validSignalPayload(uid, overrides = {}) {
  const ts = new Date(Date.now() - 2000);
  return {
    userId: uid,
    symbol: "BTCUSD",
    timeframe: "15m",
    direction: "BUY",
    strength: "STRONG_BUY",
    smcEvent: "SSL_SWEEP_CHoCH",
    entryPrice: 91250.5,
    stopLoss: 90820.0,
    takeProfit1: 91890.0,
    takeProfit2: 92325.0,
    takeProfit3: 92950.0,
    sweptLiquidityLevel: 90880.0,
    riskRewardRatio: 2.5,
    buyIndicators: 19,
    sellIndicators: 3,
    neutralIndicators: 4,
    status: "ACTIVE",
    notes: "SSL liquidity sweep + Bullish CHoCH + 19/26 TV indicators",
    createdAt: ts,
    updatedAt: ts,
    ...overrides,
  };
}

function validAlertConfigPayload(uid, overrides = {}) {
  const ts = new Date(Date.now() - 2000);
  return {
    userId: uid,
    symbol: "XAUUSD",
    enabled: true,
    tf1m: true,
    tf5m: true,
    tf15m: true,
    tf30m: true,
    minConfluence: 16,
    requireLiquiditySweep: true,
    fcmToken: "sample_fcm_token_xyz",
    createdAt: ts,
    updatedAt: ts,
    ...overrides,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write signals or alert_configs", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore(DATABASE_ID);
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("signals").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("signals").doc("sig_1").set(validSignalPayload(ALICE_UID))
  );
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("alert_configs").get());
});

test("Authenticated user can create, list, update to TP_HIT, and delete their own signal", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  const sigRef = aliceDb.collection("users").doc(ALICE_UID).collection("signals").doc("sig_btc_15m");

  await assertSucceeds(sigRef.set(validSignalPayload(ALICE_UID)));
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("signals").get());
  await assertSucceeds(
    sigRef.update({
      status: "TP_HIT",
      notes: "Target 2 reached",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  // Terminal state locking: cannot update once TP_HIT
  await assertFails(
    sigRef.update({
      status: "ACTIVE",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertSucceeds(sigRef.delete());
});

test("Cross-user isolation: Bob cannot read or write Alice's signals or alert configs", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("signals").doc("sig_1").set(validSignalPayload(ALICE_UID))
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore(DATABASE_ID);
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("signals").doc("sig_1").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("signals").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("alert_configs").doc("XAUUSD").set(validAlertConfigPayload(BOB_UID))
  );
});

test("Shadow update & schema poisoning checks reject ghost fields and mismatched 26-indicator totals", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  const sigCol = aliceDb.collection("users").doc(ALICE_UID).collection("signals");

  // Ghost field injection
  await assertFails(sigCol.doc("sig_ghost").set(validSignalPayload(ALICE_UID, { isAdmin: true })));
  // Indicator sum != 26
  await assertFails(sigCol.doc("sig_bad_sum").set(validSignalPayload(ALICE_UID, { buyIndicators: 25 })));
  // Invalid document ID characters
  await assertFails(sigCol.doc("bad id!").set(validSignalPayload(ALICE_UID)));
  // Spoofed userId inside owned path
  await assertFails(sigCol.doc("sig_spoof").set(validSignalPayload(BOB_UID)));
});

test("AlertConfig CRUD enforces schema validation and immutable symbol/userId", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore(DATABASE_ID);
  const cfgRef = aliceDb.collection("users").doc(ALICE_UID).collection("alert_configs").doc("XAUUSD");

  await assertSucceeds(cfgRef.set(validAlertConfigPayload(ALICE_UID)));
  await assertSucceeds(
    cfgRef.update({
      minConfluence: 18,
      tf1m: false,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  // Cannot mutate symbol or userId
  await assertFails(
    cfgRef.update({
      symbol: "BTCUSD",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  // minConfluence > 26 rejected
  await assertFails(
    cfgRef.update({
      minConfluence: 27,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});
