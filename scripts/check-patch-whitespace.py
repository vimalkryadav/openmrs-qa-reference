#!/usr/bin/env python3
"""Check newly added patch payload lines without rejecting unified-diff context markers."""
import subprocess
import sys

def payload_error(line):
    # The first + belongs to the outer git diff; the second belongs to a patch addition.
    return line.startswith('++') and not line.startswith('+++') and line[2:] != line[2:].rstrip(' \t\r')

if __name__ == '__main__':
    if len(sys.argv) != 2:
        raise SystemExit('Usage: check-patch-whitespace.py BASE...HEAD')
    diff = subprocess.check_output(['git', 'diff', '--no-ext-diff', '--unified=0', sys.argv[1], '--', '*.patch'], text=True)
    errors = [line for line in diff.splitlines() if payload_error(line)]
    for line in errors:
        print('Trailing whitespace in newly added patch payload: ' + repr(line[1:]), file=sys.stderr)
    raise SystemExit(bool(errors))
