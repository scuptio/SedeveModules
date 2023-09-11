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
import java.util.concurrent.ConcurrentHashMap;
import java.util.Optional;

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
import util.UniqueString;
import tlc2.util.FP64;
import tlc2.overrides.*;


 class DB {
	private String path = null;
	private  Connection connection = null;
	private  PreparedStatement prepared_insert_stmt = null;
	private  ConcurrentHashMap<Long, String> map = null;
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
			statement.executeUpdate("create table state (finger_print long, json_string string)");
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

	public Optional<RowResult> get()  {
		try {
			ResultSet rs = this.prepared_query_stmt.executeQuery();
			boolean ok = rs.next();
			if (ok) {
				long finger_print = rs.getLong(1);
				String json_string = rs.getString(2);
				RowResult row = new RowResult(finger_print, json_string);
				return Optional.of(row);
			} else {
				System.err.println("fetch no rows");
			}
		} catch (SQLException e) {
			System.err.println(e.getMessage());
			e.printStackTrace();
		}

		return Optional.empty();
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
		map.put(finger_print,  json_string);
	}
}
/**
 * Module overrides for operators to read and write JSON.
 */
public class StateStore {
	
	static DB db;
	static {
		db = DB.New();
	}

	/**
	 * Open storage.
	 *
	 * @param path  the db file path to which to write
	 */
	@TLAPlusOperator(identifier = "StoreOpen", module = "StateStore", warn = false)
	public synchronized static BoolValue storeOpen(final StringValue path) throws IOException {
		StateStore.db.open(path.val.toString());
		return BoolValue.ValTrue;
	}

	/**
	 * Open storage.
	 *
	 * @param path  the db file path to which to write
	 */
	@TLAPlusOperator(identifier = "StoreClose", module = "StateStore", warn = false)
	public synchronized static BoolValue storeClose() throws IOException {
		StateStore.db.close();
		return BoolValue.ValTrue;
	}

	/**
	 * Deserializes a tuple of *plain* JSON values from the given path.
	 *
	 * @param path the JSON file path
	 * @return a tuple of JSON values
	 */
	@TLAPlusOperator(identifier = "LoadValue", module = "StateStore", warn = false)
	public synchronized static Value loadValue() throws IOException {
		Optional<RowResult> row = StateStore.db.get();
		RowResult record = row.get();
		JsonElement node = JsonParser.parseString(record.json_string);
		return getValue(node);
	}

	/**
	 * Serializes a tuple of values to newline delimited JSON.
	 *
	 * @param path  the file to which to write the values
	 * @param value the values to write
	 * @return a boolean value indicating whether the serialization was successful
	 */
	@TLAPlusOperator(identifier = "StoreValue", module = "StateStore", warn = false)
	public synchronized static BoolValue storeValue(final Value v) throws IOException {
		long finger_print = v.fingerPrint(FP64.New());
		if (!StateStore.db.contains(finger_print)) {
			String json_string = getNode(v).toString();
			StateStore.db.put(finger_print, json_string);
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
			return getObjectNode((RecordValue) value, ValueTypeID.RecordValue);
		} else if (value instanceof TupleValue) {
			return getArrayNode((TupleValue) value, ValueTypeID.TupleValue);
		} else if (value instanceof StringValue) {
			return getJsonPrimitive(value, ValueTypeID.StringValue);
		} else if (value instanceof ModelValue) {
			return getJsonPrimitive(value, ValueTypeID.ModelValue);
		} else if (value instanceof IntValue) {
			return getJsonPrimitive(value, ValueTypeID.IntValue);
		} else if (value instanceof BoolValue) {
			return getJsonPrimitive(value, ValueTypeID.BoolValue);
		} else if (value instanceof FcnRcdValue) {
			return getObjectNode((FcnRcdValue) value, ValueTypeID.FcnRcdValue);
		} else if (value instanceof FcnLambdaValue) {
			return getObjectNode((FcnRcdValue) ((FcnLambdaValue) value).toFcnRcd(), ValueTypeID.FcnLambdaValue);
		} else if (value instanceof SetEnumValue) {
			return getArrayNode((SetEnumValue) value, ValueTypeID.SetEnumValue);
		} else if (value instanceof SetOfRcdsValue) {
			return getArrayNode((SetEnumValue) ((SetOfRcdsValue) value).toSetEnum(), ValueTypeID.SetOfRcdsValue);
		} else if (value instanceof SetOfTuplesValue) {
			return getArrayNode((SetEnumValue) ((SetOfTuplesValue) value).toSetEnum(), ValueTypeID.SetOfTuplesValue);
		} else if (value instanceof SetOfFcnsValue) {
			return getArrayNode((SetEnumValue) ((SetOfFcnsValue) value).toSetEnum(), ValueTypeID.SetOfFcnsValue);
		} else if (value instanceof SubsetValue) {
			return getArrayNode((SetEnumValue) ((SubsetValue) value).toSetEnum(), ValueTypeID.SubsetValue);
		} else if (value instanceof IntervalValue) {
			return getArrayNode((SetEnumValue) ((IntervalValue) value).toSetEnum(), ValueTypeID.IntervalValue);
		} else {
			throw new IOException("Cannot convert value: unsupported value type " + value.getClass().getName());
		}
	}

	private static JsonElement getJsonPrimitive(IValue value, short id) throws IOException {
		JsonPrimitive jsonPrimitive = null;
		if (value instanceof StringValue) {
			jsonPrimitive =  new JsonPrimitive(((StringValue) value).val.toString());
		} else if (value instanceof ModelValue) {
			jsonPrimitive = new JsonPrimitive(((ModelValue) value).val.toString());
		} else if (value instanceof IntValue) {
			jsonPrimitive = new JsonPrimitive(((IntValue) value).val);
		} else if (value instanceof BoolValue) {
			jsonPrimitive = new JsonPrimitive(((BoolValue) value).val);
		} else {
			throw new IOException("Cannot convert value: unsupported value type " + value.getClass().getName());
		}
		return jsonElementSetTypeId(jsonPrimitive, id);
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
			return getObjectNode((RecordValue) value, ValueTypeID.RecordValue);
		} else if (value instanceof TupleValue) {
			return getObjectNode((TupleValue) value, ValueTypeID.TupleValue);
		} else if (value instanceof FcnRcdValue) {
			return getObjectNode((FcnRcdValue) value, ValueTypeID.FcnRcdValue);
		} else if (value instanceof FcnLambdaValue) {
			return getObjectNode((FcnRcdValue) ((FcnLambdaValue) value).toFcnRcd(), ValueTypeID.FcnRcdValue);
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
	private static JsonElement getObjectNode(FcnRcdValue value, short id) throws IOException {
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
		return jsonElementSetTypeId(jsonObject, id);
	}

	/**
	 * Converts the given record value to an {@code JsonObject}.
	 *
	 * @param value the value to convert
	 * @return the converted {@code JsonElement}
	 */
	private static JsonElement getObjectNode(RecordValue value, short id) throws IOException {
		JsonObject jsonObject = new JsonObject();
		for (int i = 0; i < value.names.length; i++) {
			jsonObject.add(value.names[i].toString(), getNode(value.values[i]));
		}

		return jsonElementSetTypeId(jsonObject, id);
	}

	/**
	 * Converts the given tuple value to an {@code JsonObject}.
	 *
	 * @param value the value to convert
	 * @return the converted {@code JsonElement}
	 */
	private static JsonElement getObjectNode(TupleValue value, short id) throws IOException {
		JsonObject jsonObject = new JsonObject();
		for (int i = 0; i < value.elems.length; i++) {
			jsonObject.add(String.valueOf(i), getNode(value.elems[i]));
		}
		return jsonElementSetTypeId(jsonObject, id);
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
			return getArrayNode((FcnRcdValue) value, ValueTypeId.FcnRcdValue);
		} else if (value instanceof FcnLambdaValue) {
			return getArrayNode((FcnRcdValue) ((FcnLambdaValue) value).toFcnRcd(), ValueTypeId.FcnLambdaValue);
		} else if (value instanceof SetEnumValue) {
			return getArrayNode((SetEnumValue) value, ValueTypeId.SetEnumValue);
		} else if (value instanceof SetOfRcdsValue) {
			return getArrayNode((SetEnumValue) ((SetOfRcdsValue) value).toSetEnum(), ValueTypeId.SetOfRcdsValue);
		} else if (value instanceof SetOfTuplesValue) {
			return getArrayNode((SetEnumValue) ((SetOfTuplesValue) value).toSetEnum(), ValueTypeId.SetOfTuplesValue);
		} else if (value instanceof SetOfFcnsValue) {
			return getArrayNode((SetEnumValue) ((SetOfFcnsValue) value).toSetEnum(), ValueTypeId.SetOfFcnsValue);
		} else if (value instanceof SubsetValue) {
			return getArrayNode((SetEnumValue) ((SubsetValue) value).toSetEnum(), ValueTypeId.SubsetValue);
		} else if (value instanceof IntervalValue) {
			return getArrayNode((SetEnumValue) ((IntervalValue) value).toSetEnum(), ValueTypeId.IntervalValue);
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
	private static JsonElement getArrayNode(TupleValue value, short id) throws IOException {
		JsonArray jsonArray = new JsonArray(value.elems.length);
		for (int i = 0; i < value.elems.length; i++) {
			jsonArray.add(getNode(value.elems[i]));
		}
		return jsonElementSetTypeId(jsonArray, id);
	}

	/**
	 * Converts the given record value to an {@code JsonArray}.
	 *
	 * @param value the value to convert
	 * @return the converted {@code JsonElement}
	 */
	private static JsonElement getArrayNode(FcnRcdValue value, short id) throws IOException {
		if (!isValidSequence(value)) {
			return getObjectNode(value);
		}

		value.normalize();
		JsonArray jsonArray = new JsonArray(value.values.length);
		for (int i = 0; i < value.values.length; i++) {
			jsonArray.add(getNode(value.values[i]));
		}
		return jsonElementSetTypeId(jsonArray, id);
	}

	/**
	 * Converts the given tuple value to an {@code JsonArray}.
	 *
	 * @param value the value to convert
	 * @return the converted {@code JsonElement}
	 */
	private static JsonElement getArrayNode(SetEnumValue value, short id) throws IOException {
		value.normalize();
		Value[] values = value.elems.toArray();
		JsonArray jsonArray = new JsonArray(values.length);
		for (int i = 0; i < values.length; i++) {
			jsonArray.add(getNode(values[i]));
		}
		return jsonElementSetTypeId(jsonArray, id);
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
			values.add(getValue(jsonArray.get(i)));
		}
		return new TupleValue(values.toArray(new Value[values.size()]));
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
			values.add(getValue(entry.getValue()));
		}
		return new RecordValue(keys.toArray(new UniqueString[keys.size()]), values.toArray(new Value[values.size()]),
				false);
	}

	/**
	 * @deprecated It will be removed when this Class is moved to `TLC`.
	 */
	@Deprecated
	final static void resolves() {
		// See TLCOverrides.java
	}


	private static JsonElement jsonElementSetTypeId(JsonElement value, short id) throws IOException {
		JsonObject jsonObject = new JsonObject();
		jsonObject.add("i", new JsonPrimitive(id));
		jsonObject.add("v", value);
		return jsonObject;
	}
}
