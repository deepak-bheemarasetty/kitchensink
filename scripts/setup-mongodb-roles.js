// Run with mongosh as an administrator on the MongoDB deployment.
// Set MONGODB_APP_DB to the target database name before running.
const appDbName = process.env.MONGODB_APP_DB || "kitchensink";
const appDb = db.getSiblingDB(appDbName);

const users = [
  { name: "kitchensink_app", role: "kitchensinkApp" },
  { name: "kitchensink_admin", role: "kitchensinkAdmin" },
  { name: "kitchensink_editor", role: "kitchensinkEditor" },
  { name: "kitchensink_viewer", role: "kitchensinkViewer" }
];

// Prompt for passwords instead of storing credentials in this script.
const credentials = users.map(user => {
  const pwd = passwordPrompt(`MongoDB password for ${user.name}: `);
  if (!pwd) throw new Error(`A password is required for ${user.name}`);
  return { ...user, pwd };
});

const roleDefinitions = [
  {
    role: "kitchensinkApp",
    privileges: [
      { resource: { db: appDbName, collection: "" }, actions: ["find", "insert", "update", "remove", "createCollection", "dropCollection", "createIndex", "dropIndex", "collMod", "bypassDocumentValidation"] }
    ]
  },
  {
    role: "kitchensinkAdmin",
    privileges: [
    { resource: { db: appDbName, collection: "" }, actions: ["find", "insert", "update", "remove"] },
    { resource: { db: appDbName, collection: "" }, actions: ["createCollection", "dropCollection", "createIndex", "dropIndex", "collMod"] },
    { resource: { db: appDbName, collection: "" }, actions: ["createUser", "dropUser", "grantRole", "revokeRole", "viewUser", "changePassword", "createRole", "dropRole", "grantPrivilegesToRole", "revokePrivilegesFromRole", "viewRole"] }
    ]
  },
  {
    role: "kitchensinkEditor",
    privileges: [
    { resource: { db: appDbName, collection: "" }, actions: ["find", "insert", "update", "remove"] }
    ]
  },
  {
    role: "kitchensinkViewer",
    privileges: [
    { resource: { db: appDbName, collection: "" }, actions: ["find"] }
    ]
  }
];

for (const definition of roleDefinitions) {
  const roleDoc = { ...definition, roles: [] };
  if (appDb.getRole(definition.role)) appDb.updateRole(definition.role, roleDoc);
  else appDb.createRole(roleDoc);
}

for (const user of credentials) {
  const userDoc = { user: user.name, pwd: user.pwd, roles: [{ role: user.role, db: appDbName }] };
  if (appDb.getUser(user.name)) appDb.updateUser(user.name, { pwd: user.pwd, roles: userDoc.roles });
  else appDb.createUser(userDoc);
}

print(`Created application, admin, editor, and viewer database roles and users scoped to ${appDbName}.`);
