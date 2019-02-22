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

package com.korea.kivescript.lang.jsr223;

import com.korea.kivescript.RiveScript;
import com.korea.kivescript.macro.ObjectHandler;
import com.korea.kivescript.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import static java.util.Objects.requireNonNull;

/**
 * Provides JSR-223 Scripting support for object macros in RiveScript.
 * 스크립트 매크로를 위한 스크립팅핸들러클래스.
 * @author Marcel Overdijk
 * @see ObjectHandler
 * @see com.korea.kivescript.lang.groovy.GroovyHandler
 * @see com.korea.kivescript.lang.javascript.JavaScriptHandler
 * @see com.korea.kivescript.lang.ruby.RubyHandler
 */
public class Jsr223ScriptingHandler implements ObjectHandler {

	private static Logger logger = LoggerFactory.getLogger(Jsr223ScriptingHandler.class);

	protected String engineName;
	protected ScriptEngine scriptEngine;
	protected String functionNamePrefix;
	protected String functionCodeFormat;
	protected String codeDelimiter;

	/**
	 * Constructs a JSR-223 Scripting {@link ObjectHandler}.
	 *
	 * @param engineName         the short name of the desired {@link ScriptEngine},엔진이름(언어)
	 * @param functionCodeFormat the function code format,함수코드 포맷
	 */
	public Jsr223ScriptingHandler(String engineName, String functionCodeFormat) {
		this(engineName, functionCodeFormat, "object_");
	}

	/**
	 * Constructs a JSR-223 Scripting {@link ObjectHandler}.
	 *
	 * @param engineName         the short name of the desired {@link ScriptEngine}
	 * @param functionCodeFormat the function code format
	 * @param functionNamePrefix the function code prefix
	 */
	public Jsr223ScriptingHandler(String engineName, String functionCodeFormat, String functionNamePrefix) {
		this(engineName, functionCodeFormat, functionNamePrefix, "\n");
	}

	/**
	 * Constructs a JSR-223 Scripting {@link ObjectHandler}.
	 *
	 * @param engineName         the short name of the desired {@link ScriptEngine}
	 * @param functionCodeFormat the function code format
	 * @param functionNamePrefix the function code prefix
	 * @param codeDelimiter      the code delimiter
	 */
	public Jsr223ScriptingHandler(String engineName, String functionCodeFormat, String functionNamePrefix, String codeDelimiter) {
		this.engineName = requireNonNull(engineName, "'engineName' must not be null");
		this.functionNamePrefix = requireNonNull(functionNamePrefix, "'functionNamePrefix' must not be null");
		this.functionCodeFormat = requireNonNull(functionCodeFormat, "'functionCodeFormat' must not be null");
		this.codeDelimiter = requireNonNull(codeDelimiter, "'codeDelimiter' must not be null");
		this.scriptEngine = new ScriptEngineManager().getEngineByName(engineName);
		if (this.scriptEngine == null) {
			this.scriptEngine = new ScriptEngineManager(null).getEngineByName(engineName);
			if (this.scriptEngine == null) {
				logger.error("No script engine found for '{}'", engineName);
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void load(RiveScript rs, String name, String[] code) {
		if (scriptEngine == null) {
			logger.warn("Cannot load macro '{}' as no script engine was found for '{}'", name, engineName);
		} else {
			long startTime = System.currentTimeMillis();

			/**
			 * 유일한 매크로(함수)이름으로 함수코드를 문자열로 받환받는다.
			 */
			String function = resolveFunctionCode(name, code);

			/**
			 * 스크립트 엔진에 문자열 함수를 eval해준다.
			 */
			try {
				scriptEngine.eval(function);
				if (logger.isDebugEnabled()) {
					long elapsedTime = System.currentTimeMillis() - startTime;
					logger.debug("Loaded code for macro '{}' in {} ms", name, elapsedTime);
				}
			} catch (ScriptException e) {
				logger.error("Error loading code for marco '{}'", name, e);
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String call(RiveScript rs, String name, String[] fields) {
		String result = null;
		if (scriptEngine == null) {
			logger.warn("Cannot call macro '{}' as no script engine was found for '{}'", name, engineName);
		} else {
			long startTime = System.currentTimeMillis();

			// Invoke the function in the script engine.
			try {
				Invocable invocable = (Invocable) scriptEngine;
				Object value = invocable.invokeFunction(resolveFunctionName(name), rs, fields);
				if (value != null) {
					result = value.toString();
				}
				if (logger.isDebugEnabled()) {
					long elapsedTime = System.currentTimeMillis() - startTime;
					if (result == null) {
						logger.debug("Returned null from macro '{}' in {} ms", name, elapsedTime);
					} else {
						logger.debug("Returned '{}' from macro '{}' in {} ms", result, name, elapsedTime);
					}
				}
			} catch (ScriptException e) {
				logger.error("Error invoking function for macro '{}'", name, e);
			} catch (NoSuchMethodException e) {
				logger.error("Error invoking function for macro '{}'", name, e);
			}
		}
		return result;
	}

	/**
	 * Resolves the function name.
	 *
	 * @param name the name of the object
	 * @return the function name
	 */
	protected String resolveFunctionName(String name) {
		return functionNamePrefix + name;
	}

	/**
	 * Resolves the code to create the function.
	 * functionCodeFormat을 이용하여 스크립트 함수를 정의한 문자열을 리턴
	 * @param name the name of the object
	 * @param code the source code inside the object
	 * @return the source as string
	 */
	protected String resolveFunctionCode(String name, String[] code) {
		return String.format(functionCodeFormat, resolveFunctionName(name), StringUtils.join(code, codeDelimiter));
	}
}
