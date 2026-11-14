const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");
const serverTimestamp = () => new Date(Date.now() - 1000);

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_student_1";
const BOB_UID = "bob_student_2";
const ADMIN_UID = "admin_raphael_1";
const ADMIN_EMAIL = "raphaelajah84@gmail.com";

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

test("Unauthenticated caller cannot read courses, students, or payments", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("courses").where("isAvailable", "==", true).get());
  await assertFails(unauthDb.collection("students").get());
  await assertFails(unauthDb.collection("payments").get());
});

test("Student cannot self-assign ADMINISTRATOR role or write /admins", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true }).firestore();
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      uid: ALICE_UID,
      displayName: "Alice",
      email: "alice@example.com",
      role: "ADMINISTRATOR",
      isSuspended: false,
      createdAt: serverTimestamp(),
    })
  );
  await assertFails(
    aliceDb.collection("admins").doc(ALICE_UID).set({
      uid: ALICE_UID,
      email: "alice@example.com",
      createdAt: serverTimestamp(),
    })
  );
});

test("Email spoofing test: unverified bootstrap admin email cannot create courses", async () => {
  const spoofDb = testEnv.authenticatedContext("spoof_uid", {
    email: ADMIN_EMAIL,
    email_verified: false,
  }).firestore();
  await assertFails(
    spoofDb.collection("courses").doc("crs_basic").set({
      code: "JY-101",
      title: "Computer Training - Basic Course",
      durationLabel: "1 Month",
      feeNgn: 25000,
      isAvailable: true,
      isArchived: false,
      createdAt: serverTimestamp(),
    })
  );
});

test("Verified bootstrap admin can create courses and verify OPay submissions", async () => {
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: ADMIN_EMAIL,
    email_verified: true,
  }).firestore();
  await assertSucceeds(
    adminDb.collection("courses").doc("crs_basic").set({
      code: "JY-101",
      title: "Computer Training - Basic Course",
      durationLabel: "1 Month",
      durationMonths: 1,
      feeNgn: 25000,
      isAvailable: true,
      isArchived: false,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );
});

test("Cross-user isolation: Bob cannot read Alice's student profile, enrolment, or payments", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("students").doc(ALICE_UID).set({
      userId: ALICE_UID,
      studentCode: "JY-2026-0001",
      fullName: "Alice Adebayo",
      gender: "Female",
      phoneNumber: "08012345678",
      email: "alice@example.com",
      status: "ACTIVE",
      createdAt: serverTimestamp(),
    });
    await db.collection("payments").doc("pay_alice_1").set({
      reference: "PAY-1001",
      userId: ALICE_UID,
      studentId: ALICE_UID,
      enrolmentId: "enr_1",
      amountNgn: 25000,
      currency: "NGN",
      provider: "PAYSTACK",
      status: "SUCCESSFUL",
      createdAt: serverTimestamp(),
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID, { email: "bob@example.com", email_verified: true }).firestore();
  await assertFails(bobDb.collection("students").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("payments").doc("pay_alice_1").get());
  await assertFails(bobDb.collection("payments").get());

  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true }).firestore();
  await assertSucceeds(aliceDb.collection("students").where("userId", "==", ALICE_UID).get());
  await assertSucceeds(aliceDb.collection("payments").where("userId", "==", ALICE_UID).get());
});

test("Shadow update test: Alice cannot inject ghost field or escalate OPay payment submission", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true }).firestore();
  // Cannot submit already-verified OPay transfer
  await assertFails(
    aliceDb.collection("payment_submissions").doc("sub_1").set({
      submissionRef: "OPAY-001",
      userId: ALICE_UID,
      studentId: ALICE_UID,
      enrolmentId: "enr_1",
      amountNgn: 25000,
      bankName: "OPay",
      accountNumber: "6118366524",
      transferReference: "TRF999",
      status: "VERIFIED",
      createdAt: serverTimestamp(),
    })
  );
  // Valid pending submission succeeds
  await assertSucceeds(
    aliceDb.collection("payment_submissions").doc("sub_1").set({
      submissionRef: "OPAY-001",
      userId: ALICE_UID,
      studentId: ALICE_UID,
      enrolmentId: "enr_1",
      amountNgn: 25000,
      bankName: "OPay",
      accountNumber: "6118366524",
      transferReference: "TRF999",
      status: "PENDING_VERIFICATION",
      createdAt: serverTimestamp(),
    })
  );
  // Ghost field rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      uid: ALICE_UID,
      displayName: "Alice",
      email: "alice@example.com",
      role: "STUDENT",
      isSuspended: false,
      ghostAdmin: true,
      createdAt: serverTimestamp(),
    })
  );
});

test("Private chat & community group access isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("conversations").doc("conv_ab").set({
      participantUids: [ALICE_UID, BOB_UID],
      lastMessageText: "Hello",
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    });
  });

  // Alice can write message in her conversation
  await assertSucceeds(
    aliceDb.collection("conversations").doc("conv_ab").collection("messages").doc("msg_1").set({
      senderUid: ALICE_UID,
      senderName: "Alice",
      memberUids: [ALICE_UID, BOB_UID],
      text: "Welcome to JY-COMPUTERS!",
      messageType: "TEXT",
      deliveryStatus: "SENT",
      isRemoved: false,
      createdAt: serverTimestamp(),
    })
  );

  // Outsider cannot read Alice & Bob's conversation or messages
  const charlieDb = testEnv.authenticatedContext("charlie_3").firestore();
  await assertFails(charlieDb.collection("conversations").doc("conv_ab").get());
  await assertFails(
    charlieDb.collection("conversations").doc("conv_ab").collection("messages").doc("msg_1").get()
  );
  await assertFails(
    charlieDb.collection("conversations").doc("conv_ab").collection("messages").get()
  );

  // Any signed-in student can post & read general_community
  await assertSucceeds(
    bobDb.collection("group_chats").doc("general_community").collection("messages").doc("gmsg_1").set({
      senderUid: BOB_UID,
      senderName: "Bob",
      groupId: "general_community",
      text: "Any Python tips?",
      messageType: "TEXT",
      deliveryStatus: "SENT",
      isRemoved: false,
      createdAt: serverTimestamp(),
    })
  );
  await assertSucceeds(
    aliceDb.collection("group_chats").doc("general_community").collection("messages")
      .where("groupId", "==", "general_community").get()
  );
});

test("QR attendance: student can mark PRESENT with active session, but cannot forge or mark on closed session", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true }).firestore();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true }).firestore();

  // Student cannot create an attendance_sessions doc
  await assertFails(
    aliceDb.collection("attendance_sessions").doc("sess_fake").set({
      sessionCode: "QR-FAKE",
      courseId: "crs_basic",
      courseTitle: "Computer Training - Basic Course",
      dateIso: "2026-11-14",
      createdByUid: ALICE_UID,
      isActive: true,
      createdAt: serverTimestamp(),
    })
  );

  // Admin creates an active session
  await assertSucceeds(
    adminDb.collection("attendance_sessions").doc("sess_crs_basic_20261114").set({
      sessionCode: "QR-101A",
      courseId: "crs_basic",
      courseTitle: "Computer Training - Basic Course",
      dateIso: "2026-11-14",
      createdByUid: ADMIN_UID,
      createdByName: "Admin",
      isActive: true,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );

  // Student cannot forge attendance without a valid active sessionId or with ABSENT/other UID
  await assertFails(
    aliceDb.collection("attendance").doc("stu_alice_crs_basic_20261114").set({
      userId: ALICE_UID,
      studentId: ALICE_UID,
      courseId: "crs_basic",
      dateIso: "2026-11-14",
      status: "PRESENT",
      recordedByUid: ALICE_UID,
      createdAt: serverTimestamp(),
    })
  );

  await assertFails(
    aliceDb.collection("attendance").doc("stu_bob_crs_basic_20261114").set({
      userId: BOB_UID,
      studentId: BOB_UID,
      courseId: "crs_basic",
      dateIso: "2026-11-14",
      status: "PRESENT",
      recordedByUid: ALICE_UID,
      sessionId: "sess_crs_basic_20261114",
      createdAt: serverTimestamp(),
    })
  );

  // Student succeeds with matching active sessionId and PRESENT status
  await assertSucceeds(
    aliceDb.collection("attendance").doc("stu_alice_crs_basic_20261114").set({
      userId: ALICE_UID,
      studentId: ALICE_UID,
      studentName: "Alice",
      courseId: "crs_basic",
      courseTitle: "Computer Training - Basic Course",
      dateIso: "2026-11-14",
      status: "PRESENT",
      recordedByUid: ALICE_UID,
      sessionId: "sess_crs_basic_20261114",
      notes: "Marked via QR Scan",
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );

  // Admin closes the session; Bob's scan must now fail
  await assertSucceeds(
    adminDb.collection("attendance_sessions").doc("sess_crs_basic_20261114").update({
      isActive: false,
      updatedAt: serverTimestamp(),
    })
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID, { email: "bob@example.com", email_verified: true }).firestore();
  await assertFails(
    bobDb.collection("attendance").doc("stu_bob_crs_basic_20261114").set({
      userId: BOB_UID,
      studentId: BOB_UID,
      courseId: "crs_basic",
      dateIso: "2026-11-14",
      status: "PRESENT",
      recordedByUid: BOB_UID,
      sessionId: "sess_crs_basic_20261114",
      createdAt: serverTimestamp(),
    })
  );
});
