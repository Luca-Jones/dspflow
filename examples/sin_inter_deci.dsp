DSPFLOW 1
BLOCK Sine 1 30 270 110 60 0 0 0
P amplitude=1000
P period=32
P phase_deg=0
P cosine=0
P width=16
BLOCK Interp 4 220 370 110 60 0 0 0
P factor=2
P phase=0
P width=16
BLOCK Scope 5 450 290 110 82 0 0 0
P channels=3
P name=scope_2
BLOCK Clock 6 -50 380 110 60 0 0 0
P divide=2
P phase=0
BLOCK Sine 7 30 170 110 60 0 0 0
P amplitude=1000
P period=32
P phase_deg=0
P cosine=0
P width=16
BLOCK Sine 8 30 510 110 60 0 0 0
P amplitude=1000
P period=32
P phase_deg=0
P cosine=0
P width=16
BLOCK Decim 9 260 510 110 60 0 0 0
P factor=2
P phase=0
P width=16
WIRE 1 out 4 in
WIRE 6 ce 1 ce
WIRE 7 out 5 in1
W 260 200
W 260 310
WIRE 4 out 5 in2
WIRE 8 out 9 in
WIRE 9 out 5 in3
