---- MODULE StateDBTests ----

EXTENDS StateDB, FiniteSets, TLC, TLCExt, Integers, Sequences, SequencesExt

ASSUME LET T == INSTANCE TLC IN T!PrintT("StateDBTests")





TestState ==
       /\ LET 	path == "/tmp/state"
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
	   	 IN /\ (\A l \in log : SaveValue(l, path))
	   /\ FlushAll
	   /\ LET s == QueryAllValues(path)
	         IN /\ PrintT(s)
	
ASSUME(TestState)

====
