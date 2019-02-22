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

package com.korea.kivescript;

import com.korea.kivescript.ast.ObjectMacro;
import com.korea.kivescript.ast.Root;
import com.korea.kivescript.ast.Topic;
import com.korea.kivescript.ast.Trigger;
import com.korea.kivescript.exception.DeepRecursionException;
import com.korea.kivescript.exception.NoDefaultTopicException;
import com.korea.kivescript.exception.RepliesNotSortedException;
import com.korea.kivescript.exception.ReplyNotFoundException;
import com.korea.kivescript.exception.ReplyNotMatchedException;
import com.korea.kivescript.macro.ObjectHandler;
import com.korea.kivescript.macro.Subroutine;
import com.korea.kivescript.parser.Parser;
import com.korea.kivescript.parser.ParserConfig;
import com.korea.kivescript.parser.ParserException;
import com.korea.kivescript.session.ConcurrentHashMapSessionManager;
import com.korea.kivescript.session.History;
import com.korea.kivescript.session.SessionManager;
import com.korea.kivescript.session.ThawAction;
import com.korea.kivescript.session.UserData;
import com.korea.kivescript.sorting.SortBuffer;
import com.korea.kivescript.sorting.SortTrack;
import com.korea.kivescript.sorting.SortedTriggerEntry;
import com.korea.kivescript.util.StringUtils;

import rnb.analyzer.nori.analyzer.NoriAnalyzer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.korea.kivescript.regexp.Regexp.RE_ANY_TAG;
import static com.korea.kivescript.regexp.Regexp.RE_ARRAY;
import static com.korea.kivescript.regexp.Regexp.RE_BOT_VAR;
import static com.korea.kivescript.regexp.Regexp.RE_CALL;
import static com.korea.kivescript.regexp.Regexp.RE_CONDITION;
import static com.korea.kivescript.regexp.Regexp.RE_INHERITS;
import static com.korea.kivescript.regexp.Regexp.RE_META;
import static com.korea.kivescript.regexp.Regexp.RE_OPTIONAL;
import static com.korea.kivescript.regexp.Regexp.RE_PLACEHOLDER;
import static com.korea.kivescript.regexp.Regexp.RE_RANDOM;
import static com.korea.kivescript.regexp.Regexp.RE_REDIRECT;
import static com.korea.kivescript.regexp.Regexp.RE_SET;
import static com.korea.kivescript.regexp.Regexp.RE_SYMBOLS;
import static com.korea.kivescript.regexp.Regexp.RE_TOPIC;
import static com.korea.kivescript.regexp.Regexp.RE_USER_VAR;
import static com.korea.kivescript.regexp.Regexp.RE_WEIGHT;
import static com.korea.kivescript.regexp.Regexp.RE_ZERO_WITH_STAR;
import static com.korea.kivescript.session.SessionManager.HISTORY_SIZE;
import static com.korea.kivescript.util.StringUtils.countWords;
import static com.korea.kivescript.util.StringUtils.quoteMetacharacters;
import static com.korea.kivescript.util.StringUtils.stripNasties;
import static java.util.Objects.requireNonNull;

/**
 * A RiveScript interpreter written in Java.
 * <p>
 * Usage:
 * <p>
 * <pre>
 * <code>
 * import com.korea.kivescript.Config;
 * import com.korea.kivescript.RiveScript;
 *
 * // Create a new bot with the default settings.
 * 기본 세팅으로 봇을 생성한다.
 * RiveScript bot = new RiveScript();
 *
 * // To enable UTF-8 mode, you'd have initialized the bot like:
 * UTF-8 mode를 활성화하려면, 밑에와 같이 초기화한다.
 * RiveScript bot = new RiveScript(Config.utf8());
 *
 * // Load a directory full of RiveScript documents (.rive files)
 * 모든 *.rive 문서를 읽으려면 밑에와 같이 한다.
 * bot.loadDirectory("./replies");
 *
 * // Load an individual file.
 * 하나의 .rive 파일을 읽으려면 밑에와 같이 한다.
 * bot.LoadFile("./testsuite.rive");
 *
 * // Sort the replies after loading them!
 * .rive파일을 로딩한 후에 응답을 정렬한다.
 * bot.sortReplies();
 *
 * // Get a reply.
 * 응답을 얻어온다.
 * String reply = bot.reply("user", "Hello bot!");
 * </code>
 * </pre>
 *
 * @author Noah Petherbridge
 * @author Marcel Overdijk
 */
public class RiveScript {

	public static final String DEEP_RECURSION_KEY = "deepRecursion";
	public static final String REPLIES_NOT_SORTED_KEY = "repliesNotSorted";
	public static final String DEFAULT_TOPIC_NOT_FOUND_KEY = "defaultTopicNotFound";
	public static final String REPLY_NOT_MATCHED_KEY = "replyNotMatched";
	public static final String REPLY_NOT_FOUND_KEY = "replyNotFound";
	public static final String OBJECT_NOT_FOUND_KEY = "objectNotFound";
	public static final String CANNOT_DIVIDE_BY_ZERO_KEY = "cannotDivideByZero";
	public static final String CANNOT_MATH_VARIABLE_KEY = "cannotMathVariable";
	public static final String CANNOT_MATH_VALUE_KEY = "cannotMathValue";

	public static final String DEFAULT_DEEP_RECURSION_MESSAGE = "ERR: Deep Recursion Detected";
	public static final String DEFAULT_REPLIES_NOT_SORTED_MESSAGE = "ERR: Replies Not Sorted";
	public static final String DEFAULT_DEFAULT_TOPIC_NOT_FOUND_MESSAGE = "ERR: No default topic 'random' was found";
	public static final String DEFAULT_REPLY_NOT_MATCHED_MESSAGE = "ERR: No Reply Matched";
	public static final String DEFAULT_REPLY_NOT_FOUND_MESSAGE = "ERR: No Reply Found";
	public static final String DEFAULT_OBJECT_NOT_FOUND_MESSAGE = "[ERR: Object Not Found]";
	public static final String DEFAULT_CANNOT_DIVIDE_BY_ZERO_MESSAGE = "[ERR: Can't Divide By Zero]";
	public static final String DEFAULT_CANNOT_MATH_VARIABLE_MESSAGE = "[ERR: Can't perform math operation on non-numeric variable]";
	public static final String DEFAULT_CANNOT_MATH_VALUE_MESSAGE = "[ERR: Can't perform math operation on non-numeric value]";

	public static final String UNDEFINED = "undefined";

	public static final String[] DEFAULT_FILE_EXTENSIONS = new String[] {".rive", ".rs"};

	private static final Random RANDOM = new Random();

	private static final String UNDEF_TAG = "<undef>";

	private static Logger logger = LoggerFactory.getLogger(RiveScript.class);

	private boolean throwExceptions;
	private boolean strict;
	private boolean utf8;
	private boolean forceCase;
	private ConcatMode concat;
	private MorphemeMode morpheme;
	private int depth;
	private Pattern unicodePunctuation;
	private Map<String, String> errorMessages;

	private Parser parser;

	private Map<String, String> global;                 // 'global' variables
	private Map<String, String> vars;                   // 'vars' bot variables
	private Map<String, String> sub;                    // 'sub' substitutions
	private Map<String, String> person;                 // 'person' substitutions
	private Map<String, List<String>> array;            // 'array' definitions
	private SessionManager sessions;                    // user variable session manager
	private Map<String, Map<String, Boolean>> includes; // included topics
	private Map<String, Map<String, Boolean>> inherits; // inherited topics
	private Map<String, String> objectLanguages;        // object macro languages
	private Map<String, ObjectHandler> handlers;        // object language handlers
	private Map<String, Subroutine> subroutines;        // Java object handlers
	private Map<String, Topic> topics;                  // main topic structure
	private SortBuffer sorted;                          // Sorted data from sortReplies()

	// State information.
	private ThreadLocal<String> currentUser = new ThreadLocal<>();
	
	
	private NoriAnalyzer analyzer = null;

	/*------------------*/
	/*-- Constructors --*/
	/*------------------*/

	/**
	 * Creates a new {@link RiveScript} interpreter.
	 * 디폴트 생성자는 기본 설정으로 챗봇이 생성된다.
	 */
	public RiveScript() {
		this(null);
	}

	/**
	 * Creates a new {@link RiveScript} interpreter with the given {@link Config}.
	 * 주어진 설정에 따라 인터프리터 인스턴스를 생성한다.
	 * null일 경우, 기본 생성자의 설정을 따른다. 
	 * @param config the config
	 */
	public RiveScript(Config config) {
		if (config == null) {
			config = Config.basic();
		}

		this.throwExceptions = config.isThrowExceptions();
		this.strict = config.isStrict();
		this.utf8 = config.isUtf8();
		this.forceCase = config.isForceCase();
		this.concat = config.getConcat();
		this.morpheme = config.getMorpheme();
		this.depth = config.getDepth();
		this.sessions = config.getSessionManager();

		String unicodePunctuation = config.getUnicodePunctuation();
		//config의 유니코드 구두점 패턴식이 존재하지 않으면 기본 구두점 패턴식을 리턴한다.
		if (unicodePunctuation == null) {
			unicodePunctuation = Config.DEFAULT_UNICODE_PUNCTUATION_PATTERN;
		}
		this.unicodePunctuation = Pattern.compile(unicodePunctuation);
		
		//에러메시지를 세팅한다.
		this.errorMessages = new HashMap<>();
		this.errorMessages.put(DEEP_RECURSION_KEY, DEFAULT_DEEP_RECURSION_MESSAGE);
		this.errorMessages.put(REPLIES_NOT_SORTED_KEY, DEFAULT_REPLIES_NOT_SORTED_MESSAGE);
		this.errorMessages.put(DEFAULT_TOPIC_NOT_FOUND_KEY, DEFAULT_DEFAULT_TOPIC_NOT_FOUND_MESSAGE);
		this.errorMessages.put(REPLY_NOT_MATCHED_KEY, DEFAULT_REPLY_NOT_MATCHED_MESSAGE);
		this.errorMessages.put(REPLY_NOT_FOUND_KEY, DEFAULT_REPLY_NOT_FOUND_MESSAGE);
		this.errorMessages.put(OBJECT_NOT_FOUND_KEY, DEFAULT_OBJECT_NOT_FOUND_MESSAGE);
		this.errorMessages.put(CANNOT_DIVIDE_BY_ZERO_KEY, DEFAULT_CANNOT_DIVIDE_BY_ZERO_MESSAGE);
		this.errorMessages.put(CANNOT_MATH_VARIABLE_KEY, DEFAULT_CANNOT_MATH_VARIABLE_MESSAGE);
		this.errorMessages.put(CANNOT_MATH_VALUE_KEY, DEFAULT_CANNOT_MATH_VALUE_MESSAGE);

		if (config.getErrorMessages() != null) {
			for (Map.Entry<String, String> entry : config.getErrorMessages().entrySet()) {
				this.errorMessages.put(entry.getKey(), entry.getValue());
			}
		}
		
		//기본 concat모드 설정
		if (this.concat == null) {
			this.concat = Config.DEFAULT_CONCAT;
			logger.debug("No concat config: using default {}", Config.DEFAULT_CONCAT);
		}
		
		//기본 재귀 탐색깊이 설정
		if (this.depth <= 0) {
			this.depth = Config.DEFAULT_DEPTH;
			logger.debug("No depth config: using default {}", Config.DEFAULT_DEPTH);
		}
		
		//기본 세션매니저 객체등록
		if (this.sessions == null) {
			this.sessions = new ConcurrentHashMapSessionManager();
			logger.debug("No SessionManager config: using default ConcurrentHashMapSessionManager");
		}

		// Initialize the parser.
		//파서 초기화
		this.parser = new Parser(ParserConfig.newBuilder()
				.strict(this.strict)
				.utf8(this.utf8)
				.forceCase(this.forceCase)
				.concat(this.concat)
				.morpheme(this.morpheme)
				.build());

		// Initialize all the data structures.
		//모든 데이터 구조를 초기화한다.
		this.global = new HashMap<>();
		this.vars = new HashMap<>();
		this.sub = new HashMap<>();
		this.person = new HashMap<>();
		this.array = new HashMap<>();
		this.includes = new HashMap<>();
		this.inherits = new HashMap<>();
		this.objectLanguages = new HashMap<>();
		this.handlers = new HashMap<>();
		this.subroutines = new HashMap<>();
		this.topics = new HashMap<>();
		this.sorted = new SortBuffer();
	}

	/**
	 * Returns the RiveScript library version, or {@code null} if it cannot be determined.
	 * RiveScript 라이브러리 버전을 반환하거나, 확인할 수없는 경우 "null"을 반환합니다.
	 * @return the version
	 * @see Package#getImplementationVersion()
	 */
	public static String getVersion() {
		Package pkg = RiveScript.class.getPackage();
		return (pkg != null ? pkg.getImplementationVersion() : null);
	}

	/*---------------------------*/
	/*-- Configuration Methods --*/
	/*---------------------------*/

	/**
	 * Returns whether exception throwing is enabled.
	 *
	 * @return whether exception throwing is enabled
	 */
	public boolean isThrowExceptions() {
		return throwExceptions;
	}

	/**
	 * Returns whether strict syntax checking is enabled.
	 *
	 * @return whether strict syntax checking is enabled
	 */
	public boolean isStrict() {
		return strict;
	}

	/**
	 * Returns whether UTF-8 mode is enabled for user messages and triggers.
	 *
	 * @return whether UTF-8 mode is enabled for user messages and triggers
	 */
	public boolean isUtf8() {
		return utf8;
	}

	/**
	 * Returns whether forcing triggers to lowercase is enabled.
	 *
	 * @return whether forcing triggers to lowercase is enabled
	 */
	public boolean isForceCase() {
		return forceCase;
	}

	/**
	 * Returns the concat mode.
	 *
	 * @return the concat mode
	 */
	public ConcatMode getConcat() {
		return concat;
	}

	/**
	 * Returns the recursion depth limit.
	 *
	 * @return the recursion depth limit
	 */
	public int getDepth() {
		return depth;
	}

	/**
	 * Returns the unicode punctuation pattern.
	 *
	 * @return the unicode punctuation pattern
	 */
	public String getUnicodePunctuation() {
		return unicodePunctuation != null ? unicodePunctuation.toString() : null;
	}

	/**
	 * Returns the error messages (unmodifiable).
	 *
	 * @return the error messages
	 */
	public Map<String, String> getErrorMessages() {
		return Collections.unmodifiableMap(errorMessages);
	}

	/**
	 * Sets a custom language handler for RiveScript object macros.
	 * RiveScript 객체 매크로를 위한 사용자 지정 프로그래밍언어 처리기를 설정
	 * @param name    the name of the programming language
	 * @param handler the implementation
	 */
	public void setHandler(String name, ObjectHandler handler) {
		handlers.put(name, handler);
	}

	/**
	 * Removes an object macro language handler.
	 * 하나의 객체 매크로 언어 처리기를 제거한다.
	 * @param name the name of the programming language
	 */
	public void removeHandler(String name) {
		// Purge all loaded objects for this handler.
		Iterator<Map.Entry<String, String>> it = objectLanguages.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<String, String> entry = it.next();
			if (entry.getValue().equals(name)) {
				it.remove();
			}
		}

		// And delete the handler itself.
		handlers.remove(name);
	}

	/**
	 * Returns the object macro language handlers (unmodifiable).
	 * 객체 매크로 프로그래밍언어 처리기를 담은 수정불가능한(Read only) 맵객체를 반환한다.
	 * @return the object macro language handlers
	 */
	public Map<String, ObjectHandler> getHandlers() {
		return Collections.unmodifiableMap(handlers);
	}

	/**
	 * Defines a Java object macro.
	 * 자바 객체 매크로를 정의.
	 * <p>
	 * Because Java is a compiled language, this method must be used to create an object macro written in Java.
	 * 자바는 컴파일 언어이기에, 이 메소드는 자바로 객체 매크로를 작성할 때 반드시 사용되어야한다.
	 * @param name       the name of the object macro for the `<call>` tag
	 * @param subroutine the subroutine
	 */
	public void setSubroutine(String name, Subroutine subroutine) {
		subroutines.put(name, subroutine);
	}

	/**
	 * Removes a Java object macro.
	 * 자바 객체 매크로를 제거한다.
	 * @param name the name of the object macro
	 */
	public void removeSubroutine(String name) {
		subroutines.remove(name);
	}

	/**
	 * Returns the Java object macros (unmodifiable).
	 * 자바 객체 매크로를 수정불가능한 맵으로 반환한다.
	 * @return the Java object macros
	 */
	public Map<String, Subroutine> getSubroutines() {
		return Collections.unmodifiableMap(subroutines);
	}

	/**
	 * Sets a global variable.
	 * 전역 변수를 설정한다.
	 * <p>
	 * This is equivalent to {@code ! global} in RiveScript. Set the value to {@code null} to delete a global.
	 * 이 메소드는 RiveScript에서 ! global과 동등하다. 전역변수를 제거하려면 값을 "null"로 설정한다.
	 * @param name  the variable name
	 * @param value the variable value or {@code null}
	 */
	public void setGlobal(String name, String value) {
		if (value == null) {
			global.remove(name);
		} else if (name.equals("depth")) {
			try {
				/**
				 * global varialble key 값이 "depth"인 경우, 재귀 임계치로 설정한다.
				 */
				depth = Integer.parseInt(value);
			} catch (NumberFormatException e) {
				logger.warn("Can't set global 'depth' to '{}': {}", value, e.getMessage());
			}
		} else {
			global.put(name, value);
		}
	}

	/**
	 * Returns a global variable.
	 * 전역 변수를 반환한다.
	 * <p>
	 * This is equivalent to {@code <env>} in RiveScript. Returns {@code null} if the variable isn't defined.
	 * 이 메소드는 RiveScript에서 {@code <env>}와 동등하다. 만약 변수가 정의되지 않았다면 "null"을 리턴한다.
	 * @param name the variable name
	 * @return the variable value or {@code null}
	 */
	public String getGlobal(String name) {
		if (name != null && name.equals("depth")) {
			return Integer.toString(depth);
		} else {
			return global.get(name);
		}
	}

	/**
	 * Sets a bot variable.
	 * 챗봇 변수를 세팅한다.
	 * <p>
	 * This is equivalent to {@code ! vars} in RiveScript. Set the value to {@code null} to delete a bot variable.
	 * 이 메소드는 RiveScript에서 {@code ! vars}과 동등하다. 챗봇 변수를 제거하려면 값을 "null"로 설정한다.
	 * @param name  the variable name
	 * @param value the variable value or {@code null}
	 */
	public void setVariable(String name, String value) {
		if (value == null) {
			vars.remove(name);
		} else {
			vars.put(name, value);
		}
	}

	/**
	 * Returns a bot variable.
	 * 챗봇 변수를 반환한다.
	 * <p>
	 * This is equivalent to {@code <bot>} in RiveScript. Returns {@code null} if the variable isn't defined.
	 * 이 메소드는 RiveScript에서 {@code <bot>}과 동등하다. 만약 정의되지 않은 변수라면 "null"을 반환한다.
	 * @param name the variable name
	 * @return the variable value or {@code null}
	 */
	public String getVariable(String name) {
		return vars.get(name);
	}

	/**
	 * Returns all bot variables.
	 * 설정된 챗봇 변수 모두를 Map으로 반환한다.
	 * @return the variable map
	 */
	public Map<String, String> getVariables() {
		return vars;
	}

	/**
	 * Sets a substitution pattern.
	 * 치환 패턴을 설정한다.
	 * <p>
	 * This is equivalent to {@code ! sub} in RiveScript. Set the value to {@code null} to delete a substitution.
	 * 이 메소드는 RiveScript에서 {@code ! sub}와 동등하다. 만약 치환자를 제거하고 싶다면 값을 "null"로 설정한다.
	 * @param name  the substitution name
	 * @param value the substitution pattern or {@code null}
	 */
	public void setSubstitution(String name, String value) {
		if (value == null) {
			sub.remove(name);
		} else {
			sub.put(name, value);
		}
	}

	/**
	 * Returns a substitution pattern.
	 * 치환 패턴을 반환한다.
	 * <p>
	 * Returns {@code null} if the substitution isn't defined.
	 * 만약 정의되지 않은 치환자라면 "null"을 반환한다.
	 * @param name the substitution name
	 * @return the substitution pattern or {@code null}
	 */
	public String getSubstitution(String name) {
		return sub.get(name);
	}

	/**
	 * Sets a person substitution pattern.
	 * <p>
	 * This is equivalent to {@code ! person} in RiveScript. Set the value to {@code null} to delete a person substitution.
	 *
	 * @param name  the person substitution name
	 * @param value the person substitution pattern or {@code null}
	 */
	public void setPerson(String name, String value) {
		if (value == null) {
			person.remove(name);
		} else {
			person.put(name, value);
		}
	}

	/**
	 * Returns a person substitution pattern.
	 * <p>
	 * This is equivalent to {@code <person>} in RiveScript. Returns {@code null} if the person substitution isn't defined.
	 *
	 * @param name the person substitution name
	 * @return the person substitution pattern or {@code null}
	 */
	public String getPerson(String name) {
		return person.get(name);
	}

	/**
	 * Checks whether deep recursion is detected.
	 * 설정된 깊이보다 더 재귀가 깊게 들어가는지 체크한다. 예외설정을 하였다면 예외를, 아니면 warn log를 출력.
	 * <p>
	 * Throws a {@link DeepRecursionException} in case exception throwing is enabled, otherwise logs a warning.
	 * 
	 * @param depth   the recursion depth counter
	 * @param message the message to log
	 * @return whether deep recursion is detected
	 * @throws DeepRecursionException in case deep recursion is detected and exception throwing is enabled
	 */
	private boolean checkDeepRecursion(int depth, String message) throws DeepRecursionException {
		if (depth > this.depth) {
			logger.warn(message);
			if (throwExceptions) {
				throw new DeepRecursionException(message);
			}
			return true;
		}
		return false;
	}

	/*---------------------*/
	/*-- Loading Methods --*/
	/*---------------------*/

	/**
	 * Loads a single RiveScript document from disk.
	 * 하나의 .rive 파일을 로드한다.
	 * @param file the RiveScript file
	 * @throws RiveScriptException in case of a loading error
	 * @throws ParserException     in case of a parsing error
	 */
	public void loadFile(File file) throws RiveScriptException, ParserException {
		requireNonNull(file, "'file' must not be null");
		logger.debug("Loading RiveScript file: {}", file);

		// Run some sanity checks on the file.
		// 파일 상태에 따른 예외 발생.
		if (!file.exists()) {
			throw new RiveScriptException("File '" + file + "' not found");
		} else if (!file.isFile()) {
			throw new RiveScriptException("File '" + file + "' is not a regular file");
		} else if (!file.canRead()) {
			throw new RiveScriptException("File '" + file + "' cannot be read");
		}

		List<String> code = new ArrayList<>();

		// Slurp the file's contents.
		// 파일의 내용을 버퍼리더로 모두 읽어오면서 리스트 객체에 넣는다.
		try (BufferedReader br = new BufferedReader(new FileReader(file))) {
			String line;
			while ((line = br.readLine()) != null) {
				code.add(line);
			}
		} catch (IOException e) {
			throw new RiveScriptException("Error reading file '" + file + "'", e);
		}
		
		parse(file.toString(), code.toArray(new String[0]));
	}

	/**
	 * Loads a single RiveScript document from disk.
	 * 하나의 .rive 파일을 로드한다.
	 * @param path the path to the RiveScript document
	 * @throws RiveScriptException in case of a loading error
	 * @throws ParserException     in case of a parsing error
	 */
	public void loadFile(String path) throws RiveScriptException, ParserException {
		requireNonNull(path, "'path' must not be null");
		loadFile(new File(path));
	}

	/**
	 * Loads multiple RiveScript documents from a directory on disk.
	 * .rive 파일들이 담겨있는 Directory를 전체 로드한다.
	 * extenstions 파라미터로 확장자를 더 추가 할 수 있다.
	 * @param directory the directory containing the RiveScript documents
	 * @throws RiveScriptException in case of a loading error
	 * @throws ParserException     in case of a parsing error
	 */
	public void loadDirectory(File directory, String... extensions) throws RiveScriptException, ParserException {
		requireNonNull(directory, "'directory' must not be null");
		logger.debug("Loading RiveScript files from directory: {}", directory);

		if (extensions.length == 0) {
			extensions = DEFAULT_FILE_EXTENSIONS;
		}
		final String[] exts = extensions;

		// Run some sanity checks on the directory.
		if (!directory.exists()) {
			throw new RiveScriptException("Directory '" + directory + "' not found");
		} else if (!directory.isDirectory()) {
			throw new RiveScriptException("Directory '" + directory + "' is not a directory");
		}

		// Search for the files.
		File[] files = directory.listFiles(new FilenameFilter() {

			@Override
			public boolean accept(File dir, String name) {
				for (String ext : exts) {
					if (name.endsWith(ext)) {
						return true;
					}
				}
				return false;
			}
		});

		// No results?
		if (files.length == 0) {
			logger.warn("No files found in directory: {}", directory);
		}

		// Parse each file.
		for (File file : files) {
			loadFile(file);
		}
	}

	/**
	 * Loads multiple RiveScript documents from a directory on disk.
	 *
	 * @param path The path to the directory containing the RiveScript documents
	 * @throws RiveScriptException in case of a loading error
	 * @throws ParserException     in case of a parsing error
	 */
	public void loadDirectory(String path, String... extensions) throws RiveScriptException, ParserException {
		requireNonNull(path, "'path' must not be null");
		loadDirectory(new File(path), extensions);
	}

	/**
	 * Loads RiveScript source code from a text buffer, with line breaks after each line.
	 * 문자열 RiveScript code를 load한다. 개행 단위로 짤라서 파싱한다.
	 * @param code the RiveScript source code
	 * @throws ParserException in case of a parsing error
	 */
	public void stream(String code) throws ParserException {
		String[] lines = code.split("\n");
		stream(lines);
	}

	/**
	 * Loads RiveScript source code from a {@link String} array, one line per item.
	 *
	 * @param code the lines of RiveScript source code
	 * @throws ParserException in case of a parsing error
	 */
	public void stream(String[] code) throws ParserException {
		parse("stream()", code);
	}

	/*---------------------*/
	/*-- Parsing Methods --*/
	/*---------------------*/

	/**
	 * Parses the RiveScript source code into the bot's memory.
	 * 챗봇 메모리에 RiveScript 스크립트 코드를 파싱한다.
	 * @param filename the arbitrary name for the source code being parsed,파일의 절대경로.
	 * @param code     the lines of RiveScript source code,파일에서 읽어온 RiveScript 스크립트 코드 배열.
	 * @throws ParserException in case of a parsing error
	 */
	private void parse(String filename, String[] code) throws ParserException {
		/**
		 * 파서에서 파싱처리후 Root트리를 반환한다.
		 */
		Root ast = this.parser.parse(filename, code);

		/**
		 * .rive파일에서 파싱된 defined 요소들을 모두 챗봇 객체의 인스턴스 변수에 설정해준다.
		 */
		for (Map.Entry<String, String> entry : ast.getBegin().getGlobal().entrySet()) {
			if (entry.getValue().equals(UNDEF_TAG)) {
				this.global.remove(entry.getKey());
			} else {
				this.global.put(entry.getKey(), entry.getValue());
			}
		}
		for (Map.Entry<String, String> entry : ast.getBegin().getVar().entrySet()) {
			if (entry.getValue().equals(UNDEF_TAG)) {
				this.vars.remove(entry.getKey());
			} else {
				this.vars.put(entry.getKey(), entry.getValue());
			}
		}
		for (Map.Entry<String, String> entry : ast.getBegin().getSub().entrySet()) {
			if (entry.getValue().equals(UNDEF_TAG)) {
				this.sub.remove(entry.getKey());
			} else {
				this.sub.put(entry.getKey(), entry.getValue());
			}
		}
		for (Map.Entry<String, String> entry : ast.getBegin().getPerson().entrySet()) {
			if (entry.getValue().equals(UNDEF_TAG)) {
				this.person.remove(entry.getKey());
			} else {
				this.person.put(entry.getKey(), entry.getValue());
			}
		}
		for (Map.Entry<String, List<String>> entry : ast.getBegin().getArray().entrySet()) {
			if (entry.getValue().equals(UNDEF_TAG)) {
				this.array.remove(entry.getKey());
			} else {
				this.array.put(entry.getKey(), entry.getValue());
			}
		}
		
		/**
		 * 파싱된 트리거를 모두 챗봇에 설정한다.
		 */
		for (Map.Entry<String, Topic> entry : ast.getTopics().entrySet()) {
			/**
			 * 추상구문트리에서 토픽을 모두 꺼내온다.
			 */
			String topic = entry.getKey();
			Topic data = entry.getValue();

			/**
			 * 만약 해당 토픽에 포함된 토픽 혹은 상속된 토픽이 존재한다면 챗봇에 초기화해준다.
			 */
			if (!this.includes.containsKey(topic)) {
				this.includes.put(topic, new HashMap<String, Boolean>());
			}
			if (!this.inherits.containsKey(topic)) {
				this.inherits.put(topic, new HashMap<String, Boolean>());
			}

			/**
			 * 초기화된 상속,포함 토픽에 데이터를 설정해준다.
			 */
			for (String included : data.getIncludes().keySet()) {
				this.includes.get(topic).put(included, true);
			}
			for (String inherited : data.getInherits().keySet()) {
				this.inherits.get(topic).put(inherited, true);
			}
			
			/**
			 * 토픽트리를 초기화해준다.
			 */
			if (!this.topics.containsKey(topic)) {
				this.topics.put(topic, new Topic());
			}
			
			/**
			 * 파싱된 트리거들을 모두 챗봇의 토픽트리에 설정해준다.
			 */
			for (Trigger astTrigger : data.getTriggers()) {
				// Convert this AST trigger into an internal trigger.
				Trigger trigger = new Trigger();
				trigger.setTrigger(astTrigger.getTrigger());
				trigger.setReply(new ArrayList<>(astTrigger.getReply()));
				trigger.setCondition(new ArrayList<>(astTrigger.getCondition()));
				trigger.setRedirect(astTrigger.getRedirect());
				trigger.setPrevious(astTrigger.getPrevious());

				this.topics.get(topic).addTrigger(trigger);
			}
		}

		/**
		 * 파싱된 개체 매크로들을 챗봇에 설정해준다.
		 */
		for (ObjectMacro object : ast.getObjects()) {
			/**
			 * 파싱된 개체 매크로들을 챗봇에 설정하기 위해서는 챗봇 초기화단계에서 
			 * 개체 핸들러들이 등록되어있어야한다.
			 */
			if (this.handlers.containsKey(object.getLanguage())) {
				this.handlers.get(object.getLanguage()).load(this, object.getName(), object.getCode().toArray(new String[0]));
				this.objectLanguages.put(object.getName(), object.getLanguage());
			} else {
				logger.warn("Object '{}' not loaded as no handler was found for programming language '{}'", object.getName(),
						object.getLanguage());
			}
		}
	}

	/*---------------------*/
	/*-- Sorting Methods --*/
	/*---------------------*/

	/**
	 * 메모리 내에서 최적의 매칭을 위해 답변의 구조를 정렬한다.
	 * 이 버퍼를 통하여 답변 매칭을 아주 효율적으로 해준다.
	 */
	public void sortReplies() {
		/**
		 * SortBuffer를 초기화한다.
		 */
		this.sorted.getTopics().clear();
		this.sorted.getThats().clear();
		logger.debug("Sorting triggers...");

		/**
		 * 모든 토픽에 대한 반복문을 통해 정렬한다.
		 */
		for (String topic : this.topics.keySet()) {
			logger.debug("Analyzing topic {}", topic);

			/**
			 * 우리가 작성하여 파싱된 모든 트리거를 리스트로 수집한다.
			 * 해당 요소 토픽이 다른 토픽을 상속하고 있다면, 재귀적으로 리스트에 상속토픽을 추가한다.
			 * 
			 * getTopicTriggers(String topic, boolean hasThats, int depth,int inheritance, boolean inherited)
			 */
			List<SortedTriggerEntry> allTriggers = getTopicTriggers(topic, false, 0, 0, false);

			/**
			 * 트리거셋을 정렬한다.
			 */
			this.sorted.addTopic(topic, sortTriggerSet(allTriggers, true));

			/**
			 * % Previous를 가지고있는 트리거셋
			 */
			List<SortedTriggerEntry> thatTriggers = getTopicTriggers(topic, true, 0, 0, false);

			/**
			 * % Previous 가진 트리거셋 정렬
			 */
			this.sorted.addThats(topic, sortTriggerSet(thatTriggers, false));
		}

		/**
		 * 문자 길이순으로 정렬한다.(DESC)
		 */
		this.sorted.setSub(sortList(this.sub.keySet()));
		this.sorted.setPerson(sortList(this.person.keySet()));
	}

	/**
	 * <p>
	 * 토픽을 재귀적으로 스캔하고 트리거를 수집한다.
	 * 이 메소드는 토픽을 검색하여 상속하거나 포함하는 주제에 속하는 트리거와 함께 트리거를 수집한다.
	 * 일부 트리거는 상속 깊이를 나타 내기위해 {@code inherits} 태그를 사용한다.
	 * <p>
	 * "상속"과"포함"은 차이가 있다.
	 * 다른 토픽을 상속한 토픽은 상속한 토픽에 정의된 트리거를 OVERRIDE 할 수 있다. 이것은 만약 상위 토픽에 "*"트리거가 있을 경우, 상속받은 토픽은
	 * 어떠한 트리거도 매핑되지 않을 수 있다. 왜냐하면 "*"의 단순 트리거가 우선순위는 낮지만, 상속된 모든 트리거는 상속받은 트리거보다 우선순위가 있기 때문이다.
	 * <p>
	 * 다른 토픽을 상속받은 토픽은 inherits가 0부터 시작하고 상속하고 있는 토픽이 증가할 때 마다 그 값이 증가하는 inherits 태그로 프리픽스된 트리거를 갖는다.
	 * 그래서 우리는 이 태그를 이용하여 상속받은 토픽은 inherits = 0~n 까지로 부터 항상 스택의 상위로 위치 시킬 수 있다.
	 * <p>
	 * depth는 재귀호출을 할때마다 1씩 증가한다.inheritance는 다른 토픽을 상속할때마다 1씩증가한다.
	 * <p>
	 * 만약, > topic a includes b inherits c 라면,
	 * a&b는 함께 결합되어 매칭풀에 들어가고, 또한 이 결합된 매칭들은 c라는 상위 우선순위를 갖는다.
	 * <p>
	 * This way, {@code > topic alpha includes beta inherits gamma} will have this effect:
	 * alpha and beta's triggers are combined together into one matching pool, and then those triggers have higher priority than gamma's.
	 * <p>
	 * The {@code inherited} option is {@code true} if this is a recursive call, from a topic that inherits other topics. This forces the
	 * {@code {inherits}} tag to be added to the triggers. This only applies when the top topic 'includes' another topic.
	 * 
	 * 
	 * @param topic       the name of the topic to scan through
	 * @param thats       indicates to get replies with {@code %Previous} or not
	 * @param depth       the recursion depth counter
	 * @param inheritance the inheritance counter
	 * @param inherited   the inherited status
	 * @return the list of triggers
	 */
	private List<SortedTriggerEntry> getTopicTriggers(String topic, boolean thats, int depth, int inheritance, boolean inherited) {
		/**
		 * 초기설정한 깊이보다 깊다면 예외를 발생시키거나, 빈 리스트를 반환한다.
		 */
		if (checkDeepRecursion(depth, "Deep recursion while scanning topic inheritance!")) {
			return new ArrayList<>();
		}

		logger.debug("Collecting trigger list for topic {} (depth={}; inheritance={}; inherited={})", topic, depth, inheritance, inherited);

		/**
		 * 반환된 트리거를 수집한다.
		 */
		List<SortedTriggerEntry> triggers = new ArrayList<>();

		/**
		 * 현재 토픽의 트리거를 담는 리스트
		 */
		List<SortedTriggerEntry> inThisTopic = new ArrayList<>();
		
		if (this.topics.containsKey(topic)) {
			for (Trigger trigger : this.topics.get(topic).getTriggers()) {
				
				/**
				 * "%"가 없는 경우는 모든 트리거를 추가한다.
				 * 현 트리거의 답변과 자기자신을 가르키는 트리거 객체를 넣는다.
				 */
				if (!thats) {
					SortedTriggerEntry entry = new SortedTriggerEntry(trigger.getTrigger(), trigger);
					inThisTopic.add(entry);
				} else {
					/**
					 * "%"가 있는 트리거
					 * 만약 현 트리거가 thats이 존재한다면 
					 * 이전 답변과 이전답변 이후에 나오는 현재 트리거객체를 넣는다.
					 */
					if (trigger.getPrevious() != null) {
						SortedTriggerEntry entry = new SortedTriggerEntry(trigger.getPrevious(), trigger);
						inThisTopic.add(entry);
					}
				}
			}
		}

		/**
		 * 만약 다른 트리거를 포함하고 있다면?
		 */
		if (this.includes.containsKey(topic)) {
			for (String includes : this.includes.get(topic).keySet()) {
				logger.debug("Topic {} includes {}", topic, includes);
				triggers.addAll(getTopicTriggers(includes, thats, depth + 1, inheritance + 1, false));
			}
		}
		
		/**
		 * 다른 토픽을 상속받았다면?
		 */
		if (this.inherits.containsKey(topic)) {
			for (String inherits : this.inherits.get(topic).keySet()) {
				logger.debug("Topic {} inherits {}", topic, inherits);
				triggers.addAll(getTopicTriggers(inherits, thats, depth + 1, inheritance + 1, true));
			}
		}

		/**
		 * 만약 상속받은 토픽이거나 inherited가 true일 경우, 트리거에 inherits 태그를 라벨링한다.
		 * 그리고 이 토픽의 트리거는 상속받은 트리거보다 우선순위가 높다.(우선순위는 inherits 태그로 판단한다.)
		 */
		if ((this.inherits.containsKey(topic) && this.inherits.get(topic).size() > 0) || inherited) {
			for (SortedTriggerEntry trigger : inThisTopic) {
				logger.debug("Prefixing trigger with {inherits={}} {}", inheritance, trigger.getTrigger());
				String label = String.format("{inherits=%d}%s", inheritance, trigger.getTrigger());
				triggers.add(new SortedTriggerEntry(label, trigger.getPointer()));
			}
		} else {
			for (SortedTriggerEntry trigger : inThisTopic) {
				triggers.add(new SortedTriggerEntry(trigger.getTrigger(), trigger.getPointer()));
			}
		}

		return triggers;
	}

	/**
	 * 최적의 정렬 순서로 트리거를 정렬한다.
	 * <p>
	 * This function has two use cases:
	 * <p>
	 * <ol>
	 * <li>"% Previous"를 포함하지 않는 "normal"한 트리거 정렬버퍼를 생성한다.
	 * <li>"% Previous"를 포함하고 있는 트리거 정렬버퍼를 생성한다.
	 * </ol>
	 * <p>
	 * Use the {@code excludePrevious} parameter to control which one is being done.
	 * 이 메소드는 중복된 트리거 패턴을 가지지 않는 정렬된 트리거 리스트를 반환한다.
	 * (unless the source RiveScript code explicitly uses the same duplicate pattern twice, which is a user error).
	 *
	 * @param triggers        the triggers to sort
	 * @param excludePrevious indicates to exclude triggers with {@code %Previous} or not
	 * @return the sorted triggers
	 */
	private List<SortedTriggerEntry> sortTriggerSet(List<SortedTriggerEntry> triggers, boolean excludePrevious) {
		/**
		 * 트리거 우선순위 맵을 만든다.
		 */
		Map<Integer, List<SortedTriggerEntry>> priority = new HashMap<>();

		/**
		 * weight 태그가 있는지 본다.(default 0).
		 */
		for (SortedTriggerEntry trigger : triggers) {
			if (excludePrevious && trigger.getPointer().getPrevious() != null) {
				continue;
			}

			// Check the trigger text for any {weight} tags, default being 0.
			int weight = 0;
			Matcher matcher = RE_WEIGHT.matcher(trigger.getTrigger());
			if (matcher.find()) {
				weight = Integer.parseInt(matcher.group(1));
			}

			// First trigger of this priority? Initialize the weight map.
			if (!priority.containsKey(weight)) {
				priority.put(weight, new ArrayList<SortedTriggerEntry>());
			}

			priority.get(weight).add(trigger);
		}

		// Keep a running list of sorted triggers for this topic.
		List<SortedTriggerEntry> running = new ArrayList<>();

		// Sort the priorities with the highest number first.
		List<Integer> sortedPriorities = new ArrayList<>();
		for (Integer k : priority.keySet()) {
			sortedPriorities.add(k);
		}
		/**
		 * weight를 내림차순으로 정렬한다.
		 */
		Collections.sort(sortedPriorities);
		Collections.reverse(sortedPriorities);

		// Go through each priority set.
		for (Integer p : sortedPriorities) {
			logger.debug("Sorting triggers with priority {}", p);

			/**
			 * 다른 토픽을 상속받은 토픽일 수 있기 때문에 일부는 inherits 태그를 포함하고 있다.
			 * inherits값이 작을 수록 스택에서 높은 우선순위를 가진다.
			 */
			int inherits = -1;        // -1 값은 inherits하지 않은 것임을 뜻함.
			int highestInherits = -1; // 지금까지 본 가장 큰 수

			// Loop through and categorize these triggers.
			Map<Integer, SortTrack> track = new HashMap<>();
			track.put(inherits, new SortTrack());

			// Loop through all the triggers.
			for (SortedTriggerEntry trigger : priority.get(p)) {
				String pattern = trigger.getTrigger();
				logger.debug("Looking at trigger: {}", pattern);

				// See if the trigger has an {inherits} tag.
				Matcher matcher = RE_INHERITS.matcher(pattern);
				if (matcher.find()) {
					inherits = Integer.parseInt(matcher.group(1));
					if (inherits > highestInherits) {
						highestInherits = inherits;
					}
					logger.debug("Trigger belongs to a topic that inherits other topics. Level={}", inherits);
					pattern = pattern.replaceAll("\\{inherits=\\d+\\}", "");
					trigger.setTrigger(pattern);
				} else {
					inherits = -1;
				}

				// If this is the first time we've seen this inheritance level, initialize its sort track structure.
				if (!track.containsKey(inherits)) {
					track.put(inherits, new SortTrack());
				}

				// Start inspecting the trigger's contents.
				if (pattern.contains("_")) {
					/**
					 * "_"가 몇개 포함되어있는지 카운팅한다.
					 */
					int count = countWords(pattern, false);
					logger.debug("Has a _ wildcard with {} words", count);
					if (count > 0) {
						if (!track.get(inherits).getAlpha().containsKey(count)) {
							track.get(inherits).getAlpha().put(count, new ArrayList<SortedTriggerEntry>());
						}
						track.get(inherits).getAlpha().get(count).add(trigger);
					} else {
						track.get(inherits).getUnder().add(trigger);
					}
				} else if (pattern.contains("#")) {
					/**
					 * "#"가 몇개 포함되어있는지 카운팅한다.
					 */
					int count = countWords(pattern, false);
					logger.debug("Has a # wildcard with {} words", count);
					if (count > 0) {
						if (!track.get(inherits).getNumber().containsKey(count)) {
							track.get(inherits).getNumber().put(count, new ArrayList<SortedTriggerEntry>());
						}
						track.get(inherits).getNumber().get(count).add(trigger);
					} else {
						track.get(inherits).getPound().add(trigger);
					}
				} else if (pattern.contains("*")) {
					/**
					 * "*"가 몇개 포함되어있는지 카운팅한다.
					 */
					int count = countWords(pattern, false);
					logger.debug("Has a * wildcard with {} words", count);
					if (count > 0) {
						if (!track.get(inherits).getWild().containsKey(count)) {
							track.get(inherits).getWild().put(count, new ArrayList<SortedTriggerEntry>());
						}
						track.get(inherits).getWild().get(count).add(trigger);
					} else {
						track.get(inherits).getStar().add(trigger);
					}
				} else if (pattern.contains("[")) {
					/**
					 * "["가 몇개 포함되어있는지 카운팅한다.
					 */
					int count = countWords(pattern, false);
					logger.debug("Has optionals with {} words", count);
					if (!track.get(inherits).getOption().containsKey(count)) {
						track.get(inherits).getOption().put(count, new ArrayList<SortedTriggerEntry>());
					}
					track.get(inherits).getOption().get(count).add(trigger);
				} else {
					// Totally atomic.
					int count = countWords(pattern, false);
					logger.debug("Totally atomic trigger with {} words", count);
					if (!track.get(inherits).getAtomic().containsKey(count)) {
						track.get(inherits).getAtomic().put(count, new ArrayList<SortedTriggerEntry>());
					}
					track.get(inherits).getAtomic().get(count).add(trigger);
				}
			}

			/**
			 * inherits를 가지지않은 트리거들은 항상 inherits 중 가장 우선순위가 낮은 트리거의
			 * 바로 낮은 우선순위를 가진 트리거로 스택에 쌓인다.
			 */
			track.put(highestInherits + 1, track.get(-1));
			track.remove(-1);

			/**
			 * inherits를 기준으로 정렬한다.(ASC)
			 */
			List<Integer> trackSorted = new ArrayList<>();
			for (Integer k : track.keySet()) {
				trackSorted.add(k);
			}
			Collections.sort(trackSorted);

			/**
			 * 우선순위가 높은 순서대로 SortedTriggerEntry에 추가한다.
			 */
			for (Integer ip : trackSorted) {
				logger.debug("ip={}", ip);

				/**
				 * 단어수(각각의 와일드카드수)로 정렬
				 */
				running.addAll(sortByWords(track.get(ip).getAtomic()));
				running.addAll(sortByWords(track.get(ip).getOption()));
				running.addAll(sortByWords(track.get(ip).getAlpha()));
				running.addAll(sortByWords(track.get(ip).getNumber()));
				running.addAll(sortByWords(track.get(ip).getWild()));

				/**
				 * 단일 와일드카드 트리거를 문자 길이별로 정렬
				 */
				running.addAll(sortByLength(track.get(ip).getUnder()));
				running.addAll(sortByLength(track.get(ip).getPound()));
				running.addAll(sortByLength(track.get(ip).getStar()));
			}
		}

		return running;
	}

	/**
	 * Sorts a list of strings by their word counts and lengths.
	 *
	 * @param list the list to sort
	 * @return the sorted list
	 */
	private List<String> sortList(Iterable<String> list) {
		List<String> output = new ArrayList<>();

		// Track by number of words.
		Map<Integer, List<String>> track = new HashMap<>();

		// Loop through each item.
		for (String item : list) {
			int count = StringUtils.countWords(item, true);
			if (!track.containsKey(count)) {
				track.put(count, new ArrayList<String>());
			}
			track.get(count).add(item);
		}

		// Sort them by word count, descending.
		List<Integer> sortedCounts = new ArrayList<>();
		for (Integer count : track.keySet()) {
			sortedCounts.add(count);
		}
		Collections.sort(sortedCounts);
		Collections.reverse(sortedCounts);

		for (Integer count : sortedCounts) {
			// Sort the strings of this word-count by their lengths.
			List<String> sortedLengths = track.get(count);
			Collections.sort(sortedLengths, byLengthReverse());
			for (String item : sortedLengths) {
				output.add(item);
			}
		}

		return output;
	}

	/**
	 * 단어 수(각각의 와일드카드수)와 전체 길이로 트리거 세트를 정렬합니다.
	 * <p>
	 * 이것은 SortTrack의 {@code atomic}, {@code option}, {@code alpha},
	 * {@code number} 및 {@code wild} 속성을 정렬하고 특정 순서로 정렬 버퍼를 실행 중입니다.
	 * @param triggers the triggers to sort
	 * @return the sorted triggers
	 */
	private List<SortedTriggerEntry> sortByWords(Map<Integer, List<SortedTriggerEntry>> triggers) {
		/**
		 * 단어수(각각의 와일드카드수)로 정렬을 진행한다.
		 */
		List<Integer> sortedWords = new ArrayList<>();
		for (Integer wc : triggers.keySet()) {
			sortedWords.add(wc);
		}
		/**
		 * 단어수(각각의 와일드카드수)가 많은 순서대로 정렬
		 */
		Collections.sort(sortedWords);
		Collections.reverse(sortedWords);

		List<SortedTriggerEntry> sorted = new ArrayList<>();

		for (Integer wc : sortedWords) {
			/**
			 * 단어수가 같다면 전체 문장길이로 정렬
			 */
			List<String> sortedPatterns = new ArrayList<>();
			Map<String, List<SortedTriggerEntry>> patternMap = new HashMap<>();

			for (SortedTriggerEntry trigger : triggers.get(wc)) {
				sortedPatterns.add(trigger.getTrigger());
				if (!patternMap.containsKey(trigger.getTrigger())) {
					patternMap.put(trigger.getTrigger(), new ArrayList<SortedTriggerEntry>());
				}
				patternMap.get(trigger.getTrigger()).add(trigger);
			}
			/**
			 * 문장길이로 정렬
			 */
			Collections.sort(sortedPatterns, byLengthReverse());

			// Add the triggers to the sorted triggers bucket.
			for (String pattern : sortedPatterns) {
				sorted.addAll(patternMap.get(pattern));
			}
		}

		return sorted;
	}

	/**
	 * Sorts a set of triggers purely by character length.
	 * <p>
	 * This is like {@link #sortByWords(Map)}, but it's intended for triggers that consist solely of wildcard-like symbols with no real words.
	 * For example a trigger of {@code * * *} qualifies for this, and it has no words,
	 * so we sort by length so it gets a higher priority than simply {@code *}.
	 *
	 * @param triggers the triggers to sort
	 * @return the sorted triggers
	 */
	private List<SortedTriggerEntry> sortByLength(List<SortedTriggerEntry> triggers) {
		List<String> sortedPatterns = new ArrayList<>();
		Map<String, List<SortedTriggerEntry>> patternMap = new HashMap<>();
		for (SortedTriggerEntry trigger : triggers) {
			sortedPatterns.add(trigger.getTrigger());
			if (!patternMap.containsKey(trigger.getTrigger())) {
				patternMap.put(trigger.getTrigger(), new ArrayList<SortedTriggerEntry>());
			}
			patternMap.get(trigger.getTrigger()).add(trigger);
		}
		Collections.sort(sortedPatterns, byLengthReverse());

		// Only loop through unique patterns.
		Map<String, Boolean> patternSet = new HashMap<>();

		List<SortedTriggerEntry> sorted = new ArrayList<>();

		// Add them to the sorted triggers bucket.
		for (String pattern : sortedPatterns) {
			if (patternSet.containsKey(pattern) && patternSet.get(pattern)) {
				continue;
			}
			patternSet.put(pattern, true);
			sorted.addAll(patternMap.get(pattern));
		}

		return sorted;
	}


	/**
	 * Returns a {@link Comparator<String>} to sort a list of {@link String}s by reverse length.
	 * Strings with equal length will be sorted alphabetically (natural ordering).
	 *
	 * @return the comparator
	 */
	private Comparator<String> byLengthReverse() {
		return new Comparator<String>() {

			@Override
			public int compare(String o1, String o2) {
				int result = Integer.compare(o2.length(), o1.length());
				if (result == 0) {
					result = o1.compareTo(o2);
				}
				return result;
			}
		};
	}

	/*---------------------*/
	/*-- Reply Methods   --*/
	/*---------------------*/

	/**
	 * 사용자 메시지에 대한 챗봇의 응답을 반환한다.
	 * <p>
	 * In case of an exception and exception throwing is enabled a {@link RiveScriptException} is thrown.
	 * Check the subclasses to see which types exceptions can be thrown.
	 *
	 * @param username the username
	 * @param message  the user's message
	 * @return the reply
	 * @throws RiveScriptException in case of an exception and exception throwing is enabled
	 */
	public String reply(String username, String message) throws RiveScriptException {
		logger.debug("Asked to reply to [{}] {}", username, message);

		long startTime = System.currentTimeMillis();

		/**
		 * 현재 사용자 아이디를 저장한다.
		 * ThreadLocal을 이용하여 하나의 스레드 전과정에서
		 * 해당 유저정보를 사용한다. 
		 * 웹환경은 여러개의 스레드가 떠서 동작함으로 
		 * 잘못된 데이터 참조가 있을 수 있으므로 반드시 마지막에
		 * ThreadLocal.remove(); 해주어야한다.
		 */
		this.currentUser.set(username);

		try {
			/**
			 * 사용자의 세션정보 초기화
			 */
			this.sessions.init(username);

			// Format their message.
			message = formatMessage(message, false);

			String reply;

			/**
			 * BEGIN block이 존재한다면 먼저 처리한다.
			 */
			if (this.topics.containsKey("__begin__")) {
				String begin = getReply(username, "request", true, 0);

				// OK to continue?
				if (begin.contains("{ok}")) {
					reply = getReply(username, message, false, 0);
					begin = begin.replaceAll("\\{ok\\}", reply);
				}

				reply = begin;
				reply = processTags(username, message, reply, new ArrayList<String>(), new ArrayList<String>(), 0);
			} else {
				reply = getReply(username, message, false, 0);
			}

			// Save their message history.
			this.sessions.addHistory(username, message, reply);

			if (logger.isDebugEnabled()) {
				long elapsedTime = System.currentTimeMillis() - startTime;
				logger.debug("Replied [{}] to [{}] in {} ms", reply, username, elapsedTime);
			}

			return reply;

		} finally {
			// Unset the current user's ID.
			this.currentUser.remove();
		}
	}

	/**
	 * Returns a reply from the bot for a user's message.
	 *
	 * @param username the username
	 * @param message  the user's message
	 * @param isBegin  whether this reply is for the {@code BEGIN} block context or not.
	 * @param step     the recursion depth counter
	 * @return the reply
	 */
	private String getReply(String username, String message, boolean isBegin, int step) {
		/**
		 * 만약 트리거들을 sort하지 않았다면 예외발생 혹은 로그를 출력한다
		 */
		if (this.sorted.getTopics().size() == 0) {
			logger.warn("You forgot to call sortReplies()!");
			String errorMessage = this.errorMessages.get(REPLIES_NOT_SORTED_KEY);
			if (this.throwExceptions) {
				throw new RepliesNotSortedException(errorMessage);
			}
			return errorMessage;
		}
		
		/**
		 * 현재 사용자의 데이터를 수집한다.
		 */
		String topic = this.sessions.get(username, "topic");
		if (topic == null) {
			topic = "random";
		}
		
		List<String> stars = new ArrayList<>();
		List<String> thatStars = new ArrayList<>();
		String reply = null;

		/**
		 * topic이 존재하지 않는다면 random으로 설정해준다.
		 */
		if (!this.topics.containsKey(topic)) {
			logger.warn("User {} was in an empty topic named '{}'", username, topic);
			topic = "random";
			this.sessions.set(username, "topic", topic);
		}

		/**
		 * 재귀깊이 체크
		 */
		if (checkDeepRecursion(step, "Deep recursion while getting reply!")) {
			return this.errorMessages.get(DEEP_RECURSION_KEY);
		}

		/**
		 * isBegin == true라면 토픽을 "__begin__"으로 설정
		 */
		if (isBegin) {
			topic = "__begin__";
		}

		/**
		 * begin여부 확인후 다시 토픽 존재여부 확인.
		 */
		if (!this.topics.containsKey(topic)) {
			// This was handled before, which would mean topic=random and it doesn't exist. Serious issue!
			String errorMessage = this.errorMessages.get(DEFAULT_TOPIC_NOT_FOUND_KEY);
			if (this.throwExceptions) {
				throw new NoDefaultTopicException(errorMessage);
			}
			return errorMessage;
		}

		// Create a pointer for the matched data when we find it.
		Trigger matched = null;
		String matchedTrigger = null;
		boolean foundMatch = false;

		// This is because in a redirection, "lastReply" is still gonna be the same as it was the first time,
		// resulting in an infinite loop!
		/**
		 * 해당 토픽에 "% Previous" 혹은 관련된 토픽이 있는지 확인
		 * 이 작업은 재귀적인 리다이렉션 중이 아닌 처음에 수행되어야한다.
		 * 리다이렉션에서 "lastReply"는 처음과 동일 할 것이므로 무한 루프가 발생한다.
		 */
		if (step == 0) {
			List<String> allTopics = new ArrayList<>(Arrays.asList(topic));
			if (this.includes.get(topic).size() > 0 || this.inherits.get(topic).size() > 0) {
				// Get ALL the topics!
				allTopics = getTopicTree(topic, 0);
			}

			/**
			 * 모든 토픽을 조회한다.
			 */
			for (String top : allTopics) {
				logger.debug("Checking topic {} for any %Previous's", top);

				if (this.sorted.getThats(top).size() > 0) {
					logger.debug("There's a %Previous in this topic!");

					/**
					 * 사용자의 세션에서 이전 답변에 대한 정보를 가져온다.
					 */
					History history = this.sessions.getHistory(username);
					String lastReply = history.getReply().get(0);

					/**
					 * 챗봇의 이전 응답을 포맷팅해준다
					 */
					lastReply = formatMessage(lastReply, true);
					logger.debug("Bot's last reply: {}", lastReply);

					/**
					 * 매칭해본다
					 */
					for (SortedTriggerEntry trigger : this.sorted.getThats(top)) {
						String pattern = trigger.getPointer().getPrevious();
						String botside = triggerRegexp(username, pattern);
						logger.debug("Try to match lastReply {} to {} ({})", lastReply, pattern, botside);

						// Match?
						Pattern re = Pattern.compile("^" + botside + "$");
						Matcher matcher = re.matcher(lastReply);
						if (matcher.find()) {
							// Huzzah! See if OUR message is right too...
							logger.debug("Bot side matched!");

							// Collect the bot stars.
							for (int i = 1; i <= matcher.groupCount(); i++) {
								thatStars.add(matcher.group(i));
							}

							// Compare the triggers to the user's message.
							Trigger userSide = trigger.getPointer();
							String regexp = triggerRegexp(username, userSide.getTrigger());
							logger.debug("Try to match {} against {} ({})", message, userSide.getTrigger(), regexp);

							// If the trigger is atomic, we don't need to deal with the regexp engine.
							boolean isMatch = false;
							if (isAtomic(userSide.getTrigger())) {
								if (message.equals(regexp)) {
									isMatch = true;
								}
							} else {
								re = Pattern.compile("^" + regexp + "$");
								matcher = re.matcher(message);
								if (matcher.find()) {
									isMatch = true;

									// Get the user's message stars.
									for (int i = 1; i <= matcher.groupCount(); i++) {
										stars.add(matcher.group(i));
									}
								}
							}

							// Was it a match?
							if (isMatch) {
								// Keep the trigger pointer.
								matched = userSide;
								foundMatch = true;
								matchedTrigger = userSide.getTrigger();
								break;
							}
						}
					}
				}
			}
		}

		// Search their topic for a match to their trigger.
		if (!foundMatch) {
			logger.debug("Searching their topic for a match...");
			for (SortedTriggerEntry trigger : this.sorted.getTopic(topic)) {
				String pattern = trigger.getTrigger();
				String regexp = triggerRegexp(username, pattern);
				logger.debug("Try to match \"{}\" against {} ({})", message, pattern, regexp);

				// If the trigger is atomic, we don't need to bother with the regexp engine.
				boolean isMatch = false;
				if (isAtomic(pattern) && message.equals(regexp)) {
					isMatch = true;
				} else {
					// Non-atomic triggers always need the regexp.
					Pattern re = Pattern.compile("^" + regexp + "$");
					Matcher matcher = re.matcher(message);
					if (matcher.find()) {
						// The regexp matched!
						isMatch = true;

						// Collect the stars.
						for (int i = 1; i <= matcher.groupCount(); i++) {
							stars.add(matcher.group(i));
						}
					}
				}

				// A match somehow?
				if (isMatch) {
					logger.debug("Found a match!");

					// Keep the pointer to this trigger's data.
					matched = trigger.getPointer();
					foundMatch = true;
					matchedTrigger = pattern;
					break;
				}
			}
		}

		// Store what trigger they matched on.
		this.sessions.setLastMatch(username, matchedTrigger);

		// Did we match?
		if (foundMatch) {
			for (int n = 0; n < 1; n++) { // A single loop so we can break out early.
				// See if there are any hard redirects.
				if (matched.getRedirect() != null && matched.getRedirect().length() > 0) {
					logger.debug("Redirecting us to {}", matched.getRedirect());
					String redirect = matched.getRedirect();
					redirect = processTags(username, message, redirect, stars, thatStars, 0);
					redirect = redirect.toLowerCase();
					logger.debug("Pretend user said: {}", redirect);
					reply = getReply(username, redirect, isBegin, step + 1);
					break;
				}

				// Check the conditionals.
				for (String row : matched.getCondition()) {
					String[] halves = row.split("=>");
					if (halves.length == 2) {
						Matcher matcher = RE_CONDITION.matcher(halves[0].trim());
						if (matcher.find()) {
							String left = matcher.group(1).trim();
							String eq = matcher.group(2);
							String right = matcher.group(3).trim();
							String potentialReply = halves[1].trim();

							// Process tags all around.
							left = processTags(username, message, left, stars, thatStars, step);
							right = processTags(username, message, right, stars, thatStars, step);

							// Defaults?
							if (left.length() == 0) {
								left = UNDEFINED;
							}
							if (right.length() == 0) {
								right = UNDEFINED;
							}

							logger.debug("Check if {} {} {}", left, eq, right);

							// Validate it.
							boolean passed = false;

							if (eq.equals("eq") || eq.equals("==")) {
								if (left.equals(right)) {
									passed = true;
								}
							} else if (eq.equals("ne") || eq.equals("!=") || eq.equals("<>")) {
								if (!left.equals(right)) {
									passed = true;
								}
							} else {
								// Dealing with numbers here.
								int intLeft;
								int intRight;
								try {
									intLeft = Integer.parseInt(left);
									intRight = Integer.parseInt(right);
									if (eq.equals("<") && intLeft < intRight) {
										passed = true;
									} else if (eq.equals("<=") && intLeft <= intRight) {
										passed = true;
									} else if (eq.equals(">") && intLeft > intRight) {
										passed = true;
									} else if (eq.equals(">=") && intLeft >= intRight) {
										passed = true;
									}

								} catch (NumberFormatException e) {
									logger.warn("Failed to evaluate numeric condition!");
								}

							}

							if (passed) {
								reply = potentialReply;
								break;
							}
						}
					}
				}

				// Have our reply yet?
				if (reply != null && reply.length() > 0) {
					break;
				}

				// Process weights in the replies.
				List<String> bucket = new ArrayList<>();
				for (String rep : matched.getReply()) {
					int weight;
					Matcher matcher = RE_WEIGHT.matcher(rep);
					if (matcher.find()) {
						weight = Integer.parseInt(matcher.group(1));
						if (weight <= 0) {
							weight = 1;
						}

						for (int i = weight; i > 0; i--) {
							bucket.add(rep);
						}
					} else {
						bucket.add(rep);
					}
				}

				// Get a random reply.
				if (bucket.size() > 0) {
					reply = bucket.get(RANDOM.nextInt(bucket.size()));
				}
				break;
			}
		}

		// Still no reply?? Give up with the fallback error replies.
		if (!foundMatch) {
			String errorMessage = this.errorMessages.get(REPLY_NOT_MATCHED_KEY);
			if (this.throwExceptions) {
				throw new ReplyNotMatchedException(errorMessage);
			}
			reply = errorMessage;
		} else if (reply == null || reply.length() == 0) {
			String errorMessage = this.errorMessages.get(REPLY_NOT_FOUND_KEY);
			if (this.throwExceptions) {
				throw new ReplyNotFoundException(errorMessage);
			}
			reply = errorMessage;
		}

		logger.debug("Reply: {}", reply);

		// Process tags for the BEGIN block.
		if (isBegin) {
			// The BEGIN block can set {topic} and user vars.

			// Topic setter.
			Matcher matcher = RE_TOPIC.matcher(reply);
			int giveup = 0;
			while (matcher.find()) {
				giveup++;
				if (checkDeepRecursion(giveup, "Infinite loop looking for topic tag!")) {
					break;
				}
				String name = matcher.group(1);
				this.sessions.set(username, "topic", name);
				reply = reply.replace(matcher.group(0), "");
			}

			// Set user vars.
			matcher = RE_SET.matcher(reply);
			giveup = 0;
			while (matcher.find()) {
				giveup++;
				if (checkDeepRecursion(giveup, "Infinite loop looking for set tag!")) {
					break;
				}
				String name = matcher.group(1);
				String value = matcher.group(2);
				this.sessions.set(username, name, value);
				reply = reply.replace(matcher.group(0), "");
			}
		} else {
			reply = processTags(username, message, reply, stars, thatStars, 0);
		}

		return reply;
	}

	/**
	 * Formats a user's message for safe processing.
	 * 사용자의 메시지를 안전한 프로세스를 위해 형식화한다.
	 * @param message  the user's message
	 * @param botReply whether it is a bot reply or not
	 * @return the formatted message
	 */
	private String formatMessage(String message, boolean botReply) {
		// Lowercase it.
		message = "" + message;
		
		/**
		 * 만약 형테소분리 모드라면 사용자 질의를 전처리.
		 * By yeoseong_yoon
		 */
		if(this.morpheme.equals(MorphemeMode.SEPARATION)) {
			analyzer = new NoriAnalyzer();
			message = analyzer.analyzeForString(message);
			logger.info("Morpheme user question :::: {}",message);
		}
		
		/**
		 * 만약 형태소분리모드 상태이면 이미 소문자처리가 되서 나오므로 굳이 처리할 필요 없음
		 */
		message = message.toLowerCase();

		/**
		 * 사용자질의 substitute 처리
		 */
		message = substitute(message, this.sub, this.sorted.getSub());

		// In UTF-8 mode, only strip metacharacters and HTML brackets (to protect against obvious XSS attacks).
		if (this.utf8) {
			message = RE_META.matcher(message).replaceAll("");
			if (this.unicodePunctuation != null) {
				message = this.unicodePunctuation.matcher(message).replaceAll("");
			}

			// For the bot's reply, also strip common punctuation.
			if (botReply) {
				message = RE_SYMBOLS.matcher(message).replaceAll("");
			}
		} else {
			// For everything else, strip all non-alphanumerics.
			message = stripNasties(message);
		}

		// Cut leading and trailing blanks once punctuation dropped office.
		message = message.trim();
		message = message.replaceAll("\\s+", " ");

		return message;
	}

	/**
	 * Processes tags in a reply element.
	 *
	 * @param username the username
	 * @param message  the user's message
	 * @param reply    the reply
	 * @param st       the stars
	 * @param bst      the bot stars
	 * @param step     the recursion depth counter
	 * @return the processed reply
	 */
	private String processTags(String username, String message, String reply, List<String> st, List<String> bst, int step) {
		// Prepare the stars and botstars.
		List<String> stars = new ArrayList<>();
		stars.add("");
		stars.addAll(st);
		List<String> botstars = new ArrayList<>();
		botstars.add("");
		botstars.addAll(bst);
		if (stars.size() == 1) {
			stars.add(UNDEFINED);
		}
		if (botstars.size() == 1) {
			botstars.add(UNDEFINED);
		}

		// Turn arrays into randomized sets.
		Pattern re = Pattern.compile("\\(@([A-Za-z0-9_]+)\\)");
		Matcher matcher = re.matcher(reply);
		int giveup = 0;
		while (matcher.find()) {
			if (checkDeepRecursion(giveup, "Infinite loop looking for arrays in reply!")) {
				break;
			}

			String name = matcher.group(1);
			String result;
			if (this.array.containsKey(name)) {
				result = "{random}" + StringUtils.join(this.array.get(name).toArray(new String[0]), "|") + "{/random}";
			} else {
				result = "\\x00@" + name + "\\x00"; // Dummy it out so we can reinsert it later.
			}
			reply = reply.replace(matcher.group(0), result);
		}
		reply = reply.replaceAll("\\\\x00@([A-Za-z0-9_]+)\\\\x00", "(@$1)");

		// Tag shortcuts.
		reply = reply.replaceAll("<person>", "{person}<star>{/person}");
		reply = reply.replaceAll("<@>", "{@<star>}");
		reply = reply.replaceAll("<formal>", "{formal}<star>{/formal}");
		reply = reply.replaceAll("<sentence>", "{sentence}<star>{/sentence}");
		reply = reply.replaceAll("<uppercase>", "{uppercase}<star>{/uppercase}");
		reply = reply.replaceAll("<lowercase>", "{lowercase}<star>{/lowercase}");

		// Weight and star tags.
		reply = RE_WEIGHT.matcher(reply).replaceAll(""); // Remove {weight} tags.
		reply = reply.replaceAll("<star>", stars.get(1));
		reply = reply.replaceAll("<botstar>", botstars.get(1));
		for (int i = 1; i < stars.size(); i++) {
			reply = reply.replaceAll("<star" + i + ">", stars.get(i));
		}
		for (int i = 1; i < botstars.size(); i++) {
			reply = reply.replaceAll("<botstar" + i + ">", botstars.get(i));
		}

		// <input> and <reply> tags.
		reply = reply.replaceAll("<input>", "<input1>");
		reply = reply.replaceAll("<reply>", "<reply1>");
		History history = this.sessions.getHistory(username);
		if (history != null) {
			for (int i = 1; i <= HISTORY_SIZE; i++) {
				reply = reply.replaceAll("<input" + i + ">", history.getInput(i - 1));
				reply = reply.replaceAll("<reply" + i + ">", history.getReply(i - 1));
			}
		}

		// <id> and escape codes.
		reply = reply.replaceAll("<id>", username);
		reply = reply.replaceAll("\\\\s", " ");
		reply = reply.replaceAll("\\\\n", "\n");
		reply = reply.replaceAll("\\#", "#");

		// {random}
		matcher = RE_RANDOM.matcher(reply);
		giveup = 0;
		while (matcher.find()) {
			giveup++;
			if (checkDeepRecursion(giveup, "Infinite loop looking for random tag!")) {
				break;
			}

			String[] random;
			String text = matcher.group(1);
			if (text.contains("|")) {
				random = text.split("\\|");
			} else {
				random = text.split(" ");
			}

			String output = "";
			if (random.length > 0) {
				output = random[RANDOM.nextInt(random.length)];
			}

			reply = reply.replace(matcher.group(0), output);
		}

		// Person substitution and string formatting.
		String[] formats = new String[] {"person", "formal", "sentence", "uppercase", "lowercase"};
		for (String format : formats) {
			re = Pattern.compile("\\{" + format + "\\}(.+?)\\{\\/" + format + "\\}");
			matcher = re.matcher(reply);
			giveup = 0;
			while (matcher.find()) {
				giveup++;
				if (checkDeepRecursion(giveup, "Infinite loop looking for {} tag!")) {
					break;
				}

				String content = matcher.group(1);
				String replace = null;
				if (format.equals("person")) {
					replace = substitute(content, this.person, this.sorted.getPerson());
				} else {
					if (format.equals("uppercase")) {
						replace = content.toUpperCase();
					} else if (format.equals("lowercase")) {
						replace = content.toLowerCase();
					} else if (format.equals("sentence")) {
						if (content.length() > 1) {
							replace = content.substring(0, 1).toUpperCase() + content.substring(1).toLowerCase();
						} else {
							replace = content.toUpperCase();
						}
					} else if (format.equals("formal")) {
						String[] words = content.split(" ");
						for (int i = 0; i < words.length; i++) {
							String word = words[i];
							if (word.length() > 1) {
								words[i] = word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase();
							} else {
								words[i] = word.toUpperCase();
							}
						}
						replace = StringUtils.join(words, " ");
					}
				}

				reply = reply.replace(matcher.group(0), replace);
			}
		}

		// Handle all variable-related tags with an iterative regexp approach to
		// allow for nesting of tags in arbitrary ways (think <set a=<get b>>)
		// Dummy out the <call> tags first, because we don't handle them here.
		reply = reply.replaceAll("<call>", "{__call__}");
		reply = reply.replaceAll("</call>", "{/__call__}");
		while (true) {

			// Look for tags that don't contain any other tags inside them.
			matcher = RE_ANY_TAG.matcher(reply);
			if (!matcher.find()) {
				break; // No tags left!
			}

			String match = matcher.group(1);
			String[] parts = match.split(" ");
			String tag = parts[0].toLowerCase();
			String data = "";
			if (parts.length > 1) {
				data = StringUtils.join(Arrays.copyOfRange(parts, 1, parts.length), " ");
			}
			String insert = "";

			// Handle the various types of tags.
			if (tag.equals("bot") || tag.equals("env")) {
				// <bot> and <env> tags are similar.
				Map<String, String> target;
				if (tag.equals("bot")) {
					target = this.vars;
				} else {
					target = this.global;
				}

				if (data.contains("=")) {
					// Assigning the value.
					parts = data.split("=", 2);
					String name = parts[0];
					String value = parts[1];
					logger.debug("Assign {} variable {} = {}", tag, name, value);
					target.put(name, value);
				} else {
					// Getting a bot/env variable.
					if (target.containsKey(data)) {
						insert = target.get(data);
					} else {
						insert = UNDEFINED;
					}
				}
			} else if (tag.equals("set")) {
				// <set> user vars.
				parts = data.split("=", 2);
				if (parts.length > 1) {
					String name = parts[0];
					String value = parts[1];
					logger.debug("Set uservar {} = {}", name, value);
					this.sessions.set(username, name, value);
				} else {
					logger.warn("Malformed <set> tag: {}", match);
				}
			} else if (tag.equals("add") || tag.equals("sub") || tag.equals("mult") || tag.equals("div")) {
				// Math operator tags
				parts = data.split("=", 2);
				String name = parts[0];
				String strValue = parts[1];
				int result = 0;

				// Initialize the variable?
				String origStr = this.sessions.get(username, name);
				if (origStr == null) {
					origStr = "0";
					this.sessions.set(username, name, origStr);
				}

				// Sanity check.
				try {
					int value = Integer.parseInt(strValue);
					try {
						result = Integer.parseInt(origStr);

						// Run the operation.
						if (tag.equals("add")) {
							result += value;
						} else if (tag.equals("sub")) {
							result -= value;
						} else if (tag.equals("mult")) {
							result *= value;
						} else {
							// Don't divide by zero.
							if (value == 0) {
								logger.warn("Can't divide by zero");
								insert = this.errorMessages.get(CANNOT_DIVIDE_BY_ZERO_KEY);
							}
							result /= value;
						}
						this.sessions.set(username, name, Integer.toString(result));
					} catch (NumberFormatException e) {
						logger.warn("Math can't " + tag + " non-numeric variable " + name);
						insert = this.errorMessages.get(CANNOT_MATH_VARIABLE_KEY);
					}
				} catch (NumberFormatException e) {
					logger.warn("Math can't " + tag + " non-numeric value " + strValue);
					insert = this.errorMessages.get(CANNOT_MATH_VALUE_KEY);
				}
			} else if (tag.equals("get")) {
				// <get> user vars.
				insert = this.sessions.get(username, data);
				if (insert == null) {
					insert = UNDEFINED;
				}
			} else {
				// Unrecognized tag; preserve it.
				insert = "\\x00" + match + "\\x01";
			}

			reply = reply.replace(matcher.group(0), insert);
		}

		// Recover mangled HTML-like tags.
		reply = reply.replaceAll("\\\\x00", "<");
		reply = reply.replaceAll("\\\\x01", ">");

		// Topic setter.
		matcher = RE_TOPIC.matcher(reply);
		giveup = 0;
		while (matcher.find()) {
			giveup++;
			if (checkDeepRecursion(giveup, "Infinite loop looking for topic tag!")) {
				break;
			}

			String name = matcher.group(1);
			this.sessions.set(username, "topic", name);
			reply = reply.replace(matcher.group(0), "");
		}

		// Inline redirector.
		matcher = RE_REDIRECT.matcher(reply);
		giveup = 0;
		while (matcher.find()) {
			giveup++;
			if (checkDeepRecursion(giveup, "Infinite loop looking for redirect tag!")) {
				break;
			}

			String target = matcher.group(1);
			logger.debug("Inline redirection to: {}", target);
			String subreply = getReply(username, target.trim(), false, step + 1);
			reply = reply.replace(matcher.group(0), subreply);
		}

		// Object caller.
		reply = reply.replaceAll("\\{__call__\\}", "<call>");
		reply = reply.replaceAll("\\{/__call__\\}", "</call>");
		matcher = RE_CALL.matcher(reply);
		giveup = 0;
		while (matcher.find()) {
			giveup++;
			if (checkDeepRecursion(giveup, "Infinite loop looking for call tag!")) {
				break;
			}

			String text = matcher.group(1).trim();
			String[] parts = text.split(" ", 2);
			String obj = parts[0];
			String[] args;
			if (parts.length > 1) {
				args = parseCallArgsString(parts[1]);
			} else {
				args = new String[0];
			}

			// Do we know this object?
			String output;
			if (this.subroutines.containsKey(obj)) {
				// It exists as a native Java macro.
				output = this.subroutines.get(obj).call(this, args);
			} else if (this.objectLanguages.containsKey(obj)) {
				String language = this.objectLanguages.get(obj);
				output = this.handlers.get(language).call(this, obj, args);
			} else {
				output = this.errorMessages.get(OBJECT_NOT_FOUND_KEY);
			}
			if (output == null) {
				output = "";
			}

			reply = reply.replace(matcher.group(0), output);
		}

		return reply;
	}

	/**
	 * Converts an args {@link String} into a array of arguments.
	 *
	 * @param args the args string to convert
	 * @return the array of arguments
	 */
	private String[] parseCallArgsString(String args) {
		List<String> result = new ArrayList<>();
		String buffer = "";
		boolean insideAString = false;

		if (args != null) {
			for (char c : args.toCharArray()) {
				if (Character.isWhitespace(c) && !insideAString) {
					if (buffer.length() > 0) {
						result.add(buffer);
					}
					buffer = "";
					continue;
				}
				if (c == '"') {
					if (insideAString) {
						if (buffer.length() > 0) {
							result.add(buffer);
						}
						buffer = "";
					}
					insideAString = !insideAString;
					continue;
				}
				buffer += c;
			}

			if (buffer.length() > 0) {
				result.add(buffer);
			}
		}

		return result.toArray(new String[0]);
	}

	/**
	 * Applies a substitution to an input message.
	 *
	 * @param message the input message
	 * @param subs    the substitution map
	 * @param sorted  the substitution list
	 * @return the substituted message
	 */
	private String substitute(String message, Map<String, String> subs, List<String> sorted) {
		/**
		 * substitute 요소가 없으면 그대로 리턴
		 */
		if (subs == null || subs.size() == 0) {
			return message;
		}

		// Make placeholders each time we substitute something.
		List<String> ph = new ArrayList<>();
		int pi = 0;
		
		/**
		 * sort된 substitute를 이용하여 처리한다.
		 */
		for (String pattern : sorted) {
			String result = subs.get(pattern);
			String qm = quoteMetacharacters(pattern);

			// Make a placeholder.
			ph.add(result);
			String placeholder = "\\\\x00" + pi + "\\\\x00";
			pi++;

			// Run substitutions.
			message = message.replaceAll("^" + qm + "$", placeholder);
			message = message.replaceAll("^" + qm + "(\\W+)", placeholder + "$1");
			message = message.replaceAll("(\\W+)" + qm + "(\\W+)", "$1" + placeholder + "$2");
			message = message.replaceAll("(\\W+)" + qm + "$", "$1" + placeholder);
		}

		// Convert the placeholders back in.
		int tries = 0;
		while (message.contains("\\x00")) {
			tries++;
			if (checkDeepRecursion(tries, "Too many loops in substitution placeholders!")) {
				break;
			}

			Matcher matcher = RE_PLACEHOLDER.matcher(message);
			if (matcher.find()) {
				int i = Integer.parseInt(matcher.group(1));
				String result = ph.get(i);
				message = message.replace(matcher.group(0), result);
			}
		}

		return message;
	}

	/**
	 * 해당 토픽과 관련된 모든 토픽(상속,포함)의 리스트르 반환한다.(원래 토픽도 포함)
	 * 관련된 토픽의 관련된 토픽 또한 추가
	 * @param topic the name of the topic
	 * @param depth the recursion depth counter
	 * @return the list of topic names
	 */
	private List<String> getTopicTree(String topic, int depth) {
		/**
		 * 깊이체크
		 */
		if (checkDeepRecursion(depth, "Deep recursion while scanning topic tree!")) {
			return new ArrayList<>();
		}

		/**
		 * 모든 관련된 토픽과 원래 토픽을 리스트로 반환
		 */
		List<String> topics = new ArrayList<>(Arrays.asList(topic));
		for (String includes : this.topics.get(topic).getIncludes().keySet()) {
			topics.addAll(getTopicTree(includes, depth + 1));
		}
		for (String inherits : this.topics.get(topic).getInherits().keySet()) {
			topics.addAll(getTopicTree(inherits, depth + 1));
		}

		return topics;
	}

	/**
	 * 정규 표현 엔진의 트리거 패턴을 준비한다
	 *
	 * @param username the username
	 * @param pattern  the pattern
	 * @return the regular expression trigger pattern
	 */
	private String triggerRegexp(String username, String pattern) {
		// If the trigger is simply '*' then the * needs to become (.*?) to match the blank string too.
		pattern = RE_ZERO_WITH_STAR.matcher(pattern).replaceAll("<zerowidthstar>");

		// Simple replacements.
		pattern = pattern.replaceAll("\\*", "(.+?)");                  // Convert * into (.+?)
		pattern = pattern.replaceAll("#", "(\\\\d+?)");                // Convert # into (\d+?)
		pattern = pattern.replaceAll("(?<!\\\\)_", "(\\\\w+?)");       // Convert _ into (\w+?)
		pattern = pattern.replaceAll("\\\\_", "_");                    // Convert \_ into _
		pattern = pattern.replaceAll("\\s*\\{weight=\\d+\\}\\s*", ""); // Remove {weight} tags
		pattern = pattern.replaceAll("<zerowidthstar>", "(.*?)");      // Convert <zerowidthstar> into (.+?)
		pattern = pattern.replaceAll("\\|{2,}", "|");                  // Remove empty entities
		pattern = pattern.replaceAll("(\\(|\\[)\\|", "$1");            // Remove empty entities from start of alt/opts
		pattern = pattern.replaceAll("\\|(\\)|\\])", "$1");            // Remove empty entities from end of alt/opts

		// UTF-8 mode special characters.
		if (this.utf8) {
			// Literal @ symbols (like in an e-mail address) conflict with arrays.
			pattern = pattern.replaceAll("\\\\@", "\\\\u0040");
		}

		// Optionals.
		Matcher matcher = RE_OPTIONAL.matcher(pattern);
		int giveup = 0;
		while (matcher.find()) {
			giveup++;
			if (checkDeepRecursion(giveup, "Infinite loop when trying to process optionals in a trigger!")) {
				return "";
			}

			String[] parts = matcher.group(1).split("\\|");
			List<String> opts = new ArrayList<>();
			for (String p : parts) {
				opts.add("(?:\\s|\\b)+" + p + "(?:\\s|\\b)+");
			}

			// If this optional had a star or anything in it, make it non-matching.
			String pipes = StringUtils.join(opts.toArray(new String[0]), "|");
			pipes = pipes.replaceAll(StringUtils.quoteMetacharacters("(.+?)"), "(?:.+?)");
			pipes = pipes.replaceAll(StringUtils.quoteMetacharacters("(\\d+?)"), "(?:\\\\d+?)");
			pipes = pipes.replaceAll(StringUtils.quoteMetacharacters("(\\w+?)"), "(?:\\\\w+?)");

			// Put the new text in.
			pipes = "(?:" + pipes + "|(?:\\s|\\b)+)";
			pattern = pattern.replaceAll("\\s*\\[" + StringUtils.quoteMetacharacters(matcher.group(1)) + "\\]\\s*",
					StringUtils.quoteMetacharacters(pipes));
		}

		// _ wildcards can't match numbers!
		// Quick note on why I did it this way: the initial replacement above (_ => (\w+?)) needs to be \w because the square brackets
		// in [\s\d] will confuse the optionals logic just above. So then we switch it back down here.
		// Also, we don't just use \w+ because that matches digits, and similarly [A-Za-z] doesn't work with Unicode,
		// so this regexp excludes spaces and digits instead of including letters.
		pattern = pattern.replaceAll("\\\\w", "[^\\\\s\\\\d]");

		// Filter in arrays.
		giveup = 0;
		matcher = RE_ARRAY.matcher(pattern);
		while (matcher.find()) {
			giveup++;
			if (checkDeepRecursion(giveup, "Infinite loop looking when trying to process arrays in a trigger!")) {
				break;
			}

			String name = matcher.group(1);
			String rep = "";
			if (this.array.containsKey(name)) {
				rep = "(?:" + StringUtils.join(this.array.get(name).toArray(new String[0]), "|") + ")";
			}
			pattern = pattern.replace(matcher.group(0), rep);
		}

		// Filter in bot variables.
		giveup = 0;
		matcher = RE_BOT_VAR.matcher(pattern);
		while (matcher.find()) {
			giveup++;
			if (checkDeepRecursion(giveup, "Infinite loop looking when trying to process bot variables in a trigger!")) {
				break;
			}

			String name = matcher.group(1);
			String rep = "";
			if (this.vars.containsKey(name)) {
				rep = StringUtils.stripNasties(this.vars.get(name));
			}
			pattern = pattern.replace(matcher.group(0), rep.toLowerCase());
		}

		// Filter in user variables.
		giveup = 0;
		matcher = RE_USER_VAR.matcher(pattern);
		while (matcher.find()) {
			giveup++;
			if (checkDeepRecursion(giveup, "Infinite loop looking when trying to process user variables in a trigger!")) {
				break;
			}

			String name = matcher.group(1);
			String rep = UNDEFINED;
			String value = this.sessions.get(username, name);
			if (value != null) {
				rep = value;
			}
			pattern = pattern.replace(matcher.group(0), rep.toLowerCase());
		}

		// Filter in <input> and <reply> tags.
		giveup = 0;
		pattern = pattern.replaceAll("<input>", "<input1>");
		pattern = pattern.replaceAll("<reply>", "<reply1>");

		while (pattern.contains("<input") || pattern.contains("<reply")) {
			giveup++;
			if (checkDeepRecursion(giveup, "Infinite loop looking when trying to process input and reply tags in a trigger!")) {
				break;
			}

			for (int i = 1; i <= HISTORY_SIZE; i++) {
				String inputPattern = "<input" + i + ">";
				String replyPattern = "<reply" + i + ">";
				History history = this.sessions.getHistory(username);
				if (history == null) {
					pattern = pattern.replace(inputPattern, history.getInput(i - 1));
					pattern = pattern.replace(replyPattern, history.getReply(i - 1));
				} else {
					pattern = pattern.replace(inputPattern, UNDEFINED);
					pattern = pattern.replace(replyPattern, UNDEFINED);
				}
			}
		}

		// Recover escaped Unicode symbols.
		if (this.utf8) {
			pattern = pattern.replaceAll("\\u0040", "@");
		}

		return pattern;
	}

	/**
	 * Returns whether a trigger is atomic or not.
	 *
	 * @param pattern the pattern
	 * @return whether the pattern is atmonic or not
	 */
	private boolean isAtomic(String pattern) {
		// Atomic triggers don't contain any wildcards or parenthesis or anything of the sort.
		// We don't need to test the full character set, just left brackets will do.
		List<String> specials = Arrays.asList("*", "#", "_", "(", "[", "<", "@");
		for (String special : specials) {
			if (pattern.contains(special)) {
				return false;
			}
		}
		return true;
	}

	/*------------------*/
	/*-- User Methods --*/
	/*------------------*/

	/**
	 * Sets a user variable.
	 * <p>
	 * This is equivalent to {@code <set>} in RiveScript. Set the value to {@code null} to delete a user variable.
	 *
	 * @param username the username
	 * @param name     the variable name
	 * @param value    the variable value
	 */
	public void setUservar(String username, String name, String value) {
		sessions.set(username, name, value);
	}

	/**
	 * Set a user's variables.
	 * <p>
	 * Set multiple user variables by providing a {@link Map} of key/value pairs.
	 * Equivalent to calling {@link #setUservar(String, String, String)} for each pair in the map.
	 *
	 * @param username the name
	 * @param vars     the user variables
	 */
	public void setUservars(String username, Map<String, String> vars) {
		sessions.set(username, vars);
	}

	/**
	 * Returns a user variable.
	 * <p>
	 * This is equivalent to {@code <get name>} in RiveScript. Returns {@code null} if the variable isn't defined.
	 *
	 * @param username the username
	 * @param name     the variable name
	 * @return the variable value
	 */
	public String getUservar(String username, String name) {
		return sessions.get(username, name);
	}

	/**
	 * Returns all variables for a user.
	 *
	 * @param username the username
	 * @return the variables
	 */
	public UserData getUservars(String username) {
		return sessions.get(username);
	}

	/**
	 * Clears all variables for all users.
	 */
	public void clearAllUservars() {
		this.sessions.clearAll();
	}

	/**
	 * Clears a user's variables.
	 *
	 * @param username the username
	 */
	public void clearUservars(String username) {
		sessions.clear(username);
	}

	/**
	 * Makes a snapshot of a user's variables.
	 *
	 * @param username the username
	 */
	public void freezeUservars(String username) {
		sessions.freeze(username);
	}

	/**
	 * Unfreezes a user's variables.
	 *
	 * @param username the username
	 * @param action   the thaw action
	 * @see ThawAction
	 */
	public void thawUservars(String username, ThawAction action) {
		sessions.thaw(username, action);
	}

	/**
	 * Returns a user's last matched trigger.
	 *
	 * @param username the username
	 * @return the last matched trigger
	 */
	public String lastMatch(String username) {
		return sessions.getLastMatch(username);
	}

	/**
	 * Returns the current user's ID.
	 * <p>
	 * This is only useful from within a (Java) object macro, to get the ID of the user who invoked the macro.
	 * This value is set at the beginning of {@link #reply(String, String)} and unset at the end, so this method will return {@code null}
	 * outside of a reply context.
	 *
	 * @return the user's ID or {@code null}
	 */
	public String currentUser() {
		return currentUser.get();
	}

	/*-----------------------*/
	/*-- Developer Methods --*/
	/*-----------------------*/

	/**
	 * Dumps the trigger sort buffers to the standard output stream.
	 */
	public void dumpSorted() {
		dumpSorted(sorted.getTopics(), "Topics");
		dumpSorted(sorted.getThats(), "Thats");
		dumpSortedList(sorted.getSub(), "Substitutions");
		dumpSortedList(sorted.getPerson(), "Person Substitutions");
	}

	private void dumpSorted(Map<String, List<SortedTriggerEntry>> tree, String label) {
		System.out.println("Sort buffer: " + label);
		for (Map.Entry<String, List<SortedTriggerEntry>> entry : tree.entrySet()) {
			String topic = entry.getKey();
			List<SortedTriggerEntry> data = entry.getValue();
			System.out.println("  Topic: " + topic);
			for (SortedTriggerEntry trigger : data) {
				System.out.println("    + " + trigger.getTrigger());
			}
		}
	}

	private void dumpSortedList(List<String> list, String label) {
		System.out.println("Sort buffer: " + label);
		for (String item : list) {
			System.out.println("  " + item);
		}
	}

	/**
	 * Dumps the entire topic/trigger/reply structure to the standard output stream.
	 */
	public void dumpTopics() {
		for (Map.Entry<String, Topic> entry : topics.entrySet()) {
			String topic = entry.getKey();
			Topic data = entry.getValue();
			System.out.println("Topic: " + topic);
			for (Trigger trigger : data.getTriggers()) {
				System.out.println("  + " + trigger.getTrigger());
				if (trigger.getPrevious() != null) {
					System.out.println("    % " + trigger.getPrevious());
				}
				for (String condition : trigger.getCondition()) {
					System.out.println("    * " + condition);
				}
				for (String reply : trigger.getReply()) {
					System.out.println("    - " + reply);
				}
				if (trigger.getRedirect() != null) {
					System.out.println("    @ " + trigger.getRedirect());
				}
			}
		}
	}

	/**
	 * Returns the topics.
	 *
	 * @return the topics
	 */
	public Map<String, Topic> getTopics() {
		return topics;
	}
}
