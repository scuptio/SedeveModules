---- MODULE StateStoreTests ----

EXTENDS StateStore, FiniteSets, TLC, TLCExt, Integers, Sequences

ASSUME LET T == INSTANCE TLC IN T!PrintT("StateStoreTests")

\* Serialize and Serialize
TestSerialize ==
    LET output == <<[a |-> 1, b |-> "a"], [a |-> 2, b |-> "b"], [a |-> 3, b |-> "c"]>>
    IN
       /\ SerializeValue("/tmp/state.txt", output)
       /\ LET input == DeserializeValue("/tmp/state.txt")
          IN  Len(input) = 3

ASSUME(TestSerialize)

\* Store and Load
TestStore ==
    LET a1 == <<[a |-> 1, b |-> "a"], [a |-> 2, b |-> "b"], [a |-> 3, b |-> "c"]>>
        a2 == <<[c |-> 2]>>
    IN
       /\ StoreOpen("/tmp/state.db")
       /\ StoreValue(a1)
       /\ StoreValue(a2)
       /\ LET input == LoadValue
          IN  /\ PrintT(input)
              /\ PrintT(a1)
              /\ PrintT(a1 \in input)
              /\ PrintT(a2 \in input)

ASSUME(TestStore)

====
