import 'dotenv/config';
import { MongoClient, ObjectId } from 'mongodb';

const mongoUri = process.env.MONGODB_URI || 'mongodb://localhost:27017/kitchensink?replicaSet=rs0';

// Roles distribution across 100 users:
// admin: 15, manager: 20, creator: 25, editor: 20, deleter: 10, viewer: 10
const ROLE_DISTRIBUTION = [
  { role: 'admin', count: 15 },
  { role: 'manager', count: 20 },
  { role: 'creator', count: 25 },
  { role: 'editor', count: 20 },
  { role: 'deleter', count: 10 },
  { role: 'viewer', count: 10 }
];

export async function seed100Users(targetUri = mongoUri) {
  const client = new MongoClient(targetUri);
  await client.connect();
  const db = client.db();
  const usersColl = db.collection('users');

  console.log(`[Seed Users] Connected to database: ${db.databaseName}`);

  const usersToInsert = [];
  let userIndex = 1;
  const now = new Date();

  for (const group of ROLE_DISTRIBUTION) {
    for (let i = 0; i < group.count; i++) {
      const padNum = String(userIndex).padStart(4, '0');
      const username = `user.${padNum}@load.test`;
      usersToInsert.push({
        username: username,
        usernameLower: username.toLowerCase(),
        roles: [group.role],
        isActive: true,
        createdAt: now,
        updatedAt: now,
        lastLoginAt: null
      });
      userIndex++;
    }
  }

  console.log(`[Seed Users] Preparing ${usersToInsert.length} load.test users...`);

  let inserted = 0;
  let updated = 0;

  for (const user of usersToInsert) {
    const res = await usersColl.updateOne(
      { usernameLower: user.usernameLower },
      {
        $setOnInsert: {
          _id: new ObjectId(),
          createdAt: user.createdAt,
        },
        $set: {
          username: user.username,
          usernameLower: user.usernameLower,
          roles: user.roles,
          isActive: user.isActive,
          updatedAt: user.updatedAt,
        }
      },
      { upsert: true }
    );
    if (res.upsertedCount > 0) inserted++;
    else updated++;
  }

  console.log(`[Seed Users] Done! Inserted: ${inserted}, Updated: ${updated}. Total in DB: ${await usersColl.countDocuments({ usernameLower: /@load\.test$/ })}`);
  await client.close();
}

if (process.argv[1] && process.argv[1].endsWith('seed-100-users.mjs')) {
  seed100Users()
    .then(() => process.exit(0))
    .catch(err => {
      console.error('[Seed Users] Error:', err);
      process.exit(1);
    });
}

