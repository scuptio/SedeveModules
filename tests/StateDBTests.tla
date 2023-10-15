---- MODULE StateDBTests ----

EXTENDS StateDB, FiniteSets, TLC, TLCExt, Integers, Sequences, SequencesExt

ASSUME LET T == INSTANCE TLC IN T!PrintT("StateDBTests")


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


TestState ==

       /\ DBOpen("/tmp/state.db")
       /\ LET 
	   			node_id == {"n1", "n2"}
	   			value == {"v1", "v2"}
				entry == [
					term : {1},
					index: {1},
					value : value
				]
				log == {
					[
			       		log |-> [
			       			i \in node_id |-> x
			       		] 
					] : x \in  SetToAllKPermutations(entry)
				}
	   IN /\ \A l \in log : CreateState(l)
	      /\ LET s == QueryAllStates
	         IN /\ s = log
				
ASSUME(TestState)

====
