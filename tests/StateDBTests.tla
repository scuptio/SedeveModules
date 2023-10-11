---- MODULE StateDBTests ----

EXTENDS StateDB, FiniteSets, TLC, TLCExt, Integers, Sequences

ASSUME LET T == INSTANCE TLC IN T!PrintT("StateDBTests")


\* Test create and query states
TestState ==
    LET a1 == <<[b |-> "a"]>>
        a2 == <<[b |-> "b"]>>
    IN
       /\ DBOpen("/tmp/state.db")
       /\ CreateState(a1)
       /\ CreateState(a2)
       /\ LET s == QueryAllStates
          IN {a1, a2} = s

	   
ASSUME(TestState)


\* Test store and load value
TestStoreLoad ==
    LET a == "a"
        b == "b"
    IN
       /\ DBOpen("/tmp/value.db")
       /\ StoreValue(a, a)
       /\ StoreValue(b, b)
       /\ a = LoadValue(a)
       /\ b = LoadValue(b)

	   
ASSUME(TestStoreLoad)

====
