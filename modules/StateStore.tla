------------------------------ MODULE StateStore ------------------------------

LOCAL INSTANCE TLC
LOCAL INSTANCE Integers
  (*************************************************************************)
  (* Imports the definitions from the modules, but doesn't export them.    *)
  (*************************************************************************)

StoreOpen(path) ==
    TRUE

StoreClose ==
    TRUE


SerializeValue(path, val) ==
  (*************************************************************************)
  (* Serializes a values to  JSON file.                                    *)
  (*************************************************************************)
  TRUE

DeserializeValue(path) ==
  (*************************************************************************)
  (* Deserializes a JSON values from the given path.                       *)
  (*************************************************************************)
  CHOOSE val : TRUE


StoreValue(val) ==
  (*************************************************************************)
  (* StoreValue store a tuple of values to the given file as (plain) JSON. *)
  (* Records will be serialized as a JSON objects, and tuples as arrays.   *)
  (*************************************************************************)
  TRUE

LoadValue ==
  (*************************************************************************)
  (* LoadValue load a value from the given file. JSON objects will be    *)
  (* deserialized to records, and arrays will be deserialized to tuples.   *)
  (*************************************************************************)
  CHOOSE val : TRUE



============================================================================
