DSPFLOW 1
BLOCK Impulse 1 120 200
P amplitude=1024
P delay=2
P period=0
P width=16
BLOCK Sum 2 320 200
P signs=++
P width=20
BLOCK Delay 3 500 200
P depth=1
P width=20
BLOCK Scope 4 500 80
P channels=1
WIRE 1 out 2 in1
WIRE 2 out 3 in
WIRE 3 out 2 in2
WIRE 2 out 4 in1
