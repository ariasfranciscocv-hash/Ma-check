const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ADMIN_UID = "admin_001";
const ADMIN_EMAIL = "arias.francisco.cv@gmail.com";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastTime(msAgo = 2000) {
  return new Date(Date.now() - msAgo);
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

test("Unauthenticated users cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("tasks").get());
  await assertFails(unauthDb.collection("task_evidences").get());
  await assertFails(unauthDb.collection("sales_entries").get());
  await assertFails(unauthDb.collection("biweekly_quotas").where("active", "==", true).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@example.com",
      role: "EMPLOYEE",
      createdAt: pastTime(),
      updatedAt: pastTime(),
    })
  );
});

test("Role security: employee cannot self-assign ADMIN; spoofed unverified admin email is rejected", async () => {
  const aliceDb = testEnv
    .authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true })
    .firestore();

  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@example.com",
      role: "ADMIN",
      createdAt: pastTime(),
      updatedAt: pastTime(),
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@example.com",
      role: "EMPLOYEE",
      createdAt: pastTime(),
      updatedAt: pastTime(),
    })
  );

  // Selecting valid Sucursal succeeds (including Sucursal 87)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      branchName: "Sucursal 87",
      displayName: "Sucursal 87",
      updatedAt: pastTime(650),
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      branchName: "Sucursal 107",
      displayName: "Sucursal 107",
      updatedAt: pastTime(600),
    })
  );

  // Invalid Sucursal is rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      branchName: "Sucursal 999",
      displayName: "Sucursal 999",
      updatedAt: pastTime(550),
    })
  );

  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      role: "ADMIN",
      updatedAt: pastTime(500),
    })
  );

  const spoofDb = testEnv
    .authenticatedContext("spoof_uid", { email: ADMIN_EMAIL, email_verified: false })
    .firestore();
  await assertFails(spoofDb.collection("task_evidences").get());
  await assertFails(
    spoofDb.collection("users").doc("spoof_uid").set({
      userId: "spoof_uid",
      displayName: "Spoof",
      email: ADMIN_EMAIL,
      role: "ADMIN",
      createdAt: pastTime(),
      updatedAt: pastTime(),
    })
  );
});

test("Tasks & private photo evidence: employees manage only own tasks; ONLY admins read photos", async () => {
  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true })
    .firestore();
  const aliceDb = testEnv
    .authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true })
    .firestore();
  const bobDb = testEnv
    .authenticatedContext(BOB_UID, { email: "bob@example.com", email_verified: true })
    .firestore();

  await assertSucceeds(
    adminDb.collection("tasks").doc("task_1").set({
      assigneeId: ALICE_UID,
      assigneeName: "Alice",
      createdBy: ADMIN_UID,
      title: "Acomodar sala principal",
      description: "Tomar foto panorámica tras limpieza",
      taskType: "EXTRAORDINARY",
      priority: "URGENT",
      dueDate: "2026-11-24",
      status: "PENDING",
      hasPhotoEvidence: false,
      createdAt: pastTime(),
      updatedAt: pastTime(),
    })
  );

  await assertFails(aliceDb.collection("tasks").get());
  await assertSucceeds(
    aliceDb.collection("tasks").where("assigneeId", "==", ALICE_UID).get()
  );
  await assertFails(bobDb.collection("tasks").doc("task_1").get());

  await assertFails(
    aliceDb.collection("tasks").doc("task_1").update({
      status: "COMPLETED",
      hasPhotoEvidence: false,
      completedAt: pastTime(500),
      updatedAt: pastTime(500),
    })
  );

  const batch = aliceDb.batch();
  const evidenceRef = aliceDb.collection("task_evidences").doc("ev_1");
  const taskRef = aliceDb.collection("tasks").doc("task_1");
  const dummyBase64 = "A".repeat(120);
  batch.set(evidenceRef, {
    taskId: "task_1",
    uploaderId: ALICE_UID,
    uploaderName: "Alice",
    taskTitle: "Acomodar sala principal",
    imageBase64: dummyBase64,
    capturedAt: pastTime(500),
  });
  batch.update(taskRef, {
    status: "COMPLETED",
    hasPhotoEvidence: true,
    evidenceId: "ev_1",
    completedAt: pastTime(500),
    updatedAt: pastTime(500),
  });
  await assertSucceeds(batch.commit());

  await assertFails(aliceDb.collection("task_evidences").doc("ev_1").get());
  await assertFails(aliceDb.collection("task_evidences").get());
  await assertFails(bobDb.collection("task_evidences").doc("ev_1").get());

  await assertSucceeds(adminDb.collection("task_evidences").doc("ev_1").get());
  await assertSucceeds(adminDb.collection("task_evidences").get());

  await assertFails(
    aliceDb.collection("tasks").doc("task_1").update({
      status: "PENDING",
      updatedAt: pastTime(100),
    })
  );

  // Completed task is locked: neither employee nor admin can attach new evidences or files
  await assertFails(
    aliceDb.collection("task_evidences").doc("ev_second").set({
      taskId: "task_1",
      uploaderId: ALICE_UID,
      uploaderName: "Alice",
      taskTitle: "Acomodar sala principal",
      fileCount: 1,
      primaryFileName: "extra.pdf",
      capturedAt: pastTime(50),
    })
  );
  await assertFails(
    adminDb.collection("task_evidences").doc("ev_second_admin").set({
      taskId: "task_1",
      uploaderId: ADMIN_UID,
      uploaderName: "Admin",
      taskTitle: "Acomodar sala principal",
      fileCount: 1,
      primaryFileName: "extra.pdf",
      capturedAt: pastTime(50),
    })
  );
  await assertFails(
    adminDb.collection("tasks").doc("task_1").update({
      evidenceId: "ev_other",
      updatedAt: pastTime(50),
    })
  );
});

test("Multi-file task evidences (up to 5 files, PDF/Excel/photos) & per-branch quota reset fields", async () => {
  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true })
    .firestore();
  const aliceDb = testEnv
    .authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true })
    .firestore();
  const bobDb = testEnv
    .authenticatedContext(BOB_UID, { email: "bob@example.com", email_verified: true })
    .firestore();

  // Setup Alice user doc
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Sucursal 107",
      branchName: "Sucursal 107",
      email: "alice@example.com",
      role: "EMPLOYEE",
      createdAt: pastTime(5000),
      updatedAt: pastTime(5000),
    })
  );

  // Employee cannot modify confirmedQuotaId directly
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      confirmedQuotaId: "q_2",
      updatedAt: pastTime(1000),
    })
  );

  // Admin can confirm quota reset individually per branch
  await assertSucceeds(
    adminDb.collection("users").doc(ALICE_UID).update({
      confirmedQuotaId: "q_2",
      lastChecklistResetAt: pastTime(1000),
      updatedAt: pastTime(1000),
    })
  );

  // Pending task for multi-file evidence
  await assertSucceeds(
    adminDb.collection("tasks").doc("task_multi").set({
      assigneeId: ALICE_UID,
      assigneeName: "Sucursal 107",
      createdBy: ADMIN_UID,
      title: "Inventario semanal",
      description: "Subir hasta 5 archivos de respaldo",
      taskType: "DAILY",
      priority: "NORMAL",
      dueDate: "2026-11-26",
      dueTime: "23:59",
      status: "PENDING",
      hasPhotoEvidence: false,
      createdAt: pastTime(2000),
      updatedAt: pastTime(2000),
    })
  );

  // Batch 1: task_evidences (PDFs only, no imageBase64) + files 0..4
  const batch1 = aliceDb.batch();
  const evRef = aliceDb.collection("task_evidences").doc("ev_multi");
  batch1.set(evRef, {
    taskId: "task_multi",
    uploaderId: ALICE_UID,
    uploaderName: "Sucursal 107",
    taskTitle: "Inventario semanal",
    fileCount: 5,
    primaryFileName: "conteo_1.pdf",
    capturedAt: pastTime(500),
  });
  for (let i = 0; i < 5; i++) {
    batch1.set(evRef.collection("files").doc(`file_${i}_part_0`), {
      evidenceId: "ev_multi",
      taskId: "task_multi",
      uploaderId: ALICE_UID,
      fileIndex: i,
      chunkIndex: 0,
      chunkCount: 1,
      fileName: `conteo_${i + 1}.pdf`,
      mimeType: "application/pdf",
      sizeBytes: 12000,
      dataBase64: "JVBERi0xLjQK",
      capturedAt: pastTime(500),
    });
  }
  await assertSucceeds(batch1.commit());

  // 6th file (fileIndex 5) is rejected by rules
  await assertFails(
    aliceDb.collection("task_evidences").doc("ev_multi").collection("files").doc("file_5_part_0").set({
      evidenceId: "ev_multi",
      taskId: "task_multi",
      uploaderId: ALICE_UID,
      fileIndex: 5,
      chunkIndex: 0,
      chunkCount: 1,
      fileName: "extra_6.pdf",
      mimeType: "application/pdf",
      sizeBytes: 1200,
      dataBase64: "JVBERi0=",
      capturedAt: pastTime(400),
    })
  );

  // Complete the task
  await assertSucceeds(
    aliceDb.collection("tasks").doc("task_multi").update({
      status: "COMPLETED",
      hasPhotoEvidence: true,
      evidenceId: "ev_multi",
      completedAt: pastTime(300),
      updatedAt: pastTime(300),
    })
  );

  // After completion, no more files can be added even for valid fileIndex
  await assertFails(
    aliceDb.collection("task_evidences").doc("ev_multi").collection("files").doc("file_0_part_1").set({
      evidenceId: "ev_multi",
      taskId: "task_multi",
      uploaderId: ALICE_UID,
      fileIndex: 0,
      chunkIndex: 1,
      chunkCount: 2,
      fileName: "conteo_1.pdf",
      mimeType: "application/pdf",
      sizeBytes: 12000,
      dataBase64: "JVBERi0=",
      capturedAt: pastTime(100),
    })
  );

  // Only Admin can read /files subcollection; Alice & Bob cannot
  await assertFails(aliceDb.collection("task_evidences").doc("ev_multi").collection("files").get());
  await assertFails(bobDb.collection("task_evidences").doc("ev_multi").collection("files").get());
  await assertSucceeds(adminDb.collection("task_evidences").doc("ev_multi").collection("files").get());
});

test("Extraordinary notifications: admin sends, only target employee reads & acknowledges", async () => {
  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true })
    .firestore();
  const aliceDb = testEnv
    .authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true })
    .firestore();
  const bobDb = testEnv
    .authenticatedContext(BOB_UID, { email: "bob@example.com", email_verified: true })
    .firestore();

  await assertSucceeds(
    adminDb.collection("notifications").doc("notif_1").set({
      recipientId: ALICE_UID,
      taskId: "task_1",
      title: "Nueva tarea extraordinaria",
      message: "Acomodar sala principal",
      acknowledged: false,
      createdBy: ADMIN_UID,
      createdAt: pastTime(),
      updatedAt: pastTime(),
    })
  );

  await assertSucceeds(
    aliceDb.collection("notifications").where("recipientId", "==", ALICE_UID).get()
  );
  await assertFails(bobDb.collection("notifications").doc("notif_1").get());

  await assertSucceeds(
    aliceDb.collection("notifications").doc("notif_1").update({
      acknowledged: true,
      updatedAt: pastTime(500),
    })
  );
});

test("Biweekly quotas & 5-metric daily sales entries: isolated per user, admin sets quota and sees all", async () => {
  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true })
    .firestore();
  const aliceDb = testEnv
    .authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true })
    .firestore();
  const bobDb = testEnv
    .authenticatedContext(BOB_UID, { email: "bob@example.com", email_verified: true })
    .firestore();

  await assertFails(
    aliceDb.collection("biweekly_quotas").doc("q_1").set({
      title: "Quincena Fake",
      startDate: "2026-11-16",
      endDate: "2026-11-30",
      totalDays: 14,
      targetGlobalSales: 1000,
      targetCreditSales: 500,
      targetNewCustomers: 10,
      targetAppMaOperations: 10,
      targetNewCustomerRequests: 10,
      active: true,
      createdBy: ALICE_UID,
      createdAt: pastTime(),
      updatedAt: pastTime(),
    })
  );

  await assertSucceeds(
    adminDb.collection("biweekly_quotas").doc("q_1").set({
      title: "Quincena Noviembre 2",
      startDate: "2026-11-16",
      endDate: "2026-11-30",
      totalDays: 14,
      targetGlobalSales: 250000,
      targetCreditSales: 150000,
      targetNewCustomers: 45,
      targetAppMaOperations: 60,
      targetNewCustomerRequests: 75,
      active: true,
      createdBy: ADMIN_UID,
      createdAt: pastTime(),
      updatedAt: pastTime(),
    })
  );

  await assertSucceeds(
    aliceDb.collection("biweekly_quotas").where("active", "==", true).get()
  );

  await assertSucceeds(
    aliceDb.collection("sales_entries").doc("q_1_2026-11-24_alice_123").set({
      userId: ALICE_UID,
      userName: "Alice",
      quotaId: "q_1",
      dateString: "2026-11-24",
      globalSales: 28500.5,
      creditSales: 19200.0,
      newCustomers: 4,
      appMaOperations: 6,
      newCustomerRequests: 7,
      notes: "Cierre vespertino",
      createdAt: pastTime(),
      updatedAt: pastTime(),
    })
  );

  await assertSucceeds(
    aliceDb.collection("sales_entries").doc("q_1_2026-11-24_alice_123").update({
      globalSales: 31000.0,
      creditSales: 21000.0,
      newCustomers: 5,
      appMaOperations: 7,
      newCustomerRequests: 8,
      notes: "Cierre ajustado",
      updatedAt: pastTime(300),
    })
  );

  await assertFails(
    bobDb.collection("sales_entries").doc("q_1_2026-11-24_alice_123").get()
  );
  await assertFails(aliceDb.collection("sales_entries").get());
  await assertSucceeds(adminDb.collection("sales_entries").get());

  // Employee can save daily closing with comment + phone file/photo attachment (including small <50 char base64 files)
  await assertSucceeds(
    aliceDb.collection("sales_entries").doc("q_1_2026-11-25_alice_123").set({
      userId: ALICE_UID,
      userName: "Sucursal 107",
      quotaId: "q_1",
      dateString: "2026-11-25",
      globalSales: 35000.0,
      creditSales: 22000.0,
      newCustomers: 6,
      appMaOperations: 8,
      newCustomerRequests: 9,
      notes: "Cierre con soporte adjunto",
      attachmentName: "nota.txt",
      attachmentMimeType: "text/plain",
      attachmentBase64: "SG9sYQ==",
      attachmentChunkCount: 1,
      attachmentSizeBytes: 4,
      createdAt: pastTime(),
      updatedAt: pastTime(),
    })
  );

  // Employee can save chunked multi-megabyte attachment atomically in a batch
  const chunkedEntryId = "q_1_2026-11-26_alice_123";
  const chunkBatch = aliceDb.batch();
  chunkBatch.set(aliceDb.collection("sales_entries").doc(chunkedEntryId), {
    userId: ALICE_UID,
    userName: "Sucursal 107",
    quotaId: "q_1",
    dateString: "2026-11-26",
    globalSales: 40000.0,
    creditSales: 24000.0,
    newCustomers: 5,
    appMaOperations: 7,
    newCustomerRequests: 8,
    notes: "Reporte PDF grande",
    attachmentName: "reporte_grande.pdf",
    attachmentMimeType: "application/pdf",
    attachmentChunkCount: 2,
    attachmentSizeBytes: 900000,
    createdAt: pastTime(),
    updatedAt: pastTime(),
  });
  chunkBatch.set(
    aliceDb.collection("sales_entries").doc(chunkedEntryId).collection("attachment_chunks").doc("chunk_0"),
    {
      userId: ALICE_UID,
      entryId: chunkedEntryId,
      chunkIndex: 0,
      dataBase64: "C".repeat(200),
      createdAt: pastTime(),
    }
  );
  chunkBatch.set(
    aliceDb.collection("sales_entries").doc(chunkedEntryId).collection("attachment_chunks").doc("chunk_1"),
    {
      userId: ALICE_UID,
      entryId: chunkedEntryId,
      chunkIndex: 1,
      dataBase64: "D".repeat(200),
      createdAt: pastTime(),
    }
  );
  await assertSucceeds(chunkBatch.commit());

  // Owner and admin can read chunks; another employee cannot
  await assertSucceeds(
    aliceDb.collection("sales_entries").doc(chunkedEntryId).collection("attachment_chunks").where("userId", "==", ALICE_UID).get()
  );
  await assertSucceeds(
    adminDb.collection("sales_entries").doc(chunkedEntryId).collection("attachment_chunks").where("userId", "==", ALICE_UID).get()
  );
  await assertFails(
    bobDb.collection("sales_entries").doc(chunkedEntryId).collection("attachment_chunks").where("userId", "==", ALICE_UID).get()
  );
  await assertFails(
    bobDb.collection("sales_entries").doc(chunkedEntryId).collection("attachment_chunks").doc("chunk_0").get()
  );
  // Owner can replace an existing chunk via set() (triggers update rule)
  await assertSucceeds(
    aliceDb.collection("sales_entries").doc(chunkedEntryId).collection("attachment_chunks").doc("chunk_0").set({
      userId: ALICE_UID,
      entryId: chunkedEntryId,
      chunkIndex: 0,
      dataBase64: "Z".repeat(250),
      createdAt: pastTime(500),
    })
  );

  // Reject invalid chunkIndex >= 8
  await assertFails(
    aliceDb.collection("sales_entries").doc(chunkedEntryId).collection("attachment_chunks").doc("chunk_8").set({
      userId: ALICE_UID,
      entryId: chunkedEntryId,
      chunkIndex: 8,
      dataBase64: "E".repeat(100),
      createdAt: pastTime(),
    })
  );
});

test("Scheduled Friday/Sunday tasks: employee can materialize official self-scheduled tasks only", async () => {
  const aliceDb = testEnv
    .authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true })
    .firestore();

  await assertSucceeds(
    aliceDb.collection("tasks").doc("sched_alice_123_2026-11-27_inventario").set({
      assigneeId: ALICE_UID,
      assigneeName: "Sucursal 107",
      createdBy: ALICE_UID,
      title: "Inventario",
      description: "Conteo de viernes",
      taskType: "DAILY",
      priority: "NORMAL",
      dueDate: "2026-11-28",
      dueTime: "23:59",
      deadlineLabel: "Límite: Día siguiente al cierre",
      status: "PENDING",
      hasPhotoEvidence: false,
      createdAt: pastTime(),
      updatedAt: pastTime(),
    })
  );

  await assertSucceeds(
    aliceDb.collection("tasks").doc("sched_alice_123_2026-11-29_examenes").set({
      assigneeId: ALICE_UID,
      assigneeName: "Sucursal 107",
      createdBy: ALICE_UID,
      title: "Exámenes",
      description: "Exámenes dominicales",
      taskType: "DAILY",
      priority: "NORMAL",
      dueDate: "2026-11-30",
      dueTime: "11:30",
      deadlineLabel: "Límite: Día siguiente 11:30 a.m.",
      status: "PENDING",
      hasPhotoEvidence: false,
      createdAt: pastTime(),
      updatedAt: pastTime(),
    })
  );

  await assertSucceeds(
    aliceDb.collection("tasks").doc("sched_alice_123_2026-11-25_avance_ventas_1400").set({
      assigneeId: ALICE_UID,
      assigneeName: "Sucursal 107",
      createdBy: ALICE_UID,
      title: "Actualización de avance de ventas (2:00 p.m.)",
      description: "Corte de ventas 2:00 p.m.",
      taskType: "DAILY",
      priority: "NORMAL",
      dueDate: "2026-11-25",
      dueTime: "23:59",
      deadlineLabel: "Horario: 2:00 p.m.",
      status: "PENDING",
      hasPhotoEvidence: false,
      createdAt: pastTime(),
      updatedAt: pastTime(),
    })
  );

  await assertSucceeds(
    aliceDb.collection("tasks").doc("sched_alice_123_2026-11-25_avance_ventas_1800").set({
      assigneeId: ALICE_UID,
      assigneeName: "Sucursal 107",
      createdBy: ALICE_UID,
      title: "Actualización de avance de ventas (6:00 p.m.)",
      description: "Corte de ventas 6:00 p.m.",
      taskType: "DAILY",
      priority: "NORMAL",
      dueDate: "2026-11-25",
      dueTime: "23:59",
      deadlineLabel: "Horario: 6:00 p.m.",
      status: "PENDING",
      hasPhotoEvidence: false,
      createdAt: pastTime(),
      updatedAt: pastTime(),
    })
  );

  // Employee cannot create arbitrary or extraordinary tasks
  await assertFails(
    aliceDb.collection("tasks").doc("fake_extraordinary").set({
      assigneeId: BOB_UID,
      assigneeName: "Sucursal 109",
      createdBy: ALICE_UID,
      title: "Tarea inventada",
      description: "No permitida",
      taskType: "EXTRAORDINARY",
      priority: "URGENT",
      dueDate: "2026-11-29",
      status: "PENDING",
      hasPhotoEvidence: false,
      createdAt: pastTime(),
      updatedAt: pastTime(),
    })
  );
});

test("Admin can edit and delete existing daily tasks; employees cannot delete or edit details", async () => {
  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true })
    .firestore();
  const aliceDb = testEnv
    .authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true })
    .firestore();

  const dailyTaskId = "sched_alice_123_2026-11-26_apertura_piso";
  await assertSucceeds(
    aliceDb.collection("tasks").doc(dailyTaskId).set({
      assigneeId: ALICE_UID,
      assigneeName: "Sucursal 107",
      createdBy: ALICE_UID,
      title: "Apertura de piso y encendido de exhibición",
      description: "Foto de luces encendidas",
      taskType: "DAILY",
      priority: "NORMAL",
      dueDate: "2026-11-26",
      dueTime: "23:59",
      deadlineLabel: "Límite: Hoy al cierre",
      status: "PENDING",
      hasPhotoEvidence: false,
      createdAt: pastTime(2000),
      updatedAt: pastTime(2000),
    })
  );

  // Employee cannot edit task title or delete task
  await assertFails(
    aliceDb.collection("tasks").doc(dailyTaskId).update({
      title: "Título modificado por empleado",
      updatedAt: pastTime(500),
    })
  );
  await assertFails(aliceDb.collection("tasks").doc(dailyTaskId).delete());

  // Admin can modify existing daily task (title, description, priority, dueTime)
  await assertSucceeds(
    adminDb.collection("tasks").doc(dailyTaskId).update({
      title: "Apertura y exhibición principal",
      description: "Incluir foto de fachada e iluminación",
      priority: "URGENT",
      dueTime: "11:30",
      deadlineLabel: "Límite: 11:30 a.m.",
      updatedAt: pastTime(300),
    })
  );

  // Admin can delete existing daily task
  await assertSucceeds(adminDb.collection("tasks").doc(dailyTaskId).delete());
});
