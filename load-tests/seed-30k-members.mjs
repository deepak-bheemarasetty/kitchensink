import dotenv from 'dotenv';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { MongoClient, ObjectId } from 'mongodb';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
dotenv.config({ path: path.resolve(__dirname, '.env') });

const mongoUri = process.env.MONGODB_URI || 'mongodb://localhost:27017/kitchensink_loadtest?replicaSet=rs0';

/**
 * Seeds or tops-up the baseline members collection to targetCount using the member.0#####@load.test pattern.
 * @param {number} targetCount 
 * @param {string} targetUri 
 */
export async function seedBaselineMembers(targetCount = 30000, targetUri = mongoUri) {
  const client = new MongoClient(targetUri);
  await client.connect();
  const db = client.db();
  const membersColl = db.collection('members');

  console.log(`[Seed Baseline] Connected to database: ${db.databaseName}`);

  // Fetch an admin user id for actor auditing reference
  const adminUser = await db.collection('users').findOne({ usernameLower: 'user.0001@load.test' }) 
                    || await db.collection('users').findOne({});
  const actorId = adminUser ? adminUser._id : new ObjectId();

  const currentCount = await membersColl.countDocuments({ emailLower: /@load\.test$/ });
  console.log(`[Seed Baseline] Current load.test members count: ${currentCount} (target: ${targetCount})`);

  if (currentCount >= targetCount) {
    console.log(`[Seed Baseline] Already have ${currentCount} >= ${targetCount} load.test members. No insertion needed.`);
    await client.close();
    return;
  }

  const toCreate = targetCount - currentCount;
  console.log(`[Seed Baseline] Generating and inserting ${toCreate} members in batches...`);

  const BATCH_SIZE = 2500;
  let created = 0;
  const now = new Date();

  // Find max index to continue cleanly
  const lastMember = await membersColl
    .find({ emailLower: /^member\.\d+@load\.test$/ })
    .sort({ emailLower: -1 })
    .limit(1)
    .toArray();

  let startIndex = 1;
  if (lastMember.length > 0) {
    const match = lastMember[0].emailLower.match(/member\.(\d+)@load\.test/);
    if (match) {
      startIndex = parseInt(match[1], 10) + 1;
    }
  }

  while (created < toCreate) {
    const batchSize = Math.min(BATCH_SIZE, toCreate - created);
    const batch = [];

    for (let i = 0; i < batchSize; i++) {
      const num = startIndex + created + i;
      const padNum = String(num).padStart(5, '0');
      const email = `member.${padNum}@load.test`;
      const firstName = `Member${padNum}`;
      const lastName = `Test`;
      const fullNameLower = `${firstName.toLowerCase()} ${lastName.toLowerCase()}`;
      const memberId = `M-${String(num).padStart(16, '0')}`;
      
      // Phone format must be 6-14 digits starting with 1-9
      const phoneDigits = String(1000000000 + (num % 8999999999));

      batch.push({
        _id: new ObjectId(),
        memberId: memberId,
        firstName: firstName,
        lastName: lastName,
        fullNameLower: fullNameLower,
        email: email,
        emailLower: email.toLowerCase(),
        countryCode: '1',
        phone: phoneDigits,
        status: 'active',
        version: 1,
        createdAt: now,
        createdBy: actorId,
        updatedAt: now,
        updatedBy: actorId,
        deletedAt: null,
        deletedBy: null
      });
    }

    await membersColl.insertMany(batch, { ordered: false });
    created += batchSize;
    process.stdout.write(`\r[Seed Baseline] Progress: ${created}/${toCreate} inserted...`);
  }

  console.log('\n[Seed Baseline] Insertion completed successfully!');
  const finalCount = await membersColl.countDocuments({ emailLower: /@load\.test$/ });
  console.log(`[Seed Baseline] Total load.test members in collection: ${finalCount}`);

  await client.close();
}

if (process.argv[1] && process.argv[1].endsWith('seed-30k-members.mjs')) {
  const target = process.argv[2] ? parseInt(process.argv[2], 10) : 30000;
  seedBaselineMembers(target)
    .then(() => process.exit(0))
    .catch(err => {
      console.error('\n[Seed Baseline] Error:', err);
      process.exit(1);
    });
}

