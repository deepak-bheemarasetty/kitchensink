// Run after V1 against the configured application database.
const schemas = {
  users: {
    validator: {
      $jsonSchema: {
        bsonType: "object",
        required: ["username", "usernameLower", "passwordHash", "roles", "isActive", "createdAt", "updatedAt"],
        additionalProperties: false,
        properties: {
          _id: { bsonType: "objectId" },
          username: { bsonType: "string", minLength: 3, maxLength: 254, pattern: "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$" },
          usernameLower: { bsonType: "string", minLength: 3, maxLength: 254, pattern: "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$" },
          passwordHash: { bsonType: "string", minLength: 1 },
          roles: { bsonType: "array", minItems: 1, maxItems: 10, uniqueItems: true, items: { bsonType: "string" } },
          isActive: { bsonType: "bool" },
          createdAt: { bsonType: "date" },
          updatedAt: { bsonType: "date" },
          lastLoginAt: { bsonType: ["date", "null"] }
        }
      }
    },
    indexes: [{ key: { usernameLower: 1 }, name: "uq_users_username_lower", unique: true }]
  },
  roles: {
    validator: {
      $jsonSchema: {
        bsonType: "object",
        required: ["name", "description", "permissions", "isSystem", "updatedAt"],
        additionalProperties: false,
        properties: {
          _id: { bsonType: "objectId" },
          name: { bsonType: "string", minLength: 2, maxLength: 30, pattern: "^[a-z0-9-]+$" },
          description: { bsonType: "string", maxLength: 200 },
          permissions: {
            bsonType: "array", minItems: 1, uniqueItems: true,
            items: { enum: ["member:view", "member:create", "member:update", "member:delete", "user:manage", "role:manage"] }
          },
          isSystem: { bsonType: "bool" },
          updatedAt: { bsonType: "date" }
        }
      }
    },
    indexes: [{ key: { name: 1 }, name: "uq_roles_name", unique: true }]
  },
  audit_logs: {
    validator: {
      $jsonSchema: {
        bsonType: "object",
        required: ["at", "actorId", "action", "entityType", "entityId", "changes", "requestId"],
        additionalProperties: false,
        properties: {
          _id: { bsonType: "objectId" },
          at: { bsonType: "date" },
          actorId: { bsonType: "objectId" },
          action: { bsonType: "string", enum: ["member.create", "member.update", "member.delete", "member.restore", "user.roleChange", "role.update", "auth.loginFailed"] },
          entityType: { bsonType: "string" },
          entityId: { bsonType: "objectId" },
          changes: { bsonType: "object" },
          requestId: { bsonType: "string", minLength: 1 },
          ip: { bsonType: "string" }
        }
      }
    },
    indexes: [
      { key: { entityId: 1, at: -1 }, name: "ix_audit_entity_at" },
      { key: { actorId: 1, at: -1 }, name: "ix_audit_actor_at" }
    ]
  }
};

for (const [name, definition] of Object.entries(schemas)) {
  if (!db.getCollectionNames().includes(name)) db.createCollection(name);
  db.runCommand({
    collMod: name,
    validator: definition.validator,
    validationLevel: "strict",
    validationAction: "error"
  });
  for (const index of definition.indexes) {
    db.getCollection(name).createIndex(index.key, { name: index.name, unique: index.unique || false });
  }
}

print("Installed users, roles, and audit_logs validators and indexes.");
