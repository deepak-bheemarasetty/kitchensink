// Apply after V2 to transition existing application users to OAuth-only identities.
if (!db.getCollectionNames().includes("users")) {
  throw new Error("The users collection must exist before applying V3");
}

db.runCommand({
  collMod: "users",
  validator: {},
  validationLevel: "off",
  validationAction: "warn"
});

db.users.updateMany({}, { $unset: { passwordHash: "" } });
db.runCommand({
  collMod: "users",
  validator: {
    $jsonSchema: {
      bsonType: "object",
      required: ["username", "usernameLower", "roles", "isActive", "createdAt", "updatedAt"],
      additionalProperties: false,
      properties: {
        _id: { bsonType: "objectId" },
        username: { bsonType: "string", minLength: 3, maxLength: 254, pattern: "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$" },
        usernameLower: { bsonType: "string", minLength: 3, maxLength: 254, pattern: "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$" },
        roles: { bsonType: "array", minItems: 1, maxItems: 10, uniqueItems: true, items: { bsonType: "string" } },
        isActive: { bsonType: "bool" },
        createdAt: { bsonType: "date" },
        updatedAt: { bsonType: "date" },
        lastLoginAt: { bsonType: ["date", "null"] }
      }
    }
  },
  validationLevel: "strict",
  validationAction: "error"
});

print("Removed legacy password hashes and updated the OAuth-only users validator.");
