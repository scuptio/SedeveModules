package tlc2.overrides;
/*******************************************************************************
 * Copyright (c) 2019 Microsoft Research. All rights reserved.
 * Copyright (c) 2024 Scuptio. All rights reserved.
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
 ******************************************************************************/

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
