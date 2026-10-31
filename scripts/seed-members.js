db.members.updateOne(
  { _id: ObjectId("507f1f77bcf86cd799439011") },
  {
    $setOnInsert: {
      name: "John Smith",
      email: "john.smith@mailinator.com",
      phoneNumber: "2125551212"
    }
  },
  { upsert: true }
);
