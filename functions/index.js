/**
 * Cloud Functions skeleton (deploy belakangan).
 * Validasi server-side untuk kunci/billing agar anti-cheat lebih kuat.
 *
 * Setup:
 *   cd functions && npm install && firebase deploy --only functions
 */

const functions = require("firebase-functions");
const admin = require("firebase-admin");

admin.initializeApp();
const db = admin.firestore();

/** Grant 1 key after rewarded ad (SSV / trusted client call + rate limit nanti). */
exports.grantKey = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "Login dulu");
  }
  const uid = context.auth.uid;
  const ref = db.collection("users").doc(uid);
  await db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    const keys = (snap.exists ? snap.get("keys") : 0) || 0;
    tx.set(ref, { keys: keys + 1, updatedAt: admin.firestore.FieldValue.serverTimestamp() }, { merge: true });
  });
  return { ok: true };
});

/** Consume 1 key for an episode unless premium. */
exports.consumeKey = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "Login dulu");
  }
  const uid = context.auth.uid;
  const slug = String(data.slug || "");
  const episode = Number(data.episode || 0);
  const ref = db.collection("users").doc(uid);
  const result = await db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    const isPremium = snap.get("isPremium") === true;
    const premiumUntil = snap.get("premiumUntilMs") || 0;
    const now = Date.now();
    if (isPremium && (!premiumUntil || premiumUntil > now)) {
      return { ok: true, premium: true };
    }
    const keys = snap.get("keys") || 0;
    if (keys < 1) {
      throw new functions.https.HttpsError("failed-precondition", "Kunci habis");
    }
    tx.set(ref, { keys: keys - 1, updatedAt: admin.firestore.FieldValue.serverTimestamp() }, { merge: true });
    return { ok: true, premium: false, slug, episode };
  });
  return result;
});
