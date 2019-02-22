/*
 * Copyright (c) 2016 the original author or authors.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.korea.kivescript.lang.ruby;

import com.korea.kivescript.lang.jsr223.Jsr223ScriptingHandler;
import com.korea.kivescript.macro.ObjectHandler;

/**
 * Provides Ruby programming language support for object macros in RiveScript.
 * <p>
 * Example:
 * <p>
 * <pre>
 * <code>
 * import com.korea.kivescript.Config;
 * import com.korea.kivescript.RiveScript;
 * import com.korea.kivescript.lang.ruby.RubyHandler;
 *
 * RiveScript bot = new RiveScript();
 * bot.setHandler("ruby", new RubyHandler(rs));
 *
 * // and go on as normal
 * </code>
 * </pre>
 * <p>
 * And in your RiveScript code, you can load and run Ruby objects:
 * <p>
 * <pre>
 * <code>
 * > object reverse ruby
 *     msg = args.join(' ')
 *     return msg.reverse!
 * < object
 *
 * > object setname ruby
 *     username = rs.currentUser()
 *     rs.setUservar(username, 'name', args[0])
 * < object
 *
 * + reverse *
 * - &lt;call&gt;reverse &lt;star&gt;&lt;/call&gt;
 *
 * + my name is *
 * - I will remember that.&lt;call&gt;setname "&lt;formal&gt;"&lt;/call&gt;
 *
 * + what is my name
 * - You are &lt;get name&gt;
 * </code>
 * </pre>
 *
 * @author Marcel Overdijk
 * @see ObjectHandler
 * @see Jsr223ScriptingHandler
 */
public class RubyHandler extends Jsr223ScriptingHandler {

	/**
	 * Constructs a Ruby {@link ObjectHandler}.
	 */
	public RubyHandler() {
		super("ruby", ""
				+ "def %s(rs, args)\n"
				+ "    args = args.to_a\n"
				+ "    %s\n"
				+ "end");
	}
}
