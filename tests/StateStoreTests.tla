---- MODULE StateDBTests ----

EXTENDS StateDB, FiniteSets, TLC, TLCExt, Integers, Sequences

ASSUME LET T == INSTANCE TLC IN T!PrintT("StateDBTests")


\* Store and Load
TestDB ==
    LET a1 == <<[b |-> "a"]>>
        a2 == <<[b |-> "b"]>>
    IN
       /\ DBOpen("/tmp/state.db")
       /\ Put(a1)
       /\ Put(a2)
       /\ LET input == QueryAll
          IN  /\ PrintT(input)
              /\ PrintT(a1)
              /\ PrintT(a1 \in input)
              /\ PrintT(a2 \in input)

ASSUME(TestDB)

====
