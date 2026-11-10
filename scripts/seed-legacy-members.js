// Run with: mongosh "$MONGODB_URI" scripts/seed-legacy-members.js
// Seeds older legacy members [name, email, phoneNumber] into the collection or temporary staging,
// simulating legacy structure without "+" or country code in the phoneNumber.
const legacyMembers = [
  { name: "Emily Davis", email: "emily.davis@example.com", phoneNumber: "2125551003" },
  { name: "Daniel Wilson", email: "daniel.wilson@example.com", phoneNumber: "2125551004" },
  { name: "Sophia Miller", email: "sophia.miller@example.com", phoneNumber: "2125551005" },
  { name: "James Taylor", email: "james.taylor@example.com", phoneNumber: "2125551006" },
  { name: "Olivia Anderson", email: "olivia.anderson@example.com", phoneNumber: "2125551007" },
  { name: "David Thomas", email: "david.thomas@example.com", phoneNumber: "2125551008" },
  { name: "Ava Jackson", email: "ava.jackson@example.com", phoneNumber: "2125551009" },
  { name: "Robert White", email: "robert.white@example.com", phoneNumber: "2125551010" },
  { name: "Isabella Harris", email: "isabella.harris@example.com", phoneNumber: "2125551011" },
  { name: "Joseph Martin", email: "joseph.martin@example.com", phoneNumber: "2125551012" }
];

// If members collection has schema validator enabled, we migrate directly to the new structure
// preserving legacy rules: no countryCode, phone is plain digits without "+".
const now = new Date();
const actorId = new ObjectId("000000000000000000000000");

legacyMembers.forEach((item, idx) => {
  const parts = String(item.name || "").trim().split(/\s+/).filter(Boolean);
  const firstName = parts.shift() || "";
  const lastName = parts.join(" ");
  const rawPhone = String(item.phoneNumber || "").replace(/\D/g, "").replace(/^0+/, "");
  const hex = new ObjectId().toHexString().slice(-6).toUpperCase();
  const epochSec = Math.floor(now.getTime() / 1000);

  db.members.updateOne(
    { emailLower: item.email.toLowerCase() },
    {
      $setOnInsert: {
        memberId: `M-${epochSec}-${hex}`,
        firstName,
        lastName,
        fullNameLower: `${firstName} ${lastName}`.trim().toLowerCase(),
        email: item.email,
        emailLower: item.email.toLowerCase(),
        countryCode: null,
        phone: rawPhone,
        status: "active",
        createdAt: new Date(now.getTime() + idx * 1000),
        createdBy: actorId,
        updatedAt: new Date(now.getTime() + idx * 1000),
        updatedBy: actorId,
        version: NumberInt(1),
        deletedAt: null,
        deletedBy: null
      }
    },
    { upsert: true }
  );
});

print("Legacy members seeded with migrated structure (no country code, plain number without '+').");

