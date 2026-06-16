DSPFLOW 1
BLOCK Sine 1 100 220
P amplitude=12000
P period=32
P phase_deg=0
P cosine=0
P width=16
BLOCK Decim 2 300 220
P factor=4
P phase=0
BLOCK Interp 3 480 220
P factor=4
P phase=0
BLOCK Scope 4 680 140
P channels=2
BLOCK Spectrum 5 680 320
P fft=1024
P hann=1
WIRE 1 out 4 in1
WIRE 1 out 2 in
WIRE 2 out 3 in
WIRE 3 out 4 in2
WIRE 3 out 5 in
