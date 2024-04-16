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

// modified from Json.java


import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Vector;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import tlc2.util.FP64;
import tlc2.value.IValue;
import tlc2.value.impl.BoolValue;
import tlc2.value.impl.FcnLambdaValue;
import tlc2.value.impl.FcnRcdValue;
import tlc2.value.impl.IntValue;
import tlc2.value.impl.IntervalValue;
import tlc2.value.impl.ModelValue;
import tlc2.value.impl.RecordValue;
import tlc2.value.impl.SetCupValue;
import tlc2.value.impl.SetEnumValue;
import tlc2.value.impl.SetOfFcnsValue;
import tlc2.value.impl.SetOfRcdsValue;
import tlc2.value.impl.SetOfTuplesValue;
import tlc2.value.impl.SetPredValue;
import tlc2.value.impl.StringValue;
import tlc2.value.impl.SubsetValue;
import tlc2.value.impl.TupleValue;
import tlc2.value.impl.Value;
import tlc2.value.ValueConstants;
import util.UniqueString;

class _Command {
	
}

class _ValueRecord extends _Command {

	public String path = null;
	public long fingerprint = 0;
	public String value = null;
	
	public _ValueRecord(String path, long fingerprint, final String value) {
		this.path = path;
		this.fingerprint = fingerprint;
		this.value = value;
	}
}

class _Control extends _Command {
	public Lock lock = null;
	public Condition cond = null;
	public boolean done;
	
	public _Control() {
		Lock lock = new ReentrantLock();
		Condition cond = lock.newCondition();
		this.lock = lock;
		this.cond = cond;
		this.done = false;
	}
	
	void await() {
		try {
			this.lock.lock();
			while (!this.done) {
				this.cond.await();
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		} finally {
			lock.unlock();
		}
	}
	
	public void done() {
		try {
			this.lock.lock();
			this.done = true;
			this.cond.signal();
		} finally {
			lock.unlock();
		}
	}
}



class DB extends Thread {
	class _DB {
		private String path = null;
		private Connection connection = null;
		private PreparedStatement prepared_insert_state_stmt = null;
		private PreparedStatement prepared_query_state_stmt = null;
		private int stmt_cnt = 0;

		public _DB(String path) {
			this.path = path;
		}

		public void open() {
			try {
				if (this.path == null) {
					return;
				}
				this.stmt_cnt = 0;

				this.connection = DriverManager.getConnection("jdbc:sqlite:" + new File(this.path));
				this.connection.setAutoCommit(false);
				Statement statement = this.connection.createStatement();
				statement.executeUpdate(
						"create table if not exists state (finger_print long primary key, json_string text not null);");
				String insert_stmt = "insert into state values(?, ?)"
						+ "on conflict(finger_print) do nothing;";
				this.prepared_insert_state_stmt = this.connection.prepareStatement(insert_stmt);

				String query_stmt = "select json_string from state order by finger_print;";
				this.prepared_query_state_stmt = this.connection.prepareStatement(query_stmt);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		public void close() {
			try {
				if (this.connection != null) {
					this.commit();
					this.connection.close();
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}

		public Vector<String> queryAll() {
			Vector<String> vec = new Vector<>();
			try {
				ResultSet rs = this.prepared_query_state_stmt.executeQuery();
				while (true) {
					boolean ok = rs.next();
					if (ok) {
						String json_string = rs.getString(1);
						assert(json_string != null);
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

		public void newValue(long finger_print, String json_string) {
			try {
				this.prepared_insert_state_stmt.clearParameters();
				this.prepared_insert_state_stmt.setLong(1, finger_print);
				this.prepared_insert_state_stmt.setString(2, json_string);
				this.prepared_insert_state_stmt.execute();
				this.stmt_cnt += 1;
				if (this.stmt_cnt >= MAX_STMT_PER_TRANS) {
					this.commit();
				}
			} catch (SQLException e) {
				System.err.println(e.getMessage());
				e.printStackTrace();
			}
		}
		
		public void commit() throws SQLException {
			if (this.connection != null && this.stmt_cnt != 0) {
				this.stmt_cnt = 0;
				this.connection.commit();
			}
		}
	}

	private final int MAX_CAPACITY = 10000;
	private final int MAX_STMT_PER_TRANS = 10000;
	
	private ConcurrentHashMap<String, _DB> map = new ConcurrentHashMap<String, _DB>();
	private LinkedBlockingDeque<_Command> deque = new LinkedBlockingDeque<_Command>(MAX_CAPACITY);

	DB() {
	}

	public void run() {
		this.thread_run();
	}
	
	
	public void addState(String path, long fingerprint, String value) {
		_Command c = new _ValueRecord(path, fingerprint, value);
		try {
			while (!this.deque.offer(c, 1, TimeUnit.SECONDS)) {
				this.flushAll();
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	public void flushAll() {
		_Control c = new _Control();
		dequeAdd(c);
		c.await();
	}
	
	void dequeAdd(_Command c) {
		try {
			while (!this.deque.offer(c, 1, TimeUnit.SECONDS)) {
				
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		} 
	}
	
	void closeAll() {
		for (Entry<String, _DB> e : this.map.entrySet()) {
			e.getValue().close();
		}
		this.map.clear();
	}
	
	void thread_run() {
		while (true) {
			try {
				Vector<_Command> vec = new Vector<_Command>(MAX_STMT_PER_TRANS);
				int i = 0;
				while (i < MAX_STMT_PER_TRANS) {
					_Command c = (_Command)this.deque.take();
					vec.add(c);
					if (c == null || c instanceof _Control) {
						break;
					}
					i ++;
				}
				for (int _i = 0; _i < vec.size(); _i++) {
					_Command c = vec.get(_i);
					if (c == null) {
						this.closeAll();
						return;
					} else if (c instanceof _Control) {
						this.closeAll();
						_Control ctrl = (_Control)c;
						ctrl.done();
					} else if (c instanceof _ValueRecord) {
						_ValueRecord v = (_ValueRecord)c;
						_DB db = this.openDB(v.path);
						assert(v.value != null);
						db.newValue(v.fingerprint, v.value);
					}
				}	
			} catch (InterruptedException e) {
				e.printStackTrace();
				return;
			}
		}
	}

	_DB openDB(String path) {
		_DB db = this.map.get(path);
		if (db == null) {
			_DB _db = new _DB(path);
			_db.open();
			this.map.put(path, _db);
			db = _db;
		}
		return db;
	}

	Vector<String> queryAllState(String path) {
		_DB db = this.openDB(path);
		return db.queryAll();
	}

	static DB New() {
		DB db = new DB();		
		return db;
	}
}

/**
 * Module overrides for operators to read and write JSON.
 */
public class StateDB {

	static DB db;
	static {
		db = DB.New();

		Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
		    public void run() {
		    	db.flushAll();
		    }
		}));

		db.start();
	}

	/**
	 * Flush all values
	 *
	 * @param path the db file path to which to write
	 */
	@TLAPlusOperator(identifier = "FlushAll", module = "StateDB", warn = false)
	public synchronized static Value flushAll() throws IOException {
		StateDB.db.flushAll();
		return BoolValue.ValTrue;
	}

	/**
	 * Query all states and return a set.
	 *
	 */
	@TLAPlusOperator(identifier = "QueryAllValues", module = "StateDB", warn = false)
	public synchronized static Value queryAllValues(StringValue path) throws IOException {
		Vector<String> rows = StateDB.db.queryAllState(path.val.toString());
		List<Value> values = new ArrayList<>();

		for (int i = 0; i < rows.size(); i++) {
			String json_string = rows.get(i);
			JsonElement json_element = JsonParser.parseString(json_string);
			Value value = getTypedValue(json_element);
			values.add(value);
		}
		SetEnumValue set = new SetEnumValue(values.toArray(new Value[values.size()]), false);
		set.normalize();
		return set;
	}

	/**
	 * Store a state to database.
	 *
	 * @param path state value
	 */
	@TLAPlusOperator(identifier = "SaveValue", module = "StateDB", warn = false)
	public synchronized static BoolValue newState(final Value state, final StringValue path) throws IOException {
		state.normalize();
		long fp = state.fingerPrint(FP64.New());
		String json = toJsonString(state);
		StateDB.db.addState(path.val.toString(), fp, json);
		return BoolValue.ValTrue;
	}

	public static String toJsonString(Value value) {
		try {
			return getNode(value).toString();
		} catch (IOException e) {
			e.printStackTrace();
			return "";
		}
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
		} else if (value instanceof SetPredValue){
			return getArrayNode((SetEnumValue)((SetPredValue)value).toSetEnum());
		}else if (value instanceof SetCupValue) {
			return getArrayNode((SetEnumValue)((SetCupValue)value).toSetEnum());
		}
		else{
			throw new IOException("Cannot convert value: unsupported value type " + value.getClass().getName());
		}
	}

	private static JsonElement getJsonPrimitive(IValue value) throws IOException {
		JsonPrimitive jsonPrimitive = null;
		byte kind = 0;
		if (value instanceof StringValue) {
			StringValue v = ((StringValue) value);
			jsonPrimitive = new JsonPrimitive(v.val.toString());
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
	 * Converts the given record value to a {@code JsonObject}, recursively
	 * converting values.
	 *
	 * @param value the value to convert
	 * @return the converted {@code JsonElement}
	 */
	private static JsonElement getObjectNode(FcnRcdValue value) throws IOException {
		Value tuple_value = value.toTuple();
		if (tuple_value != null && ((TupleValue) tuple_value).size() != 0) {
			return getArrayNode((TupleValue) tuple_value);
		}

		final Value[] domain = value.getDomainAsValues();
		JsonObject jsonObject = new JsonObject();
		for (int i = 0; i < domain.length; i++) {
			JsonObject object = new JsonObject();
			Value domainValue = domain[i];
			JsonElement domainElement = getNode(domainValue);
			object.add("domain", domainElement);

			JsonElement valueElement = getNode(value.values[i]);
			object.add("value", valueElement);

			if (domainValue instanceof StringValue) {
				jsonObject.add(((StringValue) domainValue).val.toString(), object);
			} else {
				jsonObject.add(domainValue.toString(), object);
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
	 * Converts the given tuple value to an {@code JsonArray}.
	 *
	 * @param value the value to convert
	 * @return the converted {@code JsonElement}
	 */
	private static JsonElement getArrayNode(TupleValue value) throws IOException {
		if (value.getKind() != ValueConstants.TUPLEVALUE) {
			throw new IOException("errory tuple value type");
		}
		JsonArray jsonArray = new JsonArray(value.elems.length);
		for (int i = 0; i < value.elems.length; i++) {
			jsonArray.add(getNode(value.elems[i]));
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
		if (node.isJsonArray()) {
			JsonArray array = node.getAsJsonArray();
			if (array.size() == 0) {
				return (FcnRcdValue) FcnRcdValue.EmptyFcn;
			}
		}

		Iterator<Map.Entry<String, JsonElement>> iterator = node.getAsJsonObject().entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<String, JsonElement> entry = iterator.next();
			JsonObject element = entry.getValue().getAsJsonObject();
			JsonElement domain = element.get("domain");
			JsonElement value = element.get("value");

			keys.add(getTypedValue(domain));
			values.add(getTypedValue(value));
		}
		return new FcnRcdValue(keys.toArray(new Value[keys.size()]), values.toArray(new Value[values.size()]), true);
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
