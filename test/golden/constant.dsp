# TICKS 4
# Golden: a constant 7 should read 7 on every tick.
DSPFLOW 1
BLOCK Constant 1 100 100
P value=7
P width=16
BLOCK Scope 2 300 100
P channels=1
WIRE 1 out 2 in1
