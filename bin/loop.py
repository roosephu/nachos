#!/usr/bin/python

# This file runs nachos repeatly, and will stop when errors are thrown
# or the program enters endless loop.

import os, sys
os.system('make')
cnt = 1
print '',
while True:
    print cnt, '',
    sys.stdout.flush()
    cnt += 1
    code = os.system("java nachos.machine.Machine -d X > /dev/null")
    if code != 0:
        break
