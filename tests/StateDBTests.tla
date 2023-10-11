---- MODULE StateDBTests ----

EXTENDS StateDB, FiniteSets, TLC, TLCExt, Integers, Sequences

ASSUME LET T == INSTANCE TLC IN T!PrintT("StateDBTests")


\* Store and Load
TestStore ==
    LET a1 == <<[b |-> "a"]>>
        a2 == <<[b |-> "b"]>>
    IN
       /\ DBOpen("/tmp/state.db")
       /\ Put(a1)
       /\ Put(a2)
       /\ LET s == QueryAll
          IN  {a1, a2} = s

ASSUME(TestStore)

====
