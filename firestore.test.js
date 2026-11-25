const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";

const ADMIN_UID = "admin_owner_1";
const ADMIN_EMAIL = "siyaramp138@gmail.com";

const EMP_UID = "emp_rahul_2";
const EMP_EMAIL = "rahul.bdent@gmail.com";

const EMP2_UID = "emp_amit_3";
const EMP2_EMAIL = "amit.bdent@gmail.com";

const OUTSIDER_UID = "outsider_99";
const OUTSIDER_EMAIL = "random.user@gmail.com";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastTimestamp(msAgo = 5000) {
  return new Date(Date.now() - msAgo);
}

function adminContext() {
  return testEnv.authenticatedContext(ADMIN_UID, {
    email: ADMIN_EMAIL,
    email_verified: true,
  });
}

function employeeContext() {
  return testEnv.authenticatedContext(EMP_UID, {
    email: EMP_EMAIL,
    email_verified: true,
  });
}

function employee2Context() {
  return testEnv.authenticatedContext(EMP2_UID, {
    email: EMP2_EMAIL,
    email_verified: true,
  });
}

function outsiderContext() {
  return testEnv.authenticatedContext(OUTSIDER_UID, {
    email: OUTSIDER_EMAIL,
    email_verified: true,
  });
}

function spoofedUnverifiedAdminContext() {
  return testEnv.authenticatedContext("spoof_uid", {
    email: ADMIN_EMAIL,
    email_verified: false,
  });
}

function staffPayload(email, role = "EMPLOYEE", active = true) {
  const ts = pastTimestamp(10000);
  return {
    email,
    name: "Rahul Verma",
    role,
    active,
    addedByEmail: ADMIN_EMAIL,
    createdAt: ts,
    updatedAt: ts,
  };
}

function partyPayload(uid = ADMIN_UID) {
  const ts = pastTimestamp(10000);
  return {
    createdByUid: uid,
    createdByName: "BD Admin",
    name: "Indore Electricals",
    mobile: "9876543210",
    gstOrAddress: "23ABCDE1234F1Z5, Indore",
    partyType: "CUSTOMER",
    openingBalance: 1200.0,
    createdAt: ts,
    updatedAt: ts,
  };
}

function productPayload(uid = ADMIN_UID) {
  const ts = pastTimestamp(10000);
  return {
    createdByUid: uid,
    name: "Copper Cable 2.5mm",
    sku: "CC-250",
    unit: "Box",
    category: "Electrical",
    location: "Rack A-2",
    purchaseRate: 900.0,
    sellingRate: 1250.0,
    stockQty: 30.0,
    minAlertLevel: 5.0,
    createdAt: ts,
    updatedAt: ts,
  };
}

function stockMovementPayload(uid = ADMIN_UID, productId = "prod_1", changeQty = 10.0, movementType = "RESTOCK") {
  return {
    productId,
    productName: "Copper Cable 2.5mm",
    sku: "CC-250",
    unit: "Box",
    changeQty,
    movementType,
    reason: "Supplier delivery",
    performedByUid: uid,
    performedByName: "BD Admin",
    createdAt: pastTimestamp(4000),
  };
}

function orderPayload(uid = EMP_UID, partyId = "party_1", documentType = "ORDER", photoBase64 = "") {
  const ts = pastTimestamp(8000);
  return {
    createdByUid: uid,
    createdByName: uid === EMP2_UID ? "Amit Joshi" : "Rahul Verma",
    orderNumber: documentType === "ESTIMATE" ? "EST-2026-0001" : "BD-2026-0001",
    documentType,
    partyId,
    partyName: "Indore Electricals",
    partyMobile: "9876543210",
    partyGstOrAddress: "23ABCDE1234F1Z5, Indore",
    orderLocation: "Vijay Nagar, Indore",
    orderDateMillis: Date.now() - 5000,
    status: "PENDING",
    items: [
      {
        productId: "prod_1",
        productName: "Copper Cable 2.5mm",
        sku: "CC-250",
        unit: "Box",
        quantity: 2.0,
        unitPrice: 1250.0,
        lineTotal: 2500.0,
      },
    ],
    subtotal: 2500.0,
    taxPercent: 18.0,
    taxAmount: 450.0,
    discountAmount: 50.0,
    grandTotal: 2900.0,
    advancePaid: documentType === "ESTIMATE" ? 0.0 : 1000.0,
    notes: documentType === "ESTIMATE" ? "Sale estimate quote" : "Urgent dispatch",
    photoBase64,
    createdAt: ts,
    updatedAt: ts,
  };
}

function paymentPayload(uid = EMP_UID, partyId = "party_1") {
  const ts = pastTimestamp(6000);
  return {
    createdByUid: uid,
    createdByName: "Rahul Verma",
    partyId,
    partyName: "Indore Electricals",
    orderId: "order_1",
    orderNumber: "BD-2026-0001",
    amount: 1000.0,
    paymentMode: "UPI",
    paymentDateMillis: Date.now() - 4000,
    referenceNote: "UPI Ref 98123",
    createdAt: ts,
    updatedAt: ts,
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

test("Security: unauthenticated, outsider, and unverified spoofed admin are denied all business access", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("parties").get());
  await assertFails(unauthDb.collection("products").get());
  await assertFails(unauthDb.collection("stock_movements").get());
  await assertFails(unauthDb.collection("staff").get());

  const outsiderDb = outsiderContext().firestore();
  await assertFails(outsiderDb.collection("parties").get());
  await assertFails(outsiderDb.collection("products").get());
  await assertFails(outsiderDb.collection("stock_movements").get());
  await assertFails(outsiderDb.collection("orders").get());
  await assertFails(outsiderDb.collection("payments").get());
  await assertFails(outsiderDb.collection("staff").get());
  await assertFails(
    outsiderDb.collection("staff").doc(OUTSIDER_EMAIL).set(staffPayload(OUTSIDER_EMAIL, "ADMIN"))
  );

  const spoofDb = spoofedUnverifiedAdminContext().firestore();
  await assertFails(spoofDb.collection("products").get());
  await assertFails(spoofDb.collection("stock_movements").get());
  await assertFails(spoofDb.collection("staff").get());
});

test("Inventory CRUD & Audit Ledger: Add, Update, Adjust Stock + Movement Log, and Remove", async () => {
  const adminDb = adminContext().firestore();
  const empDb = employeeContext().firestore();

  await assertSucceeds(
    adminDb.collection("staff").doc(EMP_EMAIL).set(staffPayload(EMP_EMAIL, "EMPLOYEE", true))
  );

  // Atomic batch: create product + INITIAL_STOCK movement (verified via existsAfter)
  const createBatch = adminDb.batch();
  createBatch.set(adminDb.collection("products").doc("prod_1"), productPayload(ADMIN_UID));
  createBatch.set(
    adminDb.collection("stock_movements").doc("mov_init"),
    stockMovementPayload(ADMIN_UID, "prod_1", 30.0, "INITIAL_STOCK")
  );
  await assertSucceeds(createBatch.commit());

  // Admin updates category, location, and sellingRate
  await assertSucceeds(
    adminDb.collection("products").doc("prod_1").update({
      category: "Cables",
      location: "Warehouse B-4",
      sellingRate: 1320.0,
      updatedAt: pastTimestamp(1000),
    })
  );

  // Employee adjusts stock quantity & appends movement log atomically
  const adjustBatch = empDb.batch();
  adjustBatch.update(empDb.collection("products").doc("prod_1"), {
    stockQty: 40.0,
    updatedAt: pastTimestamp(800),
  });
  adjustBatch.set(
    empDb.collection("stock_movements").doc("mov_restock"),
    stockMovementPayload(EMP_UID, "prod_1", 10.0, "RESTOCK")
  );
  await assertSucceeds(adjustBatch.commit());

  // Stock movement audit log is immutable (even Admin cannot update an existing audit entry)
  await assertFails(
    adminDb.collection("stock_movements").doc("mov_restock").update({
      changeQty: 999.0,
    })
  );

  // Orphan stock movement referencing a missing product MUST fail
  await assertFails(
    adminDb
      .collection("stock_movements")
      .doc("mov_orphan")
      .set(stockMovementPayload(ADMIN_UID, "missing_prod", 5.0, "RESTOCK"))
  );

  // Zero-delta movement MUST fail
  await assertFails(
    adminDb
      .collection("stock_movements")
      .doc("mov_zero")
      .set(stockMovementPayload(ADMIN_UID, "prod_1", 0.0, "RESTOCK"))
  );

  // Employee cannot delete products or audit movements; Admin can remove product
  await assertFails(empDb.collection("products").doc("prod_1").delete());
  await assertFails(empDb.collection("stock_movements").doc("mov_restock").delete());
  await assertSucceeds(adminDb.collection("products").doc("prod_1").delete());
});

test("RBAC: Admin registers Employee; Employee runs daily ops but cannot delete, edit prices, or manage staff", async () => {
  const adminDb = adminContext().firestore();
  const empDb = employeeContext().firestore();

  await assertSucceeds(
    adminDb.collection("staff").doc(EMP_EMAIL).set(staffPayload(EMP_EMAIL, "EMPLOYEE", true))
  );

  await assertSucceeds(empDb.collection("staff").doc(EMP_EMAIL).get());
  await assertFails(empDb.collection("staff").get());
  await assertFails(
    empDb.collection("staff").doc(EMP_EMAIL).update({ role: "ADMIN", updatedAt: pastTimestamp(1000) })
  );

  await assertSucceeds(adminDb.collection("parties").doc("party_1").set(partyPayload(ADMIN_UID)));
  await assertSucceeds(adminDb.collection("products").doc("prod_1").set(productPayload(ADMIN_UID)));

  await assertSucceeds(empDb.collection("parties").get());
  await assertSucceeds(empDb.collection("products").get());
  await assertSucceeds(empDb.collection("orders").doc("order_1").set(orderPayload(EMP_UID, "party_1")));
  await assertSucceeds(
    empDb.collection("products").doc("prod_1").update({ stockQty: 28.0, updatedAt: pastTimestamp(2000) })
  );
  await assertSucceeds(empDb.collection("payments").doc("pay_1").set(paymentPayload(EMP_UID, "party_1")));

  await assertSucceeds(
    empDb.collection("orders").doc("order_1").update({
      orderLocation: "Dewas Naka, Indore",
      updatedAt: pastTimestamp(1800),
    })
  );
  await assertFails(
    empDb.collection("orders").doc("order_1").update({
      orderLocation: "X".repeat(161),
      updatedAt: pastTimestamp(1700),
    })
  );
  await assertSucceeds(
    empDb.collection("orders").doc("order_1").update({ status: "DISPATCHED", updatedAt: pastTimestamp(1500) })
  );
  await assertSucceeds(
    empDb.collection("orders").doc("order_1").update({ status: "DELIVERED", updatedAt: pastTimestamp(1000) })
  );
  await assertFails(
    empDb.collection("orders").doc("order_1").update({ status: "PENDING", updatedAt: pastTimestamp(500) })
  );

  await assertFails(
    empDb.collection("products").doc("prod_1").update({ sellingRate: 1.0, updatedAt: pastTimestamp(500) })
  );
  await assertFails(empDb.collection("parties").doc("party_1").delete());
  await assertFails(empDb.collection("products").doc("prod_1").delete());
  await assertFails(empDb.collection("orders").doc("order_1").delete());
  await assertFails(empDb.collection("payments").doc("pay_1").delete());

  await assertSucceeds(
    adminDb.collection("products").doc("prod_1").update({ sellingRate: 1300.0, updatedAt: pastTimestamp(400) })
  );
  await assertSucceeds(adminDb.collection("payments").doc("pay_1").delete());
  await assertSucceeds(adminDb.collection("orders").doc("order_1").delete());
});

test("RBAC Revocation: Suspending employee immediately revokes all business reads/writes", async () => {
  const adminDb = adminContext().firestore();
  const empDb = employeeContext().firestore();

  await assertSucceeds(
    adminDb.collection("staff").doc(EMP_EMAIL).set(staffPayload(EMP_EMAIL, "EMPLOYEE", true))
  );
  await assertSucceeds(empDb.collection("products").get());

  await assertSucceeds(
    adminDb.collection("staff").doc(EMP_EMAIL).update({ active: false, updatedAt: pastTimestamp(1000) })
  );

  await assertFails(empDb.collection("parties").get());
  await assertFails(empDb.collection("products").get());
  await assertFails(empDb.collection("stock_movements").get());
  await assertFails(empDb.collection("orders").get());
  await assertFails(empDb.collection("products").doc("p_x").set(productPayload(EMP_UID)));
});

test("Schema Integrity: Ghost fields, embedded @DocumentId 'id', future timestamps, and orphan orders rejected", async () => {
  const adminDb = adminContext().firestore();
  await assertSucceeds(adminDb.collection("parties").doc("party_1").set(partyPayload(ADMIN_UID)));

  await assertFails(
    adminDb.collection("products").doc("prod_ghost").set({
      ...productPayload(ADMIN_UID),
      isVerified: true,
    })
  );
  await assertFails(
    adminDb.collection("products").doc("prod_id_err").set({
      ...productPayload(ADMIN_UID),
      id: "prod_id_err",
    })
  );
  await assertFails(
    adminDb.collection("products").doc("prod_future").set({
      ...productPayload(ADMIN_UID),
      createdAt: new Date(Date.now() + 60000),
    })
  );
  await assertFails(
    adminDb.collection("orders").doc("order_orphan").set(orderPayload(ADMIN_UID, "missing_party"))
  );
  await assertFails(
    adminDb.collection("orders").doc("order_bad_type").set(orderPayload(ADMIN_UID, "party_1", "QUOTATION"))
  );
  await assertFails(
    adminDb.collection("orders").doc("order_huge_photo").set(orderPayload(ADMIN_UID, "party_1", "ORDER", "A".repeat(400001)))
  );
});

test("Employee Creator-Only Isolation & Sale Estimates with Camera Photo", async () => {
  const adminDb = adminContext().firestore();
  const emp1Db = employeeContext().firestore();
  const emp2Db = employee2Context().firestore();

  await assertSucceeds(
    adminDb.collection("staff").doc(EMP_EMAIL).set(staffPayload(EMP_EMAIL, "EMPLOYEE", true))
  );
  await assertSucceeds(
    adminDb.collection("staff").doc(EMP2_EMAIL).set(staffPayload(EMP2_EMAIL, "EMPLOYEE", true))
  );
  await assertSucceeds(adminDb.collection("parties").doc("party_1").set(partyPayload(ADMIN_UID)));

  // Admin creates an order; Emp1 creates an order & estimate with photo; Emp2 creates an estimate
  await assertSucceeds(adminDb.collection("orders").doc("admin_ord").set(orderPayload(ADMIN_UID, "party_1", "ORDER", "")));
  await assertSucceeds(emp1Db.collection("orders").doc("emp1_ord").set(orderPayload(EMP_UID, "party_1", "ORDER", "base64thumb123")));
  await assertSucceeds(emp1Db.collection("orders").doc("emp1_est").set(orderPayload(EMP_UID, "party_1", "ESTIMATE", "base64estphoto")));
  await assertSucceeds(emp2Db.collection("orders").doc("emp2_est").set(orderPayload(EMP2_UID, "party_1", "ESTIMATE", "")));

  // Emp1 CAN list own orders/estimates via scoped where("createdByUid", "==", EMP_UID)
  await assertSucceeds(emp1Db.collection("orders").where("createdByUid", "==", EMP_UID).get());
  await assertSucceeds(emp1Db.collection("orders").doc("emp1_est").get());

  // Emp1 CANNOT list all orders unfiltered or query another user's orders/estimates
  await assertFails(emp1Db.collection("orders").get());
  await assertFails(emp1Db.collection("orders").where("createdByUid", "==", EMP2_UID).get());
  await assertFails(emp1Db.collection("orders").where("createdByUid", "==", ADMIN_UID).get());

  // Emp1 CANNOT read or update Admin's or Emp2's order/estimate
  await assertFails(emp1Db.collection("orders").doc("admin_ord").get());
  await assertFails(emp1Db.collection("orders").doc("emp2_est").get());
  await assertFails(
    emp1Db.collection("orders").doc("emp2_est").update({ notes: "Tampered", updatedAt: pastTimestamp(500) })
  );

  // Emp1 CAN update own photoBase64 and modify own Order/Estimate items, totals, location, and notes
  await assertSucceeds(
    emp1Db.collection("orders").doc("emp1_est").update({ photoBase64: "updatedBase64Img", updatedAt: pastTimestamp(500) })
  );
  await assertSucceeds(
    emp1Db.collection("orders").doc("emp1_est").update({
      orderLocation: "Local Indore — Vijay Nagar",
      items: [
        {
          productId: "prod_1",
          productName: "Copper Cable 2.5mm",
          sku: "CC-250",
          unit: "Carton",
          quantity: 5.0,
          unitPrice: 1200.0,
          lineTotal: 6000.0,
        },
      ],
      subtotal: 6000.0,
      discountAmount: 100.0,
      taxPercent: 0.0,
      taxAmount: 0.0,
      grandTotal: 5900.0,
      notes: "Revised estimate quantity",
      updatedAt: pastTimestamp(400),
    })
  );

  // Emp1 CANNOT point order to a non-existent party or hijack createdByUid
  await assertFails(
    emp1Db.collection("orders").doc("emp1_est").update({
      partyId: "nonexistent_party",
      updatedAt: pastTimestamp(300),
    })
  );
  await assertFails(
    emp1Db.collection("orders").doc("emp1_est").update({
      createdByUid: ADMIN_UID,
      updatedAt: pastTimestamp(300),
    })
  );

  // Admin CAN list ALL orders & estimates unfiltered, read anyone's record, and modify any order/estimate
  await assertSucceeds(adminDb.collection("orders").get());
  await assertSucceeds(adminDb.collection("orders").doc("emp1_est").get());
  await assertSucceeds(adminDb.collection("orders").doc("emp2_est").get());
  await assertSucceeds(
    adminDb.collection("orders").doc("emp2_est").update({
      orderLocation: "Out of Indore — Dewas",
      grandTotal: 3100.0,
      subtotal: 3100.0,
      notes: "Admin verified rate",
      updatedAt: pastTimestamp(200),
    })
  );
});


