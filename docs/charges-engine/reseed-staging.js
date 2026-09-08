// Bring an environment seeded before 2026-09-08 up to the corrected rate cards.
//
// Needed once, and only because the AC-2 corrections edited cards that had already been deployed.
// ADR-26 forbids that from here on: a rate change ships as a new generation with a new
// scheduleCode, which POST /charges/seed applies with no deletion. Since ADR-27 nothing seeds at
// startup, so the cards come back when that endpoint is called rather than on the next restart.
//
// Run against the target database with mongosh, then re-seed through the API:
//
//   mongosh "mongodb+srv://$MONGO_USER:$MONGO_PASSWORD@it-staging.6fu8nvy.mongodb.net/?retryWrites=true&w=majority" \
//           docs/charges-engine/reseed-staging.js
//
// The database is selected below rather than taken from the URI, because the application passes it
// separately (spring.mongodb.database) and a URI without a path lands mongosh on "test" — where
// every count below reads zero and the script cheerfully reports there is nothing to do.

const DATABASE = "it-staging";
db = db.getSiblingDB(DATABASE);

// ---------------------------------------------------------------- 0. am I pointed at the right place?
if (!db.getCollectionNames().includes("charge_schedules")) {
    print("No charge_schedules collection in '" + DATABASE + "'. Either the application has never run "
        + "against this database, or this is the wrong one. Nothing changed.");
    quit(1);
}
print("database: " + DATABASE + "  (" + db.charge_schedules.countDocuments({}) + " cards on file)");

// ---------------------------------------------------------------- 1. is it safe?
// Deleting a card orphans the provenance of every charge it priced: UserChargeEntity.scheduleId
// would point at a document that no longer exists, and a corrected card cannot be traced to the
// rows it affected. Check before deleting.

const codes = db.charge_schedules.find({ schedule_code: /_2025_04$/ }, { schedule_code: 1 })
                .toArray().map(c => c.schedule_code);
print("cards to be replaced: " + codes.join(", "));

const priced = db.user_charges.countDocuments({ schedule_code: { $in: codes } });
print("charges priced by them: " + priced);

// ---------------------------------------------------------------- 2. the safe case
// Nothing priced. Delete and restart; the seeder rebuilds all six from the corrected files.
if (priced === 0) {
    const result = db.charge_schedules.deleteMany({ schedule_code: { $in: codes } });
    print("deleted " + result.deletedCount + " cards — now run: POST /charges/seed (SUPER_USER)");
}

// ---------------------------------------------------------------- 3. the unsafe case
// Something was priced by a card about to be replaced. Do NOT delete: the rows lose their
// provenance and the charges are wrong but no longer traceable. In staging the simplest honest
// reset is to clear the derived data too, and re-register any demat account afterwards:
//
//   db.user_charges.deleteMany({});
//   db.charge_accounts.deleteMany({});          // billing history and AMC watermarks
//   db.charge_schedules.deleteMany({ schedule_code: { $in: codes } });
//
// In production this branch is not available. It is what POST /charges/recompute exists for
// (tech-spec §14.4), and that endpoint is not built — which is why ADR-26 says a card that has
// priced anything must not be amended until it is.
