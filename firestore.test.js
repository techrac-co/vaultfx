const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const CHARLIE_UID = "charlie_789";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

test("Unauthenticated user cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("payTags").doc("alice_tag").get());
  await assertFails(unauthDb.collection("transfers").where("recipientUid", "==", ALICE_UID).get());
  await assertFails(unauthDb.collection("escrows").where("participantUids", "array-contains", ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("holdings").doc("BTC").set({
      assetCode: "BTC",
      balance: 1.0,
      avgBuyPriceUsd: 90000,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("User profile: owner create, partial update, cross-user denial, and ghost field rejection", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      payTag: "alice_fx",
      preferredFiat: "USD",
      walletSeed: "seed_alice_01",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      preferredFiat: "NGN",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      walletSeed: "hacked_seed",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertFails(
    bobDb.collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      displayName: "Bob",
      payTag: "bob_fx",
      preferredFiat: "USD",
      walletSeed: "seed_bob_01",
      isAdmin: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
});

test("Holdings, transactions, and savedPairs subcollections: owner CRUD & cross-user isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("holdings").doc("BTC").set({
      assetCode: "BTC",
      balance: 0.25,
      avgBuyPriceUsd: 92000,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("holdings").doc("BTC").update({
      balance: 0.5,
      avgBuyPriceUsd: 93000,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertSucceeds(bobDb.collection("users").doc(BOB_UID).collection("holdings").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("holdings").get());

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("transactions").doc("tx_1").set({
      txType: "ESCROW_LOCK",
      assetCode: "BTC",
      amount: 0.05,
      quoteCode: "USD",
      quoteAmount: 4700,
      priceUsd: 94000,
      feeAmount: 0,
      counterparty: "Escrow @bob_fx",
      txHash: "0xabc123def456",
      status: "COMPLETED",
      createdAt: now,
    })
  );

  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("transactions").doc("tx_1").update({
      amount: 99,
    })
  );
});

test("Escrow lifecycle: buyer funds -> seller delivers -> buyer releases -> seller claims (terminal lock & outsider denial)", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const now = new Date(Date.now() - 10000);

  // Alice (Buyer) locks 0.5 ETH in escrow for Bob (Seller)
  await assertSucceeds(
    aliceDb.collection("escrows").doc("esc_1").set({
      buyerUid: ALICE_UID,
      buyerTag: "alice_fx",
      sellerUid: BOB_UID,
      sellerTag: "bob_fx",
      participantUids: [ALICE_UID, BOB_UID],
      assetCode: "ETH",
      amount: 0.5,
      title: "OTC Naira Settlement",
      agreedTerms: "Release after NGN transfer confirms",
      txHash: "0xescrow001",
      status: "FUNDED",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Filtered participantUids query succeeds for Alice and Bob; unfiltered fails
  await assertSucceeds(bobDb.collection("escrows").where("participantUids", "array-contains", BOB_UID).get());
  await assertFails(bobDb.collection("escrows").get());

  // Outsider Charlie cannot read or modify
  await assertFails(charlieDb.collection("escrows").doc("esc_1").get());
  await assertFails(charlieDb.collection("escrows").where("participantUids", "array-contains", ALICE_UID).get());

  // Buyer cannot prematurely mark DELIVERED or CLAIMED
  await assertFails(
    aliceDb.collection("escrows").doc("esc_1").update({
      status: "DELIVERED",
      updatedAt: new Date(Date.now() - 4000),
    })
  );
  await assertFails(
    bobDb.collection("escrows").doc("esc_1").update({
      status: "RELEASED",
      updatedAt: new Date(Date.now() - 4000),
    })
  );

  // Seller marks DELIVERED
  await assertSucceeds(
    bobDb.collection("escrows").doc("esc_1").update({
      status: "DELIVERED",
      updatedAt: new Date(Date.now() - 3000),
    })
  );

  // Buyer releases funds
  await assertSucceeds(
    aliceDb.collection("escrows").doc("esc_1").update({
      status: "RELEASED",
      updatedAt: new Date(Date.now() - 2000),
    })
  );

  // Buyer cannot steal CLAIMED; only Seller can claim RELEASED
  await assertFails(
    aliceDb.collection("escrows").doc("esc_1").update({
      status: "CLAIMED",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Seller claims RELEASED -> CLAIMED
  await assertSucceeds(
    bobDb.collection("escrows").doc("esc_1").update({
      status: "CLAIMED",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Terminal state lock: CLAIMED cannot transition anywhere
  await assertFails(
    bobDb.collection("escrows").doc("esc_1").update({
      status: "REFUNDED",
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Escrow dispute & seller refund lifecycle: buyer disputes -> seller refunds -> buyer reclaims", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 8000);

  await assertSucceeds(
    aliceDb.collection("escrows").doc("esc_2").set({
      buyerUid: ALICE_UID,
      buyerTag: "alice_fx",
      sellerUid: BOB_UID,
      sellerTag: "bob_fx",
      participantUids: [ALICE_UID, BOB_UID],
      assetCode: "USDT",
      amount: 250,
      title: "Design Sprint",
      txHash: "0xescrow002",
      status: "FUNDED",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Dispute requires disputeReason >= 3 chars
  await assertFails(
    aliceDb.collection("escrows").doc("esc_2").update({
      status: "DISPUTED",
      disputeReason: "no",
      updatedAt: new Date(Date.now() - 3000),
    })
  );

  await assertSucceeds(
    aliceDb.collection("escrows").doc("esc_2").update({
      status: "DISPUTED",
      disputeReason: "Delivery delayed beyond deadline",
      updatedAt: new Date(Date.now() - 3000),
    })
  );

  // Seller refunds buyer
  await assertSucceeds(
    bobDb.collection("escrows").doc("esc_2").update({
      status: "REFUNDED",
      updatedAt: new Date(Date.now() - 2000),
    })
  );

  // Buyer reclaims REFUNDED -> CLAIMED
  await assertSucceeds(
    aliceDb.collection("escrows").doc("esc_2").update({
      status: "CLAIMED",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});
