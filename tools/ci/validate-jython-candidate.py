import os
import sys

from java.lang import StringBuilder

builder = StringBuilder()
builder.append("Jython candidate Java interop")
if str(builder.toString()) != "Jython candidate Java interop":
    raise RuntimeError("Java interop smoke check failed")

script_root = sys.argv[1]
failures = []
count = 0

for directory, subdirectories, filenames in os.walk(script_root):
    for filename in filenames:
        if not filename.endswith(".py"):
            continue
        count += 1
        path = os.path.join(directory, filename)
        try:
            source = open(path, "r").read()
            compile(source, path, "exec")
        except Exception, error:
            failures.append((path, str(error)))

print "Jython candidate syntax probe: %d scripts scanned, %d failed." % (count, len(failures))
for path, error in failures[:25]:
    print "Jython syntax migration candidate: %s: %s" % (path, error)

if failures:
    sys.exit(1)
