// Run with: mongosh "$MONGODB_URI" scripts/reset-and-seed-members.js
// Environment safety guard (VULN-038)
const dbName = db.getName();
if (dbName === "production" || dbName === "prod" || (!dbName.includes("dev") && !dbName.includes("test") && !dbName.includes("kitchensink"))) {
  print("ERROR: Refusing to run destructive reset against non-dev/non-test database: " + dbName);
  quit(1);
}

// 1. Wipe collections
db.members.deleteMany({});
db.audit_logs.deleteMany({});
print("Cleaned members and audit_logs collections in db: " + dbName);

// 2. Load fresh seed data
load("scripts/seed-fresh-members.js");

// 3. Load migrated legacy seed data
load("scripts/seed-legacy-members.js");

print("All seed data reset and loaded.");
