#!/usr/bin/python3

import tempfile
import os
from shutil import copyfile
import subprocess
from pexpect import fdpexpect
import sys
import signal

tempdir = tempfile.TemporaryDirectory()
cwd = os.getcwd()
os.chdir(tempdir.name)

copyfile(os.path.join(cwd, 'micropython/firmware.hex'),
        os.path.join(tempdir.name, 'insns.hex'))

cmd = [ os.path.join(cwd, 'chiselwatt') ]

devNull = open(os.devnull, 'w')
p = subprocess.Popen(cmd, stdout=subprocess.PIPE,
        stdin=subprocess.PIPE, stderr=subprocess.PIPE)

exp = fdpexpect.fdspawn(p.stdout)
exp.logfile = sys.stdout.buffer

exp.expect('Type "help\(\)" for more information.')
exp.expect('>>>')

p.stdin.write(b'n2=0\r\n')
p.stdin.write(b'n1=1\r\n')
p.stdin.write(b'for i in range(25):\r\n')
p.stdin.write(b'    n0 = n1 + n2\r\n')
p.stdin.write(b'    print(n0)\r\n')
p.stdin.write(b'    n2 = n1\r\n')
p.stdin.write(b'    n1 = n0\r\n')
p.stdin.write(b'\r\n')
p.stdin.flush()

exp.expect('n1 = n0', timeout=60)
n2 = 0
n1 = 1
for i in range(25):
    n0 = n1 + n2
    exp.expect("%s" % n0, timeout=60)
    n2 = n1
    n1 = n0

os.kill(p.pid, signal.SIGKILL)
