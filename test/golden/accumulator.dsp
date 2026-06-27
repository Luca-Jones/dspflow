# TICKS 6
# Golden: accumulator ramp. Constant(1) + Delay(feedback) -> Sum -> Delay.
# Scope ch1 = sum output (ramp 1,2,3,...), ch2 = the constant (always 1).
# At each tick's clockEdge the scope samples sum AFTER evaluate:
#   t0 delay=0 -> sum=1 ; t1 delay=1 -> sum=2 ; ... so ch1 = 1,2,3,4,5,6.
DSPFLOW 1
BLOCK Constant 1 100 100
P value=1
P width=16
BLOCK Sum 2 300 100
P signs=++
P width=16
BLOCK Delay 3 500 100
P width=16
P depth=1
BLOCK Scope 4 700 100
P channels=2
WIRE 1 out 2 in1
WIRE 3 out 2 in2
WIRE 2 out 3 in
WIRE 2 out 4 in1
WIRE 1 out 4 in2
