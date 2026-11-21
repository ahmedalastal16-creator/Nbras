const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_student_1";
const BOB_UID = "bob_student_2";
const CONV_ID = "pkg_tawjihi_scientific_1";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
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

test("Unauthenticated guest cannot read or write any user conversations or messages", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const now = new Date(Date.now() - 1000);

  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("conversations").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc(CONV_ID)
      .set({
        userId: ALICE_UID,
        stageId: "tawjihi_scientific",
        stageTitle: "التوجيهي — الفرع العلمي",
        subjectTitle: "الفيزياء",
        lastMessagePreview: "اشرح الزخم",
        messageCount: 1,
        createdAt: now,
        updatedAt: now,
      })
  );
});

test("Authenticated owner: atomic batch create + subsequent partial update + message append succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const t1 = new Date(Date.now() - 5000);
  const t2 = new Date(Date.now() - 1000);

  const convRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("conversations")
    .doc(CONV_ID);
  const msg1Ref = convRef.collection("messages").doc("msg_1");

  // Atomic batch write creating conversation summary and first message (NO `id` field)
  const batch = aliceDb.batch();
  batch.set(convRef, {
    userId: ALICE_UID,
    stageId: "tawjihi_scientific",
    stageTitle: "التوجيهي — الفرع العلمي",
    subjectTitle: "الفيزياء",
    lastMessagePreview: "ما هو قانون حفظ الزخم؟",
    messageCount: 1,
    createdAt: t1,
    updatedAt: t1,
  });
  batch.set(msg1Ref, {
    userId: ALICE_UID,
    conversationId: CONV_ID,
    role: "user",
    text: "ما هو قانون حفظ الزخم؟",
    createdAt: t1,
  });
  await assertSucceeds(batch.commit());

  // Partial update on conversation + model message with optional imageUri omitted
  const msg2Ref = convRef.collection("messages").doc("msg_2");
  const batch2 = aliceDb.batch();
  batch2.update(convRef, {
    lastMessagePreview: "يبقى الزخم الخطي الكلي ثابتاً في النظام المعزول.",
    messageCount: 2,
    updatedAt: t2,
  });
  batch2.set(msg2Ref, {
    userId: ALICE_UID,
    conversationId: CONV_ID,
    role: "model",
    text: "يبقى الزخم الخطي الكلي ثابتاً في النظام المعزول.",
    createdAt: t2,
  });
  await assertSucceeds(batch2.commit());

  // Verify Kotlin repository list queries succeed
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .orderBy("updatedAt", "desc")
      .get()
  );
  await assertSucceeds(
    convRef.collection("messages").orderBy("createdAt", "asc").get()
  );
});

test("Cross-user isolation: Bob cannot read, write, or delete Alice's conversations or messages", async () => {
  const now = new Date(Date.now() - 5000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    const convRef = db
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc(CONV_ID);
    await convRef.set({
      userId: ALICE_UID,
      stageId: "tawjihi_scientific",
      stageTitle: "التوجيهي — الفرع العلمي",
      subjectTitle: "الفيزياء",
      lastMessagePreview: "مرحباً",
      messageCount: 1,
      createdAt: now,
      updatedAt: now,
    });
    await convRef.collection("messages").doc("msg_1").set({
      userId: ALICE_UID,
      conversationId: CONV_ID,
      role: "user",
      text: "مرحباً",
      createdAt: now,
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const aliceConv = bobDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("conversations")
    .doc(CONV_ID);

  await assertFails(aliceConv.get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("conversations").get()
  );
  await assertFails(aliceConv.collection("messages").get());
  await assertFails(aliceConv.delete());
});

test("Security & Schema Red Team: rejects ghost fields, immutable mutation, @DocumentId leak, bad role, and future timestamps", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date(Date.now() - 5000);
  const future = new Date(Date.now() + 120000);
  const convRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("conversations")
    .doc(CONV_ID);

  // Reject ghost field / @DocumentId `id` leak
  await assertFails(
    convRef.set({
      id: CONV_ID,
      userId: ALICE_UID,
      stageId: "tawjihi_scientific",
      stageTitle: "التوجيهي — الفرع العلمي",
      subjectTitle: "الفيزياء",
      lastMessagePreview: "تجربة",
      messageCount: 1,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Reject future timestamp
  await assertFails(
    convRef.set({
      userId: ALICE_UID,
      stageId: "tawjihi_scientific",
      stageTitle: "التوجيهي — الفرع العلمي",
      subjectTitle: "الفيزياء",
      lastMessagePreview: "تجربة",
      messageCount: 1,
      createdAt: future,
      updatedAt: future,
    })
  );

  // Reject orphan message without parent conversation
  await assertFails(
    convRef.collection("messages").doc("orphan_1").set({
      userId: ALICE_UID,
      conversationId: CONV_ID,
      role: "user",
      text: "رسالة يتيمة",
      createdAt: now,
    })
  );

  // Create valid parent conversation
  await assertSucceeds(
    convRef.set({
      userId: ALICE_UID,
      stageId: "tawjihi_scientific",
      stageTitle: "التوجيهي — الفرع العلمي",
      subjectTitle: "الفيزياء",
      lastMessagePreview: "تجربة",
      messageCount: 1,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Reject immutable field mutation (createdAt / stageId)
  await assertFails(
    convRef.update({
      stageId: "tawjihi_literary",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    convRef.update({
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject invalid role on message
  await assertFails(
    convRef.collection("messages").doc("bad_role").set({
      userId: ALICE_UID,
      conversationId: CONV_ID,
      role: "admin",
      text: "محتوى غير صالح",
      createdAt: now,
    })
  );

  // Reject explicit null imageUri
  await assertFails(
    convRef.collection("messages").doc("null_img").set({
      userId: ALICE_UID,
      conversationId: CONV_ID,
      role: "user",
      text: "سؤال",
      imageUri: null,
      createdAt: now,
    })
  );
});

test("Cloud Study Summaries: owner CRUD & query succeed; unauth, cross-user, and schema violations fail", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const t1 = new Date(Date.now() - 5000);
  const t2 = new Date(Date.now() - 1000);

  const summaryId = "sum_physics_1";
  const aliceSummaryRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("summaries")
    .doc(summaryId);

  // Unauthenticated denied
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("summaries").get()
  );

  // Owner create (no @DocumentId id key)
  await assertSucceeds(
    aliceSummaryRef.set({
      userId: ALICE_UID,
      stageId: "tawjihi_scientific",
      stageTitle: "التوجيهي — الفرع العلمي",
      subjectTitle: "الفيزياء",
      title: "ملخص الزخم الخطي والدفع",
      content: "الزخم الخطي يساوي حاصل ضرب الكتلة في السرعة المتجهة.",
      keyTerms: "الزخم • الدفع • حفظ الزخم",
      sourceType: "manual",
      createdAt: t1,
      updatedAt: t1,
    })
  );

  // Owner list query ordered by updatedAt desc
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("summaries")
      .orderBy("updatedAt", "desc")
      .get()
  );

  // Owner partial update preserving userId, sourceType, createdAt
  await assertSucceeds(
    aliceSummaryRef.update({
      title: "ملخص الزخم الخطي والدفع (محدّث)",
      content: "قانون حفظ الزخم يطبق في الأنظمة المعزولة.",
      updatedAt: t2,
    })
  );

  // Reject immutable field mutation (sourceType / createdAt)
  await assertFails(
    aliceSummaryRef.update({
      sourceType: "tutor",
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    aliceSummaryRef.update({
      createdAt: t2,
      updatedAt: t2,
    })
  );

  // Reject @DocumentId id leak and invalid sourceType
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("summaries").doc("sum_bad_1").set({
      id: "sum_bad_1",
      userId: ALICE_UID,
      stageId: "tawjihi_scientific",
      stageTitle: "التوجيهي — الفرع العلمي",
      subjectTitle: "الفيزياء",
      title: "تجربة",
      content: "محتوى",
      keyTerms: "",
      sourceType: "manual",
      createdAt: t1,
      updatedAt: t1,
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("summaries").doc("sum_bad_2").set({
      userId: ALICE_UID,
      stageId: "tawjihi_scientific",
      stageTitle: "التوجيهي — الفرع العلمي",
      subjectTitle: "الفيزياء",
      title: "تجربة",
      content: "محتوى",
      keyTerms: "",
      sourceType: "admin",
      createdAt: t1,
      updatedAt: t1,
    })
  );

  // Cross-user isolation
  const bobViewAlice = bobDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("summaries")
    .doc(summaryId);
  await assertFails(bobViewAlice.get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("summaries").get()
  );
  await assertFails(bobViewAlice.update({ title: "اختراق", updatedAt: t2 }));
  await assertFails(bobViewAlice.delete());

  // Owner delete succeeds
  await assertSucceeds(aliceSummaryRef.delete());
});

