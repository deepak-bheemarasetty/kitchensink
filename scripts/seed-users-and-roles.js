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
// Administrator accounts can be specified via environment variable ADMIN_EMAILS (comma-separated),
// e.g.: ADMIN_EMAILS="admin@example.com,secops@example.com" mongosh "$MONGODB_URI" scripts/seed-users-and-roles.js
const adminEmailsEnv = typeof process !== "undefined" && process.env && process.env.ADMIN_EMAILS ? process.env.ADMIN_EMAILS : "";
const adminEmails = adminEmailsEnv
  .split(",")
  .map(e => e.trim())
  .filter(Boolean);

if (adminEmails.length > 0) {
  print(`Seeding ${adminEmails.length} administrator user account(s) from ADMIN_EMAILS...`);
  for (const email of adminEmails) {
    const emailLower = email.toLowerCase();
    db.users.updateOne(
      { usernameLower: emailLower },
      {
        $setOnInsert: {
          username: email,
          usernameLower: emailLower,
          roles: ["admin"],
          isActive: true,
          createdAt: now,
          updatedAt: now,
          lastLoginAt: null
        }
      },
      { upsert: true }
    );
  }
  print(`Upserted configured admin users.`);
} else {
  print("No admin emails provided via ADMIN_EMAILS. Skipping admin user seeding.");
}

print("Roles and users script execution completed successfully.");
