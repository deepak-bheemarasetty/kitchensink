const now = new Date();
const actorId = new ObjectId("000000000000000000000000");
const people = [
  ["John", "Smith", "john.smith@mailinator.com"],
  ["Alice", "Johnson", "alice.johnson@example.com"],
  ["Michael", "Brown", "michael.brown@example.com"]
];

db.members.insertMany(
  people.map(([firstName, lastName, email]) => ({
    memberId: `MEM-${now.getTime()}-${new ObjectId().toHexString().slice(-6).toUpperCase()}`,
    firstName,
    lastName,
    fullNameLower: `${firstName} ${lastName}`.toLowerCase(),
    email,
    emailLower: email.toLowerCase(),
    phone: "+12125551212",
    status: "active",
    createdAt: now,
    createdBy: actorId,
    updatedAt: now,
    updatedBy: actorId,
    version: 1,
    deletedAt: null,
    deletedBy: null
  }))
);
