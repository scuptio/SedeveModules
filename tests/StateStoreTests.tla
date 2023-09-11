---- MODULE StateStoreTests ----

EXTENDS StateStore, TLC, TLCExt, Integers, Sequences

ASSUME LET T == INSTANCE TLC IN T!PrintT("StateStoreTests")


\* Store and Load objects
TestObjects ==
    LET output == <<[a |-> 1, b |-> "a"], [a |-> 2, b |-> "b"], [a |-> 3, b |-> "c"]>>
    IN
       /\ StoreOpen("/tmp/state.db")
       /\ StoreValue(output)
       /\ LET input == LoadValue
          IN  Len(input) = 3

ASSUME(TestObjects)


====
