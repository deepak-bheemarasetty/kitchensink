// Run with: mongosh "$MONGODB_URI" scripts/seed-fresh-members.js
// Inserts fresh demo records with the new structure (firstName, lastName, countryCode, phone)
const now = new Date();
const actorId = new ObjectId("000000000000000000000000");

const freshMembers = [
  {
    firstName: "John",
    lastName: "Smith",
    email: "john.smith@mailinator.com",
    countryCode: "1",
    phone: "2125551212"
  },
  {
    firstName: "Alice",
    lastName: "Johnson",
    email: "alice.johnson@example.com",
    countryCode: "44",
    phone: "2079460192"
  },
  {
    firstName: "Michael",
    lastName: "Brown",
    email: "michael.brown@example.com",
    countryCode: "91",
    phone: "9876543210"
  },
  {
    firstName: "Sara",
    lastName: "Connor",
    email: "sara.connor@cyberdyne.org",
    countryCode: null,
    phone: "4155552671"
  }
];

freshMembers.forEach((m, idx) => {
  const hex = new ObjectId().toHexString().slice(-6).toUpperCase();
  const epochSec = Math.floor(now.getTime() / 1000);
  db.members.updateOne(
    { emailLower: m.email.toLowerCase() },
    {
      $setOnInsert: {
        memberId: `M-${epochSec}-${hex}`,
        firstName: m.firstName,
        lastName: m.lastName,
        fullNameLower: `${m.firstName} ${m.lastName}`.trim().toLowerCase(),
        email: m.email,
        emailLower: m.email.toLowerCase(),
        countryCode: m.countryCode,
        phone: m.phone,
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

print("Fresh seed members upserted successfully.");

