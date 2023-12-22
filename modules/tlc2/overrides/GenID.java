
package tlc2.overrides;


import tlc2.value.impl.BoolValue;
import tlc2.value.impl.IntValue;
import java.util.concurrent.atomic.AtomicInteger;
import tlc2.value.impl.Value;
import tlc2.overrides.*;

public final class GenID {

	static AtomicInteger value = new AtomicInteger(0);
	private GenID() {
		// no-instantiation!
	}
	
	/**
	 * Return a UUID with its string representation.
	 */
	@TLAPlusOperator(identifier = "SetID", module = "GenID", warn = false)
	public static Value setId(IntValue id) throws Exception {
		value.set(id.val);
		return BoolValue.ValTrue;
	}
	
	/**
	 * Return a UUID with its string representation.
	 */
	@TLAPlusOperator(identifier = "GetID", module = "GenID", warn = false)
	public static Value getId() throws Exception {
		int n = value.get();
		IntValue v =  IntValue.gen(n);
		return v;
	}
	
	/**
	 * Return a UUID with its string representation.
	 */
	@TLAPlusOperator(identifier = "NextID", module = "GenID", warn = false)
	public static Value nextId() throws Exception {
		int n = value.addAndGet(1);
		IntValue v =  IntValue.gen(n);
		return v;
	}

}
