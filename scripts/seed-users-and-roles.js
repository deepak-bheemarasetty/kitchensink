// Seed roles and initial admin application users into MongoDB.
// Usage: mongosh "$MONGODB_URI" scripts/seed-users-and-roles.js
// Or: mongosh kitchensink scripts/seed-users-and-roles.js

const now = new Date();

// 1. All Roles definitions
const roles = [
  {
    name: "viewer",
    description: "viewer role",
    isSystem: true,
    permissions: ["member:view"]
  },
  {
    name: "creator",
    description: "creator role",
    isSystem: false,
    permissions: ["member:view", "member:create"]
  },
  {
    name: "editor",
    description: "editor role",
    isSystem: false,
    permissions: ["member:view", "member:update"]
  },
  {
    name: "deleter",
    description: "deleter role",
    isSystem: false,
    permissions: ["member:view", "member:delete"]
  },
  {
    name: "manager",
    description: "manager role",
    isSystem: false,
    permissions: ["member:view", "member:create", "member:update", "member:delete"]
  },
  {
    name: "admin",
    description: "admin role",
    isSystem: true,
    permissions: [
      "member:view",
      "member:create",
      "member:update",
      "member:delete",
      "user:manage",
      "role:manage"
    ]
  }
];

print("Seeding application roles (insert only if not exists)...");
for (const r of roles) {
  db.roles.updateOne(
    { name: r.name },
    {
      $setOnInsert: {
        name: r.name,
        description: r.description,
        isSystem: r.isSystem,
        permissions: r.permissions,
        updatedAt: now
      }
    },
    { upsert: true }
  );
}
print(`Checked ${roles.length} roles.`);

// 2. Admin Users bootstrap
// Only admin accounts are seeded here so the initial administrator can log in
// and manage other user identities via the application UI.
const adminUsers = [
  {
    email: "kitchensink.admin@mail.com",
    roles: ["admin"],
    isActive: true
  },
  {
    email: "kitchensink.dbadmin@mail.com",
    roles: ["admin"],
    isActive: true
  }
];

print("Seeding administrator user accounts (insert only if not exists)...");
for (const u of adminUsers) {
  const emailLower = u.email.toLowerCase();
  db.users.updateOne(
    { usernameLower: emailLower },
    {
      $setOnInsert: {
        username: u.email,
        usernameLower: emailLower,
        roles: u.roles,
        isActive: u.isActive,
        createdAt: now,
        updatedAt: now,
        lastLoginAt: null
      }
    },
    { upsert: true }
  );
}
print(`Upserted ${adminUsers.length} admin users.`);

print("Roles and admin users seeding completed successfully.");
