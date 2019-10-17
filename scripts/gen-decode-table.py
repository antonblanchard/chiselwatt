#!/usr/bin/python3

import yaml

# Print all the common keys in order each time, makes diffing between
# versions easier.
base_keys = [ 'unit', 'internalOp', 'rA', 'rB', 'rS', 'rOut', 'carryIn', 'carryOut', 'crIn', 'crOut', 'compare', 'is32bit', 'signed', 'invertIn', 'invertOut', 'rightShift', 'clearLeft', 'clearRight', 'length', 'byteReverse', 'update', 'reservation', 'high', 'extended', 'countRight' ]

all_keys = list()
all_lengths = list()

with open('instructions.yaml') as fh:
    insns = yaml.safe_load(fh)

    all_keys = list()

    for key in base_keys:
        all_keys.append(key)
        all_lengths.append(len(key) + 1)

    # Add all the keys and find the maximum length of either the name or the value
    for insn in insns:
        for key in insns[insn].keys():
            if key not in all_keys:
                all_keys.append(key)
                keylen = len(key) + 1
                vallen = len(insns[insn][key]) + 2
                all_lengths.append(max(keylen, vallen))
            else:
                i = all_keys.index(key)
                all_lengths[i] = max(all_lengths[i], len(insns[insn][key]) + 2)

    print("                       // ", end = '')
    for i in range(len(all_keys)):
        key = all_keys[i]
        length = all_lengths[i]
        print("%-*s" % (length, key), end = '')
    print("")

    for insn in insns:
        print("    %-13s -> List(" % (insn.upper()), end = '')
        for i in range(len(all_keys)):
            key = all_keys[i]
            length = all_lengths[i]

            if (i == (len(all_keys)-1)):
                append = '),' 
            else:
                append = ','

            if key in insns[insn]:
                val = insns[insn][key] + append
            else:
                val = 'DC' + append

            print("%-*s" % (length, val), end = '')
        print("")
