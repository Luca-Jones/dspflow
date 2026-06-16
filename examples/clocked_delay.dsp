DSPFLOW 1
BLOCK Sine 1 100 200
P amplitude=10000
P period=64
P phase_deg=0
P cosine=0
P width=16
BLOCK Clock 2 100 340
P divide=8
P phase=0
BLOCK Delay 3 320 200
P depth=1
P width=16
BLOCK Scope 4 540 200
P channels=2
WIRE 1 out 3 in
WIRE 2 out 3 ce
WIRE 1 out 4 in1
WIRE 3 out 4 in2
