#!/bin/zsh
# Relock both rule packs after editing backend/src/main/resources/rulepacks/timeline (session 26).
# Usage from the repo root: zsh docs/taxplanning-build/tools/run_relock.sh <BUMP 0|1> <scratch dir>
# Copies the throwaway checksum printer into the test tree, runs it (it fails by design, printing CKSUM lines),
# deletes it, and rewrites the locks. Always check no ZzPrintChecksumsTest.java is left in backend/src/test.
set -u
BUMP=$1; OUT=$2; TOOLS=docs/taxplanning-build/tools
T=backend/src/test/java/com/thiru/wealthlens/taxengine/rules/timeline/ZzPrintChecksumsTest.java
cp $TOOLS/zz_print_checksums_test.java.txt $T
./mvnw test -pl backend -Dtest=ZzPrintChecksumsTest -Dcheckstyle.skip -Dspotless.check.skip=true -Dsurefire.failIfNoSpecifiedTests=false > "$OUT/zz.out" 2>&1
rm -f "$T"
grep "CKSUM" "$OUT/zz.out" | sed 's/^.*CKSUM/CKSUM/' | sort -u > "$OUT/cksum.txt"
wc -l < "$OUT/cksum.txt"
python3 -I $TOOLS/relock_rulepacks.py "$OUT/cksum.txt" backend/src/main/resources/rulepacks "$BUMP"
