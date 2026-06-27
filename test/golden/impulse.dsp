# TICKS 8
# Golden: one-shot-ish pulse train, amplitude 100, first at delay=2, period 4.
# Expect 100 at ticks 2 and 6, else 0.
DSPFLOW 1
BLOCK Impulse 1 100 100
P amplitude=100
P delay=2
P period=4
P width=16
BLOCK Scope 2 300 100
P channels=1
WIRE 1 out 2 in1
