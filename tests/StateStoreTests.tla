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
    LET output == <<[a |-> 1, b |-> "a"], [a |-> 2, b |-> "b"], [a |-> 3, b |-> "c"]>>
    IN
       /\ StoreOpen("/tmp/state.db")
       /\ StoreValue(output)
       /\ LET input == LoadValue
          IN  Cardinality(input) = 1

ASSUME(TestStore)

====
