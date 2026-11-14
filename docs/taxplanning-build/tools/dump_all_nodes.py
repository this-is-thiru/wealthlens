# Throwaway full-trace dump for behaviour-unchanged refactors (W-088q2, W-088u1; D-265, D-271).
# `on` inserts one write statement (marked ZZDUMP) at NodeHarness.run and PipelineRunner's two evaluate sites;
# `off` removes it. Never commit with it on: `grep -rl ZZDUMP backend/src` must be empty.
# Use: ZZDUMP_FILE=<scratch>/x.txt python3 dump_all_nodes.py on; ./mvnw -q clean test -pl backend -Punit -Dcheckstyle.skip
#      -Dspotless.check.skip=true; python3 dump_all_nodes.py off. Do it on HEAD and on the change, then norm2.py and
#      maskdoubling.py each file and diff. The patch's own `try` fails PipelineRunnerTest.thereIsNoTryInTheRunner in
#      dump runs only (expected).
import os
import sys
R=os.path.join(os.path.dirname(os.path.abspath(__file__)), '../../../backend/src/')
DUMP=os.environ['ZZDUMP_FILE']  # where each run appends its records; a file outside the repo
def w(tag,expr):
    return ('try { java.nio.file.Files.writeString(java.nio.file.Path.of("'+DUMP+'"), ("'+tag+' " + '+expr+').replace("\\n", "\\\\n") + "\\n",'
            ' java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND); }'
            ' catch (java.io.IOException zze) { throw new java.io.UncheckedIOException(zze); } // ZZDUMP')
sites=[
 (R+'test/java/com/thiru/wealthlens/taxengine/test/NodeHarness.java',
  '        return node.evaluate(run.forNode(node));\n',
  '        NodeResult zzr = node.evaluate(run.forNode(node)); // ZZDUMP\n        '+w('HARNESS','node.produces() + " " + zzr.value() + " || " + zzr.trace()')+'\n        return zzr; // ZZDUMP\n'),
 (R+'main/java/com/thiru/wealthlens/taxengine/pipeline/PipelineRunner.java',
  '            NodeResult result = node.evaluate(context.forNode(node));\n',
  '            NodeResult result = node.evaluate(context.forNode(node));\n            '+w('PIPE','node.produces() + " " + result.value() + " || " + result.trace()')+'\n'),
 (R+'main/java/com/thiru/wealthlens/taxengine/pipeline/PipelineRunner.java',
  '                answer = node.evaluate(store.forNode(node)).value();\n',
  '                answer = node.evaluate(store.forNode(node)).value();\n                '+w('RECOMP','node.produces() + " " + answer')+'\n'),
]
for p,anchor,rep in sites:
    s=open(p).read()
    if sys.argv[1]=='on':
        assert s.count(anchor)==1,(p,anchor); s=s.replace(anchor,rep)
    else:
        if anchor.strip().startswith('return node.evaluate') and 'zzr' in s:
            s=s.replace(rep,anchor)
        else:
            s=''.join(l for l in s.splitlines(keepends=True) if 'ZZDUMP' not in l)
    open(p,'w').write(s)
