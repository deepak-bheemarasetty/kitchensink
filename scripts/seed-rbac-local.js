// Local development only. Run against localhost/kitchensink after V2.
if (db.getName() !== "kitchensink") {
  throw new Error("Refusing to seed a database other than kitchensink");
}

const permissions = {
  viewer: ["member:view"],
  creator: ["member:view", "member:create"],
  editor: ["member:view", "member:update"],
  deleter: ["member:view", "member:delete"],
  manager: ["member:view", "member:create", "member:update", "member:delete"],
  admin: ["member:view", "member:create", "member:update", "member:delete", "user:manage", "role:manage"]
};
const now = new Date();

for (const [name, rolePermissions] of Object.entries(permissions)) {
  db.roles.updateOne(
    { name },
    { $set: { description: `${name} role`, permissions: rolePermissions, isSystem: name === "viewer" || name === "admin", updatedAt: now } },
    { upsert: true }
  );
}

const users = [
  ["kitchensink.viewer@mail.com", "viewer", "$2a$10$//aKxkPsw7yGJndseJeoTeH73qy5Al/tWf3OeQHSj6MDV6a03EMiq"],
  ["kitchensink.creator@mail.com", "creator", "$2a$10$p/cXtETfkVqkpToOWShCy.fRzjWFgW0VJkdedI3/eoTTRBhFGTw.K"],
  ["kitchensink.editor@mail.com", "editor", "$2a$10$hHLHeC5pTlCJfs47EDVRbeuPpdvaXXVo5iljf9Y8xmbGDcMHM0P1C"],
  ["kitchensink.deleter@mail.com", "deleter", "$2a$10$i3zjhrscoBkV4upa/y1H9e0OWFAM1QSXfLjsFSm97nfhGKz4VwuQC"],
  ["kitchensink.manager@mail.com", "manager", "$2a$10$HIWgniqXGJ6K7Q.kUiwENu2fatomHKgvaF3uETu9LnKAt76xTnw5q"],
  ["kitchensink.admin@mail.com", "admin", "$2a$10$xgoIrxw4M8E5m9sX2RUxIePIR2Z0NhnH0UBj1p9dq9nikStIt4dBy"]
];

for (const [username, role, passwordHash] of users) {
  db.users.updateOne(
    { usernameLower: username.toLowerCase() },
    {
      $setOnInsert: {
        username,
        usernameLower: username.toLowerCase(),
        passwordHash,
        roles: [role],
        isActive: true,
        createdAt: now,
        lastLoginAt: null
      },
      $set: { updatedAt: now }
    },
    { upsert: true }
  );
}

printjson({ seededRoles: db.roles.countDocuments(), testUsers: db.users.countDocuments({ username: /^kitchensink\./ }) });
