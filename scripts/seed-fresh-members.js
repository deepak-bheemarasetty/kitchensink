// Run with: mongosh "$MONGODB_URI" scripts/seed-fresh-members.js
// Inserts 260 fresh demo records with the new structure (firstName, lastName, countryCode, phone)
const now = new Date();
const actorId = new ObjectId("000000000000000000000000");

const firstNames = [
  "James", "Mary", "Robert", "Patricia", "John", "Jennifer", "Michael", "Linda",
  "David", "Elizabeth", "William", "Barbara", "Richard", "Susan", "Joseph", "Jessica",
  "Thomas", "Sarah", "Charles", "Karen", "Christopher", "Lisa", "Daniel", "Nancy",
  "Matthew", "Betty", "Anthony", "Margaret", "Mark", "Sandra", "Donald", "Ashley",
  "Steven", "Kimberly", "Paul", "Emily", "Andrew", "Donna", "Joshua", "Michelle",
  "Kenneth", "Carol", "Kevin", "Amanda", "Brian", "Melissa", "George", "Deborah",
  "Timothy", "Stephanie", "Ronald", "Rebecca", "Jason", "Sharon", "Edward", "Laura",
  "Jeffrey", "Cynthia", "Ryan", "Dorothy", "Jacob", "Amy", "Gary", "Kathleen",
  "Nicholas", "Angela", "Eric", "Shirley", "Jonathan", "Emma", "Stephen", "Brenda",
  "Larry", "Pamela", "Justin", "Nicole", "Scott", "Anna", "Brandon", "Samantha"
];

const lastNames = [
  "Smith", "Johnson", "Williams", "Brown", "Jones", "Garcia", "Miller", "Davis",
  "Rodriguez", "Martinez", "Hernandez", "Lopez", "Gonzalez", "Wilson", "Anderson", "Thomas",
  "Taylor", "Moore", "Jackson", "Martin", "Lee", "Perez", "Thompson", "White",
  "Harris", "Sanchez", "Clark", "Ramirez", "Lewis", "Robinson", "Walker", "Young",
  "Allen", "King", "Wright", "Scott", "Torres", "Nguyen", "Hill", "Flores",
  "Green", "Adams", "Nelson", "Baker", "Hall", "Rivera", "Campbell", "Mitchell",
  "Carter", "Roberts", "Gomez", "Phillips", "Evans", "Turner", "Diaz", "Parker",
  "Cruz", "Edwards", "Collins", "Reyes", "Stewart", "Morris", "Morales", "Murphy",
  "Cook", "Rogers", "Gutierrez", "Ortiz", "Morgan", "Cooper", "Peterson", "Bailey"
];

const countryCodes = ["1", "44", "91", "61", "49", "33", "81", null];

// Generate 260 distinct fresh members
const freshMembers = [];
for (let i = 0; i < 260; i++) {
  const fn = firstNames[i % firstNames.length];
  const ln = lastNames[Math.floor(i / firstNames.length) % lastNames.length];
  const cc = countryCodes[i % countryCodes.length];
  // 10-digit phone number starting with digit 2-9
  const phone = String(2000000000 + (i * 12345) % 7000000000);
  const email = `${fn.toLowerCase()}.${ln.toLowerCase()}.${i + 1}@example.com`;
  freshMembers.push({
    firstName: fn,
    lastName: ln,
    email: email,
    countryCode: cc,
    phone: phone
  });
}

// Bulk upsert in batches of 50
const batchSize = 50;
let upsertedCount = 0;

for (let b = 0; b < freshMembers.length; b += batchSize) {
  const batch = freshMembers.slice(b, b + batchSize);
  const bulkOps = batch.map((m, idx) => {
    const overallIndex = b + idx;
    const hex = new ObjectId().toHexString().slice(-6).toUpperCase();
    const epochSec = Math.floor(now.getTime() / 1000) + overallIndex;
    return {
      updateOne: {
        filter: { emailLower: m.email.toLowerCase() },
        update: {
          $setOnInsert: {
            memberId: `M-${epochSec}-${hex}`,
            firstName: m.firstName,
            lastName: m.lastName,
            fullNameLower: `${m.firstName} ${m.lastName}`.trim().toLowerCase(),
            email: m.email,
            emailLower: m.email.toLowerCase(),
            countryCode: m.countryCode,
            phone: m.phone,
            status: "active",
            createdAt: new Date(now.getTime() + overallIndex * 1000),
            createdBy: actorId,
            updatedAt: new Date(now.getTime() + overallIndex * 1000),
            updatedBy: actorId,
            version: NumberInt(1),
            deletedAt: null,
            deletedBy: null
          }
        },
        upsert: true
      }
    };
  });

  const res = db.members.bulkWrite(bulkOps);
  upsertedCount += (res.upsertedCount || 0) + (res.matchedCount || 0);
}

print(`Fresh seed members upserted successfully: ${freshMembers.length} records processed.`);
