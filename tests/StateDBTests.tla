---- MODULE StateDBTests ----

EXTENDS StateDB, FiniteSets, TLC, TLCExt, Integers, Sequences

ASSUME LET T == INSTANCE TLC IN T!PrintT("StateDBTests")


SetToSeqs(S) == 
  (**************************************************************************)
  (* Convert the set S to a set containing all sequences containing the     *)
  (* elements of S exactly once and no other elements.                      *)
  (* Example:                                                               *)
  (*    SetToSeqs({}), {<<>>}                                               *)
  (*    SetToSeqs({"t","l"}) = {<<"t","l">>, <<"l","t">>}                   *) 
  (**************************************************************************)
  LET D == 1..Cardinality(S)
  IN { f \in [D -> S] : \A i,j \in D : i # j => f[i] # f[j] }

SetToAllKPermutations(S) ==
  (**************************************************************************)
  (* Convert the set S to a set containing all k-permutations of elements   *)
  (* of S for k \in 0..Cardinality(S).                                      *)
  (* Example:                                                               *)
  (*    SetToAllKPermutations({}) = {<<>>}                                  *)
  (*    SetToAllKPermutations({"a"}) = {<<>>, <<"a">>}                      *)
  (*    SetToAllKPermutations({"a","b"}) =                                  *)
  (*                    {<<>>, <<"a">>, <<"b">>,<<"a","b">>, <<"b","a">>}   *)
  (**************************************************************************)
  UNION { SetToSeqs(s) : s \in SUBSET S  }

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
