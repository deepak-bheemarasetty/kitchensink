// Run with: mongosh "$MONGODB_URI" src/main/resources/db/migration/V1__members_to_design_model.js
const collection = db.members;
const pending = collection.find({ firstName: { $exists: false }, name: { $exists: true } }).toArray();
const seenEmails = new Set();
const errors = [];

for (const old of pending) {
  const words = String(old.name || "").trim().split(/\s+/).filter(Boolean);
  const firstName = words.shift() || "Unknown";
  const lastName = words.join(" ");
  const email = String(old.email || "").trim();
  const emailLower = email.toLowerCase();
  if (!email || seenEmails.has(emailLower)) {
    errors.push({ id: old._id, email, reason: "missing or duplicate email" });
    continue;
  }
  seenEmails.add(emailLower);
  const digits = String(old.phoneNumber || "").replace(/\D/g, "");
  const countryCode = old.countryCode == null ? "" : String(old.countryCode);
  const phone = digits && countryCode ? `+${countryCode}${digits}` : null;
  const now = old.creationDate || new Date();
  const actorId = new ObjectId("000000000000000000000000");
  const update = {
    $set: {
      memberId: `MEM-${String(old._id).replace(/\W/g, "").toUpperCase()}`,
      firstName,
      lastName,
      fullNameLower: `${firstName} ${lastName}`.trim().toLowerCase(),
      email,
      emailLower,
      phone,
      country: old.countryCode == null ? null : countryCode,
      status: "active",
      createdAt: now,
      createdBy: actorId,
      updatedAt: old.lastUpdateDate || now,
      updatedBy: actorId,
      version: 1,
      deletedAt: null,
      deletedBy: null
    },
    $unset: { name: "", phoneNumber: "", countryCode: "", creationDate: "", lastUpdateDate: "", lastUpdatedBy: "" }
  };
  collection.updateOne({ _id: old._id }, update);
}

printjson({ migrated: pending.length - errors.length, rejected: errors });
if (errors.length) quit(2);
