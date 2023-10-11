package tlc2.overrides;
/*******************************************************************************
 * Copyright (c) 2019 Microsoft Research. All rights reserved.
 *
 * The MIT License (MIT)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to do
 * so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 * Contributors:
 *   Markus Alexander Kuppe - initial API and implementation
 ******************************************************************************/

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import tlc2.value.IValue;
import tlc2.value.impl.BoolValue;
import tlc2.value.impl.FcnLambdaValue;
import tlc2.value.impl.FcnRcdValue;
import tlc2.value.impl.IntValue;
import tlc2.value.impl.IntervalValue;
import tlc2.value.impl.ModelValue;
import tlc2.value.impl.RecordValue;
import tlc2.value.impl.SetEnumValue;
import tlc2.value.impl.SetOfFcnsValue;
import tlc2.value.impl.SetOfRcdsValue;
import tlc2.value.impl.SetOfTuplesValue;
import tlc2.value.impl.StringValue;
import tlc2.value.impl.SubsetValue;
import tlc2.value.impl.TupleValue;
import tlc2.value.impl.Value;
import tlc2.value.ValueConstants;
import util.UniqueString;
import tlc2.util.FP64;
import tlc2.overrides.*;


 class DB {
	private String path = null;
	private  Connection connection = null;
	private  PreparedStatement prepared_insert_stmt = null;
	private  ConcurrentHashMap<Long, Long> map = null;
	private  PreparedStatement prepared_query_stmt = null;
	DB () {}

	public void open(String path) {
		try {
			if (this.path == path) {
				return;
			}


			this.path = path;
			this.map = new ConcurrentHashMap<>();
			this.connection = DriverManager.getConnection(String.format("jdbc:sqlite:%s", path));
			Statement statement = this.connection.createStatement();
			statement.executeUpdate("drop table if exists state");
			statement.executeUpdate("create table state (finger_print long primary key, json_string string)");
			String insert_stmt = "insert into state values(?, ?);";
			String query_stmt = "select finger_print, json_string from state;";
			this.prepared_insert_stmt = this.connection.prepareStatement(insert_stmt);
			this.prepared_query_stmt = this.connection.prepareStatement(query_stmt);

		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public void close() {
		try {
			connection.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	static DB New() {
		String env_name = "STATE_STORE";
		String path = "state_store.db";
		Map<String, String> env = System.getenv();
		if (env.containsKey(env_name)) {
			path = env.get(env_name);
		}

		DB db = new DB();
		db.open(path);
		return db;
	}

	public boolean contains(long finger_print) {
		return this.map.containsKey(finger_print);
	}

	public Vector<String> get()  {
		Vector<String> vec = new Vector<>();
		try {
			ResultSet rs = this.prepared_query_stmt.executeQuery();
			while (true) {
				boolean ok = rs.next();
				if (ok) {
					String json_string = rs.getString(2);
					vec.add(json_string);
				} else {
					break;
				}
			}
		} catch (SQLException e) {
			System.err.println(e.getMessage());
			e.printStackTrace();
		}
		return vec;
	}

	public void put(long finger_print, String json_string) {
		try {
			this.prepared_insert_stmt.clearParameters();
			this.prepared_insert_stmt.setLong(1, finger_print);
			this.prepared_insert_stmt.setString(2, json_string);
			this.prepared_insert_stmt.execute();
		} catch (SQLException e) {
			System.err.println(e.getMessage());
			e.printStackTrace();
		}
		map.put(finger_print,  finger_print);
	}
}
/**
 * Module overrides for operators to read and write JSON.
 */
public class StateDB {
	
	static DB db;
	static {
		db = DB.New();
	}

	/**
	 * Open storage.
	 *
	 * @param path  the db file path to which to write
	 */
	@TLAPlusOperator(identifier = "DBOpen", module = "StateDB", warn = false)
	public synchronized static BoolValue storeOpen(final StringValue path) throws IOException {
		StateDB.db.open(path.val.toString());
		return BoolValue.ValTrue;
	}

	/**
	 * Open storage.
	 *
	 * @param path  the db file path to which to write
	 */
	@TLAPlusOperator(identifier = "DBClose", module = "StateDB", warn = false)
	public synchronized static BoolValue storeClose() throws IOException {
		StateDB.db.close();
		return BoolValue.ValTrue;
	}

	/**
	 * Deserializes a tuple of *plain* JSON values from the given path.
	 *
	 * @param path the JSON file path
	 * @return a tuple of JSON values
	 */
	@TLAPlusOperator(identifier = "QueryAll", module = "StateDB", warn = false)
	public synchronized static Value queryAllValues() throws IOException {
		Vector<String> rows = StateDB.db.get();
		
		List<Value> values = new ArrayList<>();

		for (int i = 0; i < rows.size(); i++) {
			String json_string = rows.get(i);
			JsonElement json_element = JsonParser.parseString(json_string);
			Value value = getTypedValue(json_element);
			values.add(value);
		}
		SetEnumValue set = new SetEnumValue(values.toArray(new Value[values.size()]), false);
		set.normalize();
		return set ;
	}

	/**
	 * Serializes a tuple of values to newline delimited JSON.
	 *
	 * @param path  the file to which to write the values
	 * @param value the values to write
	 * @return a boolean value indicating whether the serialization was successful
	 */
	@TLAPlusOperator(identifier = "Put", module = "StateDB", warn = false)
	public synchronized static BoolValue putValue(final Value v) throws IOException {
		long finger_print = v.fingerPrint(FP64.New());
		if (!StateDB.db.contains(finger_print)) {
			String json_string = getNode(v).toString();
			StateDB.db.put(finger_print, json_string);
		}

		return BoolValue.ValTrue;
	}

	/**
	 * Recursively converts the given value to a {@code JsonElement}.
	 *
	 * @param value the value to convert
	 * @return the converted {@code JsonElement}
	 */
	private static JsonElement getNode(IValue value) throws IOException {
		if (value instanceof RecordValue) {
			return getObjectNode((RecordValue) value);
		} else if (value instanceof TupleValue) {
			return getArrayNode((TupleValue) value);
		} else if (value instanceof StringValue) {
			return getJsonPrimitive(value);
		} else if (value instanceof ModelValue) {
			return getJsonPrimitive(value);
		} else if (value instanceof IntValue) {
			return getJsonPrimitive(value);
		} else if (value instanceof BoolValue) {
			return getJsonPrimitive(value);
		} else if (value instanceof FcnRcdValue) {
			return getObjectNode((FcnRcdValue) value);
		} else if (value instanceof FcnLambdaValue) {
			return getObjectNode((FcnRcdValue) ((FcnLambdaValue) value).toFcnRcd());
		} else if (value instanceof SetEnumValue) {
			return getArrayNode((SetEnumValue) value);
		} else if (value instanceof SetOfRcdsValue) {
			return getArrayNode((SetEnumValue) ((SetOfRcdsValue) value).toSetEnum());
		} else if (value instanceof SetOfTuplesValue) {
			return getArrayNode((SetEnumValue) ((SetOfTuplesValue) value).toSetEnum());
		} else if (value instanceof SetOfFcnsValue) {
			return getArrayNode((SetEnumValue) ((SetOfFcnsValue) value).toSetEnum());
		} else if (value instanceof SubsetValue) {
			return getArrayNode((SetEnumValue) ((SubsetValue) value).toSetEnum());
		} else if (value instanceof IntervalValue) {
			return getArrayNode((SetEnumValue) ((IntervalValue) value).toSetEnum());
		} else {
			throw new IOException("Cannot convert value: unsupported value type " + value.getClass().getName());
		}
	}

	private static JsonElement getJsonPrimitive(IValue value) throws IOException {
		JsonPrimitive jsonPrimitive = null;
		byte kind = 0;
		if (value instanceof StringValue) {
			StringValue v = ((StringValue) value);
			jsonPrimitive =  new JsonPrimitive(v.val.toString());
			kind = v.getKind();
		} else if (value instanceof ModelValue) {
			ModelValue v = (ModelValue) value;
			jsonPrimitive = new JsonPrimitive(v.val.toString());
			kind = v.getKind();
		} else if (value instanceof IntValue) {
			IntValue v = (IntValue) value;
			jsonPrimitive = new JsonPrimitive(v.val);
			kind = v.getKind();
		} else if (value instanceof BoolValue) {
			BoolValue v = (BoolValue) value;
			jsonPrimitive = new JsonPrimitive(v.val);
			kind = v.getKind();
		} else {
			throw new IOException("Cannot convert value: unsupported value type " + value.getClass().getName());
		}
		return jsonElementSetTypeId(jsonPrimitive, kind);
	}

	/**
	 * Returns a boolean indicating whether the given value is a valid sequence.
	 *
	 * @param value the value to check
	 * @return indicates whether the value is a valid sequence
	 */
	private static boolean isValidSequence(FcnRcdValue value) {
		final Value[] domain = value.getDomainAsValues();
		for (Value d : domain) {
			if (!(d instanceof IntValue)) {
				return false;
			}
		}
		value.normalize();
		for (int i = 0; i < domain.length; i++) {
			if (((IntValue) domain[i]).val != (i + 1)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Recursively converts the given value to an {@code JsonObject}.
	 *
	 * @param value the value to convert
	 * @return the converted {@code JsonElement}
	 */
	private static JsonElement getObjectNode(IValue value) throws IOException {
		if (value instanceof RecordValue) {
			return getObjectNode((RecordValue) value);
		} else if (value instanceof TupleValue) {
			return getObjectNode((TupleValue) value);
		} else if (value instanceof FcnRcdValue) {
			return getObjectNode((FcnRcdValue) value);
		} else if (value instanceof FcnLambdaValue) {
			return getObjectNode((FcnRcdValue) ((FcnLambdaValue) value).toFcnRcd());
		} else {
			throw new IOException("Cannot convert value: unsupported value type " + value.getClass().getName());
		}
	}

	/**
	 * Converts the given record value to a {@code JsonObject}, recursively converting values.
	 *
	 * @param value the value to convert
	 * @return the converted {@code JsonElement}
	 */
	private static JsonElement getObjectNode(FcnRcdValue value) throws IOException {
		if (isValidSequence(value)) {
			return getArrayNode(value);
		}

		final Value[] domain = value.getDomainAsValues();
		JsonObject jsonObject = new JsonObject();
		for (int i = 0; i < domain.length; i++) {
			Value domainValue = domain[i];
			if (domainValue instanceof StringValue) {
				jsonObject.add(((StringValue) domainValue).val.toString(), getNode(value.values[i]));
			} else {
				jsonObject.add(domainValue.toString(), getNode(value.values[i]));
			}
		}
		return jsonElementSetTypeId(jsonObject, value.getKind());
	}

	/**
	 * Converts the given record value to an {@code JsonObject}.
	 *
	 * @param value the value to convert
	 * @return the converted {@code JsonElement}
	 */
	private static JsonElement getObjectNode(RecordValue value) throws IOException {
		JsonObject jsonObject = new JsonObject();
		for (int i = 0; i < value.names.length; i++) {
			jsonObject.add(value.names[i].toString(), getNode(value.values[i]));
		}

		return jsonElementSetTypeId(jsonObject, value.getKind());
	}

	/**
	 * Converts the given tuple value to an {@code JsonObject}.
	 *
	 * @param value the value to convert
	 * @return the converted {@code JsonElement}
	 */
	private static JsonElement getObjectNode(TupleValue value) throws IOException {
		JsonObject jsonObject = new JsonObject();
		for (int i = 0; i < value.elems.length; i++) {
			jsonObject.add(String.valueOf(i), getNode(value.elems[i]));
		}
		return jsonElementSetTypeId(jsonObject,value.getKind());
	}

	/**
	 * Recursively converts the given value to an {@code JsonArray}.
	 *
	 * @param value the value to convert
	 * @return the converted {@code JsonElement}
	 */
	private static JsonElement getArrayNode(IValue value) throws IOException {
		if (value instanceof TupleValue) {
			return getArrayNode((TupleValue) value);
		} else if (value instanceof FcnRcdValue) {
			return getArrayNode((FcnRcdValue) value);
		} else if (value instanceof FcnLambdaValue) {
			return getArrayNode((FcnRcdValue) ((FcnLambdaValue) value).toFcnRcd());
		} else if (value instanceof SetEnumValue) {
			return getArrayNode((SetEnumValue) value);
		} else if (value instanceof SetOfRcdsValue) {
			return getArrayNode((SetEnumValue) ((SetOfRcdsValue) value).toSetEnum());
		} else if (value instanceof SetOfTuplesValue) {
			return getArrayNode((SetEnumValue) ((SetOfTuplesValue) value).toSetEnum());
		} else if (value instanceof SetOfFcnsValue) {
			return getArrayNode((SetEnumValue) ((SetOfFcnsValue) value).toSetEnum());
		} else if (value instanceof SubsetValue) {
			return getArrayNode((SetEnumValue) ((SubsetValue) value).toSetEnum());
		} else if (value instanceof IntervalValue) {
			return getArrayNode((SetEnumValue) ((IntervalValue) value).toSetEnum());
		} else {
			throw new IOException("Cannot convert value: unsupported value type " + value.getClass().getName());
		}
	}

	/**
	 * Converts the given tuple value to an {@code JsonArray}.
	 *
	 * @param value the value to convert
	 * @return the converted {@code JsonElement}
	 */
	private static JsonElement getArrayNode(TupleValue value) throws IOException {
		JsonArray jsonArray = new JsonArray(value.elems.length);
		for (int i = 0; i < value.elems.length; i++) {
			jsonArray.add(getNode(value.elems[i]));
		}
		return jsonElementSetTypeId(jsonArray, value.getKind());
	}

	/**
	 * Converts the given record value to an {@code JsonArray}.
	 *
	 * @param value the value to convert
	 * @return the converted {@code JsonElement}
	 */
	private static JsonElement getArrayNode(FcnRcdValue value) throws IOException {
		if (!isValidSequence(value)) {
			return getObjectNode(value);
		}

		value.normalize();
		JsonArray jsonArray = new JsonArray(value.values.length);
		for (int i = 0; i < value.values.length; i++) {
			jsonArray.add(getNode(value.values[i]));
		}

		return jsonElementSetTypeId(jsonArray, value.getKind());
	}

	/**
	 * Converts the given tuple value to an {@code JsonArray}.
	 *
	 * @param value the value to convert
	 * @return the converted {@code JsonElement}
	 */
	private static JsonElement getArrayNode(SetEnumValue value) throws IOException {
		value.normalize();
		Value[] values = value.elems.toArray();
		JsonArray jsonArray = new JsonArray(values.length);
		for (int i = 0; i < values.length; i++) {
			jsonArray.add(getNode(values[i]));
		}
		return jsonElementSetTypeId(jsonArray, value.getKind());
	}


	private static Value getTypedValue(JsonElement node) throws IOException {
		if (!node.isJsonObject()) {
			throw new IOException("Cannot convert value: unsupported JSON value 467 " + node.toString());
		}
		JsonObject object = node.getAsJsonObject();
		JsonElement je_kind = object.get("kind");
		JsonElement je_object = object.get("object");
		int kind = je_kind.getAsInt();

		switch (kind) {
			case ValueConstants.RECORDVALUE:
				return getRecordValue(je_object);
			case ValueConstants.TUPLEVALUE:
				return getTupleValue(je_object);
			case ValueConstants.STRINGVALUE:
				return new StringValue(je_object.getAsString());
			case ValueConstants.MODELVALUE:
				return ModelValue.make(je_object.getAsString());
			case ValueConstants.INTVALUE:
				return IntValue.gen(je_object.getAsInt());
			case ValueConstants.BOOLVALUE:
				return new BoolValue(je_object.getAsBoolean());
			case ValueConstants.FCNRCDVALUE:
				return getFcnRcdValue(je_object);
			case ValueConstants.SETENUMVALUE:
				return getSetEnumValue(je_object);
			default:
				throw new IOException("Unknown value kind :" + kind);

		}
	}
	/**
	 * Recursively converts the given {@code JsonElement} to a TLC value.
	 *
	 * @param node the {@code JsonElement} to convert
	 * @return the converted value
	 */
	private static Value getValue(JsonElement node) throws IOException {
		if (node.isJsonArray()) {
			return getTupleValue(node);
		}
		else if (node.isJsonObject()) {
			return getRecordValue(node);
		}
		else if (node.isJsonPrimitive()) {
			JsonPrimitive primitive = node.getAsJsonPrimitive();
			if (primitive.isNumber()) {
				return IntValue.gen(primitive.getAsInt());
			}
			else if (primitive.isBoolean()) {
				return new BoolValue(primitive.getAsBoolean());
			}
			else if (primitive.isString()) {
				return new StringValue(primitive.getAsString());
			}
		}
		else if (node.isJsonNull()) {
			return null;
		}
		throw new IOException("Cannot convert value: unsupported JSON value " + node.toString());
	}

	/**
	 * Converts the given {@code JsonElement} to a tuple.
	 *
	 * @param node the {@code JsonElement} to convert
	 * @return the tuple value
	 */
	private static TupleValue getTupleValue(JsonElement node) throws IOException {
		List<Value> values = new ArrayList<>();
		JsonArray jsonArray = node.getAsJsonArray();
		for (int i = 0; i < jsonArray.size(); i++) {
			values.add(getTypedValue(jsonArray.get(i)));
		}
		return new TupleValue(values.toArray(new Value[values.size()]));
	}


	/**
	 * Converts the given {@code JsonElement} to a FcnRcd.
	 *
	 * @param node the {@code JsonElement} to convert
	 * @return the FcnRcd value
	 */
	private static FcnRcdValue getFcnRcdValue(JsonElement node) throws IOException {
		List<Value> keys = new ArrayList<>();
		List<Value> values = new ArrayList<>();
		Iterator<Map.Entry<String, JsonElement>> iterator = node.getAsJsonObject().entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<String, JsonElement> entry = iterator.next();
			keys.add(new StringValue(entry.getKey()));
			values.add(getTypedValue(entry.getValue()));
		}
		return new FcnRcdValue(keys.toArray(new Value[keys.size()]), values.toArray(new Value[values.size()]),
				true);
	}

	/**
	 * Converts the given {@code JsonElement} to a SetEnum.
	 *
	 * @param node the {@code JsonElement} to convert
	 * @return the SetEnum value
	 */
	private static SetEnumValue getSetEnumValue(JsonElement node) throws IOException {
		List<Value> values = new ArrayList<>();
		JsonArray jsonArray = node.getAsJsonArray();
		for (int i = 0; i < jsonArray.size(); i++) {
			values.add(getTypedValue(jsonArray.get(i)));
		}
		return new SetEnumValue(values.toArray(new Value[values.size()]), true);
	}


	/**
	 * Converts the given {@code JsonElement} to a record.
	 *
	 * @param node the {@code JsonElement} to convert
	 * @return the record value
	 */
	private static RecordValue getRecordValue(JsonElement node) throws IOException {
		List<UniqueString> keys = new ArrayList<>();
		List<Value> values = new ArrayList<>();
		Iterator<Map.Entry<String, JsonElement>> iterator = node.getAsJsonObject().entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<String, JsonElement> entry = iterator.next();
			keys.add(UniqueString.uniqueStringOf(entry.getKey()));
			values.add(getTypedValue(entry.getValue()));
		}
		return new RecordValue(keys.toArray(new UniqueString[keys.size()]), values.toArray(new Value[values.size()]),
				false);
	}

	private static JsonElement jsonElementSetTypeId(JsonElement value, short id) throws IOException {
		JsonObject jsonObject = new JsonObject();
		jsonObject.add("kind", new JsonPrimitive(id));
		jsonObject.add("object", value);
		return jsonObject;
	}
}
