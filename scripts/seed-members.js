const members = [
  {
    name: "John Smith",
    email: "john.smith@mailinator.com",
    phoneNumber: "2125551212"
  },
  { name: "Alice Johnson", email: "alice.johnson@example.com", phoneNumber: "2125551001" },
  { name: "Michael Brown", email: "michael.brown@example.com", phoneNumber: "2125551002" },
  { name: "Emily Davis", email: "emily.davis@example.com", phoneNumber: "2125551003" },
  { name: "Daniel Wilson", email: "daniel.wilson@example.com", phoneNumber: "2125551004" },
  { name: "Sophia Miller", email: "sophia.miller@example.com", phoneNumber: "2125551005" },
  { name: "James Taylor", email: "james.taylor@example.com", phoneNumber: "2125551006" },
  { name: "Olivia Anderson", email: "olivia.anderson@example.com", phoneNumber: "2125551007" },
  { name: "David Thomas", email: "david.thomas@example.com", phoneNumber: "2125551008" },
  { name: "Ava Jackson", email: "ava.jackson@example.com", phoneNumber: "2125551009" },
  { name: "Robert White", email: "robert.white@example.com", phoneNumber: "2125551010" },
  { name: "Isabella Harris", email: "isabella.harris@example.com", phoneNumber: "2125551011" },
  { name: "Joseph Martin", email: "joseph.martin@example.com", phoneNumber: "2125551012" },
  { name: "Mia Thompson", email: "mia.thompson@example.com", phoneNumber: "2125551013" },
  { name: "Charles Garcia", email: "charles.garcia@example.com", phoneNumber: "2125551014" },
  { name: "Charlotte Martinez", email: "charlotte.martinez@example.com", phoneNumber: "2125551015" },
  { name: "Thomas Robinson", email: "thomas.robinson@example.com", phoneNumber: "2125551016" },
  { name: "Amelia Clark", email: "amelia.clark@example.com", phoneNumber: "2125551017" },
  { name: "Christopher Lewis", email: "christopher.lewis@example.com", phoneNumber: "2125551018" },
  { name: "Harper Lee", email: "harper.lee@example.com", phoneNumber: "2125551019" },
  { name: "Matthew Walker", email: "matthew.walker@example.com", phoneNumber: "2125551020" },
  { name: "Evelyn Hall", email: "evelyn.hall@example.com", phoneNumber: "2125551021" },
  { name: "Anthony Allen", email: "anthony.allen@example.com", phoneNumber: "2125551022" },
  { name: "Abigail Young", email: "abigail.young@example.com", phoneNumber: "2125551023" },
  { name: "Mark Hernandez", email: "mark.hernandez@example.com", phoneNumber: "2125551024" },
  { name: "Andrew King", email: "andrew.king@example.com", phoneNumber: "2125551025" },
  { name: "Ella Wright", email: "ella.wright@example.com", phoneNumber: "2125551026" },
  { name: "Joshua Lopez", email: "joshua.lopez@example.com", phoneNumber: "2125551027" },
  { name: "Grace Hill", email: "grace.hill@example.com", phoneNumber: "2125551028" },
  { name: "Ryan Scott", email: "ryan.scott@example.com", phoneNumber: "2125551029" },
  { name: "Lily Green", email: "lily.green@example.com", phoneNumber: "2125551030" },
  { name: "Brandon Adams", email: "brandon.adams@example.com", phoneNumber: "2125551031" },
  { name: "Zoe Baker", email: "zoe.baker@example.com", phoneNumber: "2125551032" },
  { name: "Kevin Nelson", email: "kevin.nelson@example.com", phoneNumber: "2125551033" },
  { name: "Nora Carter", email: "nora.carter@example.com", phoneNumber: "2125551034" },
  { name: "Jason Mitchell", email: "jason.mitchell@example.com", phoneNumber: "2125551035" },
  { name: "Chloe Perez", email: "chloe.perez@example.com", phoneNumber: "2125551036" },
  { name: "Eric Roberts", email: "eric.roberts@example.com", phoneNumber: "2125551037" },
  { name: "Lena Turner", email: "lena.turner@example.com", phoneNumber: "2125551038" },
  { name: "Justin Phillips", email: "justin.phillips@example.com", phoneNumber: "2125551039" },
  { name: "Maya Campbell", email: "maya.campbell@example.com", phoneNumber: "2125551040" },
  { name: "Sean Parker", email: "sean.parker@example.com", phoneNumber: "2125551041" },
  { name: "Ruby Evans", email: "ruby.evans@example.com", phoneNumber: "2125551042" },
  { name: "Peter Edwards", email: "peter.edwards@example.com", phoneNumber: "2125551043" },
  { name: "Violet Collins", email: "violet.collins@example.com", phoneNumber: "2125551044" },
  { name: "Patrick Stewart", email: "patrick.stewart@example.com", phoneNumber: "2125551045" },
  { name: "Claire Sanchez", email: "claire.sanchez@example.com", phoneNumber: "2125551046" },
  { name: "Gregory Morris", email: "gregory.morris@example.com", phoneNumber: "2125551047" },
  { name: "Stella Rogers", email: "stella.rogers@example.com", phoneNumber: "2125551048" },
  { name: "Kenneth Reed", email: "kenneth.reed@example.com", phoneNumber: "2125551049" },
  { name: "Naomi Cook", email: "naomi.cook@example.com", phoneNumber: "2125551050" },
  { name: "Raymond Morgan", email: "raymond.morgan@example.com", phoneNumber: "2125551051" },
  { name: "Lucy Bell", email: "lucy.bell@example.com", phoneNumber: "2125551052" },
  { name: "Dennis Murphy", email: "dennis.murphy@example.com", phoneNumber: "2125551053" },
  { name: "Audrey Bailey", email: "audrey.bailey@example.com", phoneNumber: "2125551054" },
  { name: "Walter Rivera", email: "walter.rivera@example.com", phoneNumber: "2125551055" },
  { name: "Sarah Cooper", email: "sarah.cooper@example.com", phoneNumber: "2125551056" },
  { name: "Keith Richardson", email: "keith.richardson@example.com", phoneNumber: "2125551057" },
  { name: "Anna Cox", email: "anna.cox@example.com", phoneNumber: "2125551058" },
  { name: "Roger Howard", email: "roger.howard@example.com", phoneNumber: "2125551059" },
  { name: "Eliza Ward", email: "eliza.ward@example.com", phoneNumber: "2125551060" },
  { name: "Frank Torres", email: "frank.torres@example.com", phoneNumber: "2125551061" },
  { name: "Ivy Peterson", email: "ivy.peterson@example.com", phoneNumber: "2125551062" },
  { name: "Albert Gray", email: "albert.gray@example.com", phoneNumber: "2125551063" },
  { name: "Molly Ramirez", email: "molly.ramirez@example.com", phoneNumber: "2125551064" },
  { name: "Philip James", email: "philip.james@example.com", phoneNumber: "2125551065" },
  { name: "Hazel Watson", email: "hazel.watson@example.com", phoneNumber: "2125551066" },
  { name: "Louis Brooks", email: "louis.brooks@example.com", phoneNumber: "2125551067" },
  { name: "Daisy Kelly", email: "daisy.kelly@example.com", phoneNumber: "2125551068" },
  { name: "Bruce Sanders", email: "bruce.sanders@example.com", phoneNumber: "2125551069" },
  { name: "Sophie Price", email: "sophie.price@example.com", phoneNumber: "2125551070" },
  { name: "Martin Bennett", email: "martin.bennett@example.com", phoneNumber: "2125551071" },
  { name: "Paige Wood", email: "paige.wood@example.com", phoneNumber: "2125551072" },
  { name: "Leon Barnes", email: "leon.barnes@example.com", phoneNumber: "2125551073" },
  { name: "Faith Ross", email: "faith.ross@example.com", phoneNumber: "2125551074" }
].map((member, index) => {
  const timestamp = new Date();
  return {
    ...member,
    _id: NumberLong(index + 1),
    countryCode: 1,
    createdBy: "seed",
    creationDate: timestamp,
    lastUpdatedBy: "seed",
    lastUpdateDate: timestamp,
    version: NumberLong(0)
  };
});

db.members.bulkWrite(
  members.map(member => ({
    updateOne: {
      filter: { email: member.email },
      update: { $setOnInsert: member },
      upsert: true
    }
  }))
);

db.sequences.updateOne(
  { _id: "members" },
  { $setOnInsert: { sequence: NumberLong(members.length) } },
  { upsert: true }
);
