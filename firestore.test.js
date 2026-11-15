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
const TEACHER_A_UID = "teacher_a";
const TEACHER_B_UID = "teacher_b";
const YEAR_ID = "year_2025_2026";
const CLASS_A_ID = "class_10a1";
const CLASS_B_ID = "class_11a2";
const STUDENT_A_ID = "student_a1";
const SESSION_A_ID = "session_a1";
const QR_SECRET = "0123456789abcdef0123456789abcdef";

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

async function seedAdminAndClasses() {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    const now = new Date();
    await db.collection("admins").doc(ADMIN_UID).set({
      uid: ADMIN_UID,
      email: "admin@school.edu.vn",
      displayName: "Admin Trường",
      grantedByUid: ADMIN_UID,
      createdAt: now,
    });
    await db.collection("school_years").doc(YEAR_ID).set({
      name: "2025 - 2026",
      isCurrent: true,
      isSchoolWide: true,
      ownerUid: ADMIN_UID,
      semester1StartMillis: 1757030400000,
      semester1EndMillis: 1768003200000,
      semester2StartMillis: 1768608000000,
      semester2EndMillis: 1780185600000,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("classes").doc(CLASS_A_ID).set({
      name: "10A1",
      grade: 10,
      schoolYearId: YEAR_ID,
      schoolYearName: "2025 - 2026",
      homeroomTeacherUid: TEACHER_A_UID,
      homeroomTeacherName: "Cô Lan",
      ownerUid: ADMIN_UID,
      teacherUids: [TEACHER_A_UID],
      qrSecret: QR_SECRET,
      studentCount: 1,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("classes").doc(CLASS_B_ID).set({
      name: "11A2",
      grade: 11,
      schoolYearId: YEAR_ID,
      schoolYearName: "2025 - 2026",
      homeroomTeacherUid: TEACHER_B_UID,
      homeroomTeacherName: "Thầy Minh",
      ownerUid: ADMIN_UID,
      teacherUids: [TEACHER_B_UID],
      qrSecret: QR_SECRET,
      studentCount: 0,
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("students").doc(STUDENT_A_ID).set({
      studentCode: "HS10A101",
      fullName: "Nguyễn Văn An",
      gender: "Nam",
      dateOfBirth: "12/05/2010",
      parentPhone: "0912345678",
      classId: CLASS_A_ID,
      className: "10A1",
      schoolYearId: YEAR_ID,
      qrToken: "tok_9f8a7b6c5d4e3f2a",
      qrVersion: 1,
      active: true,
      ownerUid: ADMIN_UID,
      teacherUids: [TEACHER_A_UID],
      createdAt: now,
      updatedAt: now,
    });
    await db.collection("sessions").doc(SESSION_A_ID).set({
      classId: CLASS_A_ID,
      className: "10A1",
      schoolYearId: YEAR_ID,
      subject: "Toán học",
      periodLabel: "Tiết 1",
      sessionDate: "2026-11-14",
      scheduledStartMillis: 1794643200000,
      lateAfterMinutes: 10,
      status: "OPEN",
      createdByUid: TEACHER_A_UID,
      createdByName: "Cô Lan",
      teacherUids: [TEACHER_A_UID],
      presentCount: 0,
      lateCount: 0,
      excusedCount: 0,
      unexcusedCount: 0,
      createdAt: now,
      updatedAt: now,
    });
  });
}

test("Unauthenticated user cannot read classes or students", async () => {
  await seedAdminAndClasses();
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("classes").doc(CLASS_A_ID).get());
  await assertFails(unauthDb.collection("students").doc(STUDENT_A_ID).get());
});

test("Teacher cannot self-assign admin role on profile creation", async () => {
  const teacherDb = testEnv.authenticatedContext(TEACHER_A_UID, {
    email: "lan@school.edu.vn",
    email_verified: true,
  }).firestore();
  await assertFails(
    teacherDb.collection("teachers").doc(TEACHER_A_UID).set({
      uid: TEACHER_A_UID,
      displayName: "Cô Lan",
      email: "lan@school.edu.vn",
      subject: "Toán",
      phone: "0901112223",
      role: "admin",
      active: true,
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );
});

test("Teacher can create own teacher profile with role=teacher", async () => {
  const teacherDb = testEnv.authenticatedContext(TEACHER_A_UID, {
    email: "lan@school.edu.vn",
    email_verified: true,
  }).firestore();
  await assertSucceeds(
    teacherDb.collection("teachers").doc(TEACHER_A_UID).set({
      uid: TEACHER_A_UID,
      displayName: "Cô Lan",
      email: "lan@school.edu.vn",
      subject: "Toán",
      phone: "0901112223",
      role: "teacher",
      active: true,
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );
});

test("Class-level isolation: Teacher B cannot read or query Teacher A students", async () => {
  await seedAdminAndClasses();
  const teacherBDb = testEnv.authenticatedContext(TEACHER_B_UID).firestore();
  await assertFails(teacherBDb.collection("students").doc(STUDENT_A_ID).get());
  await assertFails(teacherBDb.collection("students").get());
  await assertSucceeds(
    teacherBDb.collection("students").where("teacherUids", "array-contains", TEACHER_B_UID).get()
  );
});

test("Teacher A can query authorized students and record deterministic attendance", async () => {
  await seedAdminAndClasses();
  const teacherADb = testEnv.authenticatedContext(TEACHER_A_UID).firestore();
  await assertSucceeds(
    teacherADb.collection("students").where("teacherUids", "array-contains", TEACHER_A_UID).get()
  );

  const recordId = `${SESSION_A_ID}_${STUDENT_A_ID}`;
  await assertSucceeds(
    teacherADb.collection("attendance_records").doc(recordId).set({
      sessionId: SESSION_A_ID,
      classId: CLASS_A_ID,
      className: "10A1",
      schoolYearId: YEAR_ID,
      sessionDate: "2026-11-14",
      subject: "Toán học",
      studentId: STUDENT_A_ID,
      studentCode: "HS10A101",
      studentName: "Nguyễn Văn An",
      status: "PRESENT",
      scanMethod: "QR_SCAN",
      checkedInAtMillis: 1794643250000,
      note: "",
      recordedByUid: TEACHER_A_UID,
      recordedByName: "Cô Lan",
      updatedByUid: TEACHER_A_UID,
      updatedByName: "Cô Lan",
      teacherUids: [TEACHER_A_UID],
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  // Non-deterministic record ID must fail (anti-duplicate invariant)
  await assertFails(
    teacherADb.collection("attendance_records").doc("random_dup_id").set({
      sessionId: SESSION_A_ID,
      classId: CLASS_A_ID,
      className: "10A1",
      schoolYearId: YEAR_ID,
      sessionDate: "2026-11-14",
      subject: "Toán học",
      studentId: STUDENT_A_ID,
      studentCode: "HS10A101",
      studentName: "Nguyễn Văn An",
      status: "PRESENT",
      scanMethod: "QR_SCAN",
      checkedInAtMillis: 1794643260000,
      note: "",
      recordedByUid: TEACHER_A_UID,
      recordedByName: "Cô Lan",
      updatedByUid: TEACHER_A_UID,
      updatedByName: "Cô Lan",
      teacherUids: [TEACHER_A_UID],
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );
});

test("Audit logs are append-only (update and delete rejected)", async () => {
  await seedAdminAndClasses();
  const teacherADb = testEnv.authenticatedContext(TEACHER_A_UID).firestore();
  const logRef = teacherADb.collection("audit_logs").doc("log_001");
  await assertSucceeds(
    logRef.set({
      recordId: `${SESSION_A_ID}_${STUDENT_A_ID}`,
      sessionId: SESSION_A_ID,
      classId: CLASS_A_ID,
      className: "10A1",
      studentId: STUDENT_A_ID,
      studentName: "Nguyễn Văn An",
      previousStatus: "ABSENT_UNEXCUSED",
      newStatus: "ABSENT_EXCUSED",
      reason: "Phụ huynh nhắn tin xin phép nghỉ ốm",
      editorUid: TEACHER_A_UID,
      editorName: "Cô Lan",
      teacherUids: [TEACHER_A_UID],
      createdAt: new Date(),
    })
  );
  await assertFails(logRef.update({ reason: "Tampered" }));
  await assertFails(logRef.delete());
});

test("Bootstrapped admin email with unverified email is rejected from admin actions", async () => {
  const spoofedAdminDb = testEnv.authenticatedContext("spoof_uid", {
    email: "haupham101981@gmail.com",
    email_verified: false,
  }).firestore();
  await assertFails(spoofedAdminDb.collection("teachers").get());
});

test("Bootstrapped admin with verified email has full access", async () => {
  await seedAdminAndClasses();
  const realAdminDb = testEnv.authenticatedContext("real_admin_uid", {
    email: "haupham101981@gmail.com",
    email_verified: true,
  }).firestore();
  await assertSucceeds(realAdminDb.collection("classes").get());
  await assertSucceeds(realAdminDb.collection("students").get());
});
