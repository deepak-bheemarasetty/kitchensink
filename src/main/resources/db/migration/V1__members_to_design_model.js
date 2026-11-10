// Run from the repository root with: mongosh "$MONGODB_URI" src/main/resources/db/migration/V1__members_to_design_model.js
const collection = db.getCollection("members");
const legacy = collection.find({ name: { $exists: true } }).toArray();
const emailOwners = new Map();
const prepared = [];
const errors = [];

for (const old of legacy) {
  const nameParts = String(old.name || "").trim().split(/\s+/).filter(Boolean);
  const firstName = nameParts.shift() || "";
  const lastName = nameParts.join(" ");
  const email = String(old.email || "").trim();
  const emailLower = email.toLowerCase();
  if (!firstName || !email) {
    errors.push({ id: old._id, reason: "missing first name or email" });
    continue;
  }
  if (emailOwners.has(emailLower)) {
    errors.push({ id: old._id, reason: `duplicate email also used by ${emailOwners.get(emailLower)}` });
    continue;
  }
  emailOwners.set(emailLower, old._id);

  // Extract digits for phone and countryCode
  const rawDigits = String(old.phoneNumber || old.phone || "").replace(/\D/g, "");
  let rawCc = old.countryCode == null ? "" : String(old.countryCode).replace(/\D/g, "");
  let phone = rawDigits;

  // If phone starts with 0, strip leading zeros
  phone = phone.replace(/^0+/, "");

  if (rawCc && phone.startsWith(rawCc)) {
    phone = phone.slice(rawCc.length);
  }
  if (!/^[1-9][0-9]{5,13}$/.test(phone)) {
    errors.push({ id: old._id, reason: `invalid phone number digits: ${phone}` });
    continue;
  }
  const countryCode = rawCc && !rawCc.startsWith("0") && /^[1-9][0-9]{0,3}$/.test(rawCc) ? rawCc : null;

  const createdAt = old.creationDate || new Date();
  const actorId = new ObjectId("000000000000000000000000");
  prepared.push({
    old,
    document: {
      _id: old._id instanceof ObjectId ? old._id : new ObjectId(),
      memberId: `M-${String(old._id).replace(/\W/g, "").slice(-18).toUpperCase()}`,
      firstName,
      lastName,
      fullNameLower: `${firstName} ${lastName}`.trim().toLowerCase(),
      email,
      emailLower,
      countryCode,
      phone,
      status: "active",
      createdAt,
      createdBy: actorId,
      updatedAt: old.lastUpdateDate || createdAt,
      updatedBy: actorId,
      version: 1,
      deletedAt: null,
      deletedBy: null
    }
  });
}

if (errors.length) {
  printjson({ migrated: 0, rejected: errors });
  quit(2);
}

if (legacy.length && !db.getCollectionNames().includes("members_legacy_backup")) {
  collection.aggregate([{ $match: { name: { $exists: true } } }, { $out: "members_legacy_backup" }]);
}

for (const item of prepared) {
  collection.replaceOne({ _id: item.old._id }, item.document);
}

collection.createIndex({ emailLower: 1 }, { name: "uq_members_email_active", unique: true, partialFilterExpression: { deletedAt: null } });
collection.createIndex({ memberId: 1 }, { name: "uq_members_member_id_active", unique: true, partialFilterExpression: { deletedAt: null } });
collection.createIndex({ fullNameLower: 1, _id: 1 }, { name: "ix_members_name_id" });
collection.createIndex({ status: 1, fullNameLower: 1 }, { name: "ix_members_status_name" });

db.runCommand({
  collMod: "members",
  validator: {
    $jsonSchema: {
      bsonType: "object",
      required: ["_id", "memberId", "firstName", "lastName", "fullNameLower", "email", "emailLower", "phone", "status", "createdAt", "updatedAt", "version"],
      additionalProperties: false,
      properties: {
        _id: { bsonType: "objectId" },
        memberId: { bsonType: "string", minLength: 3, maxLength: 20, pattern: "^[A-Z0-9-]+$" },
        firstName: { bsonType: "string", minLength: 1, maxLength: 60 },
        lastName: { bsonType: "string", maxLength: 60 },
        fullNameLower: { bsonType: "string", maxLength: 121 },
        email: { bsonType: "string", minLength: 3, maxLength: 254 },
        emailLower: { bsonType: "string", minLength: 3, maxLength: 254 },
        countryCode: { bsonType: ["string", "null"], pattern: "^[1-9][0-9]{0,3}$" },
        phone: { bsonType: "string", pattern: "^[1-9][0-9]{5,13}$" },
        status: { bsonType: "string", enum: ["active", "inactive"] },
        createdAt: { bsonType: "date" },
        createdBy: { bsonType: ["objectId", "null"] },
        updatedAt: { bsonType: "date" },
        updatedBy: { bsonType: ["objectId", "null"] },
        version: { bsonType: "int", minimum: 1 },
        deletedAt: { bsonType: ["date", "null"] },
        deletedBy: { bsonType: ["objectId", "null"] }
      }
    }
  },
  validationLevel: "moderate",
  validationAction: "error"
});
