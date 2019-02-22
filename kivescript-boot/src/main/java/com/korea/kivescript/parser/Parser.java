/*
 * Copyright (c) 2016-2017 the original author or authors.
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

package com.korea.kivescript.parser;

import com.korea.kivescript.ConcatMode;
import com.korea.kivescript.Config;
import com.korea.kivescript.MorphemeMode;
import com.korea.kivescript.ast.ObjectMacro;
import com.korea.kivescript.ast.Root;
import com.korea.kivescript.ast.Trigger;
import com.korea.kivescript.util.StringUtils;

import rnb.analyzer.nori.analyzer.NoriAnalyzer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parser for RiveScript source code.
 *
 * @author Noah Petherbridge
 * @author Marcel Overdijk
 */
public class Parser {

	/**
	 * The supported version of the RiveScript language.
	 */
	public static final double RS_VERSION = 2.0;
	
	private static Logger logger = LoggerFactory.getLogger(Parser.class);

	private boolean strict;
	private boolean utf8;
	private boolean forceCase;
	private ConcatMode concat;
	private MorphemeMode morpheme;

	/**
	 * Creates a new {@link Parser} with a default {@link ParserConfig}.
	 */
	public Parser() {
		this(null);
	}

	/**
	 * Creates a new {@link Parser} with the given {@link ParserConfig}.
	 *
	 * @param config the config
	 */
	public Parser(ParserConfig config) {
		if (config == null) {
			config = new ParserConfig();
		}
		this.strict = config.isStrict();
		this.utf8 = config.isUtf8();
		this.forceCase = config.isForceCase();
		this.concat = config.getConcat();
		this.morpheme = config.getMorpheme();
	}

	/**
	 * Parses the RiveScript source code.
	 * RiveScript 스크립트 코드를 파싱한다.
	 * <p>
	 * This will return an Abstract Syntax Tree {@link Root} object containing all of the relevant information parsed from the source code.
	 * 스크립트 코드로 부터 파싱된 관련된 모든 정보를 담고 있는 추상구문트리의 {@link Root} 객체를 반환한다. 
	 * <p>
	 * In case of errorMessages (e.g. a syntax error when strict mode is enabled) a {@link ParserException} will be thrown.
	 * strict mode를 활성화 한다면 구문에러시 {@link ParserException}를 발생시킨다.
	 * @param filename the arbitrary name for the source code being parsed
	 * @param code     the lines of RiveScript source code
	 * @return the AST root object
	 * @throws ParserException in case of a parsing error
	 */
	public Root parse(String filename, String[] code) throws ParserException {

		logger.debug("Parsing {}", filename);

		if (logger.isTraceEnabled()) {
			for (String line : code) {
				logger.trace("{}", line);
			}
		}

		long startTime = System.currentTimeMillis();

		// Create the root Abstract Syntax Tree (AST).
		// 추상구문트리 루트를 생성한다.
		Root ast = new Root();

		// Track temporary variables.
		/**
		 * 추상구문트리를 구성하는 변수들 초기화
		 */
		String topic = "random";           // Default topic = random,기본 토픽은 랜덤이다.
		int lineno = 0;                    // Line numbers for syntax tracking, 구문추적을 위한 줄번호.
		boolean inComment = false;         // In a multi-line comment ,여러줄 주석 안에 포함된 구문인가
		boolean inObject = false;          // In an object macro, Object macro 작성중인가
		String objectName = null;          // The name of the current object, 현재 개체 이름.
		String objectLanguage = null;      // The programming language of the current object,현재 개체 매크로의 프로그래밍 언어.
		List<String> objectBuffer = null;  // The source code buffer of the current object, 현재 개체 매크로의 코드버퍼.
		Trigger currentTrigger = null;     // The current trigger, 현재 트리거.
		String previous = null;            // The a %Previous trigger, 이전 트리거.
		
		/**
		 * ! local 
		 * 데이터에서 설정될 로컬 옵션이다.
		 */
		Map<String, String> localOptions = new HashMap<>();

		// Go through the lines of code.
		for (int lp = 0; lp < code.length; lp++) {
			//현재 코드의 다음 라인숫자
			lineno = lp + 1;

			// Strip the line.
			String line = code[lp].trim();
			
			// 공백은 생략한다.
			if (line.length() == 0) {
				continue; // Skip blank lines!
			}

			// Are we inside an `> object`?
			/**
			 * 현재 object macro 코드를 처리중인가?
			 */
			if (inObject) {
				// End of the object?
				/**
				 * 개체의 끝인가?
				 * 개체의 끝 태그라면, ObjectMacro 객체를 생성하고, 필요정보를 설정한 후에 추상구문트리에 추가한다. 
				 */
				if (line.contains("< object") || line.contains("<object")) {
					if (objectName != null && objectName.length() > 0) {
						ObjectMacro object = new ObjectMacro();
						object.setName(objectName);
						object.setLanguage(objectLanguage);
						object.setCode(objectBuffer);
						ast.addObject(object);
					}
					/**
					 * 이미 추상구문트리에 들어갔음으로 변수 초기화
					 */
					inObject = false;
					objectName = null;
					objectLanguage = null;
					objectBuffer = null;
				} else {
					/**
					 * 추가할 개체 매크로 코드가 남았다면 계속 버퍼에 넣는다
					 */
					objectBuffer.add(line);
				}
				continue;
			}

			/**
			 * 주석문은 파싱에서 제외
			 */
			if (line.startsWith("//")) {
				continue; // Single line comment.
			} else if (line.startsWith("/*")) {
				// Start of a multi-line comment.
				if (line.contains("*/")) {
					continue; // The end comment is on the same line!
				}
				// We're now inside a multi-line comment.
				inComment = true;
				continue;
			} else if (line.contains("*/")) {
				// End of a multi-line comment.
				inComment = false;
				continue;
			} else if (inComment) {
				continue;
			}

			// Separate the command from its data.
			// 데이터로 부터 명령어(트리거)를 분리한다.
			// 데이터의 길이가 2보다작다면 파싱하지 않고 warn log 출력.
			if (line.length() < 2) {
				logger.warn("Weird single-character line '{}' at {} line {}", line, filename, lineno);
				continue;
			}
			/**
			 * 라인데이터에서 트리거와 데이터 분리.
			 */
			String cmd = line.substring(0, 1);
			line = line.substring(1);

			/**
			 * 만약 제거한 트리거 이후에도 주석이 존재한다면 주석은 파싱에서 생략한다.
			 */
			if (line.contains(" // ")) {
				line = line.substring(0, line.indexOf(" // "));
			}
			
			line = line.trim();

			/**
			 * LowerCase가 적용되어 있고, 현재 트리거가 "+"라면 데이터 소문자로 모두 치환.
			 * LowerCaseFilter 적용전에 만약 형태소분리 모드라면 먼저 데이터 전처리후
			 * Filter 처리.
			 * (사실 전처리과정에 LowerCase가 적용됨. 나중에 적절히 둘중하나를 제거할 필요 있음.)
			 */
			if (forceCase && cmd.equals("+")) {
				/**
				 * 만약 형태소분리 모드가 되어있다면 "+" 트리거에 대해 형태소분리 작업을 들어간다.
				 * By yeoseong_yoon
				 */
				if(this.morpheme.equals(MorphemeMode.SEPARATION)) {
					
					NoriAnalyzer analyzer = new NoriAnalyzer();
					
					line = analyzer.analyzeForString(line);
					
					logger.info("Morpheme knowledge :::: {}",line);
					
				}
				
				line = line.toLowerCase();
			}

			logger.debug("Cmd: {}; line: {}", cmd, line);

			/**
			 * 구문체크.
			 */
			try {
				checkSyntax(cmd, line);
			} catch (ParserException e) {
				/**
				 * strict를 활성화하였다면 구문체크에서 오류가 있을시에
				 * 예외를 발생시킨다.
				 */
				if (strict) {
					throw e; // Simply rethrow the parser exception.
				} else {
					logger.warn("Syntax error '{}' at {} line {}", e.getMessage(), filename, lineno);
				}
			}

			/**
			 * "+"트리거라면 이전 상태를 초기화해준다.
			 */
			if (cmd.equals("+")) {
				previous = null;
			}

			/**
			 * "^","%" 트리거를 위해 다음 스크립트 코드를 미리 들여다본다.
			 */
			if (!cmd.equals("^")) {
				/**
				 * 현재 라인 + 1 트리거부터 조회한다.
				 */
				for (int li = (lp + 1); li < code.length; li++) {
					String lookahead = code[li].trim();
					if (lookahead.length() < 2) {
						continue;
					}
					
					/**
					 * 다음 데이터 트리거와 데이터 분리.
					 */
					String lookCmd = lookahead.substring(0, 1);
					lookahead = lookahead.substring(1).trim();

					/**
					 * 만약 "%","^" 트리거가 아니라면 반복문을 벗어난다.
					 */
					if (!lookCmd.equals("%") && !lookCmd.equals("^")) {
						break;
					}

					/**
					 * 데이터가 존재하지 않는다면 반복문을 벗어난다.
					 */
					if (lookahead.length() == 0) {
						break;
					}

					logger.debug("\tLookahead {}: {} {}", li, lookCmd, lookahead);

					/**
					 * 만약 현재 트리거가 "+"이고, 다음 트리거가 "%"라면,
					 * 이전 상태에 다음 "%" 트리거의 데이터를 설정해준다.
					 */
					if (cmd.equals("+")) {
						if (lookCmd.equals("%")) {
							previous = lookahead;
							break;
						} else {
							previous = null;
						}
					}

					/**
					 * 현재 트리거가 "!"이고, 다음 트리거가 "^"라면,
					 * 줄바꿈을 한줄로 "<crlf>"구분자로 설정을 엮어준다.
					 */
					if (cmd.equals("!")) {
						if (lookCmd.equals("^")) {
							line += "<crlf>" + lookahead;
						}
						continue;
					}

					/**
					 * 현재 트리거가 "^"가 아니면서 다음 트리거가 "%"가 아닐때,
					 * 즉, 현재 트리거가 "-"이고, 다음 트리거가 "^"일 경우.
					 */
					if (!cmd.equals("^") && !lookCmd.equals("%")) {
						if (lookCmd.equals("^")) {
							// Which character to concatenate with?
							ConcatMode concat = null;
							if (localOptions.containsKey("concat")) {
								concat = ConcatMode.fromName(localOptions.get("concat"));
							}
							if (concat == null) {
								concat = this.concat != null ? this.concat : Config.DEFAULT_CONCAT;
							}
							line += concat.getConcatChar() + lookahead;
						}
					}
				}
			}

			// Handle the types of RiveScript commands.
			// RiveScript의 트리거 유형을 핸들링한다.
			switch (cmd) {

				case "!": { // ! Define
					
					//ex) ! version = 1.0 -> [! version ,  1.0]
					String[] halves = line.split("=", 2);
					//ex) ! version -> [!, version]
					String[] left = halves[0].trim().split(" ", 2);
					String value = "";
					String kind = ""; // global, var, sub, ...
					String name = "";
					if (halves.length == 2) {
						//ex) value = 1.0
						value = halves[1].trim();
					}
					if (left.length >= 1) {
						//ex) kind = !
						kind = left[0].trim();
						if (left.length >= 2) {
							left = Arrays.copyOfRange(left, 1, left.length);
							name = StringUtils.join(left, " ").trim();
						}
					}

					// Remove 'fake' line breaks unless this is an array.
					// 배열이 아니라면 줄바꿈을 제거.
					if (!kind.equals("array")) {
						value = value.replaceAll("<crlf>", "");
					}

					// Handle version numbers.
					// 만약 ! 종류가 버젼이라면.
					if (kind.equals("version")) {
						double parsedVersion = 0;
						try {
							parsedVersion = Double.parseDouble(value);
						} catch (NumberFormatException e) {
							logger.warn("RiveScript version '{}' at {} line {} is not a valid floating number", value, filename, lineno);
						}
						if (parsedVersion > RS_VERSION) {
							throw new ParserException(
									String.format("Unsupported RiveScript version at %s line %d. We only support %s", filename, lineno,
											RS_VERSION));
						}
						continue;
					}

					// All other types of define's require a value and a variable name.
					if (name.length() == 0) {
						logger.warn("Undefined variable name at {} line {}", filename, lineno);
						continue;
					}
					if (value.length() == 0) {
						logger.warn("Undefined variable value at {} line {}", filename, lineno);
						continue;
					}

					// Handle the rest of the !Define types.
					switch (kind) {

						case "local": {
							// Local file-scoped parser options.
							// 로컬 파일범위 파서옵션 설정.
							logger.debug("\tSet local parser option {} = {}", name, value);
							localOptions.put(name, value);
							break;
						}
						case "global": {
							// Set a 'global' variable.
							// Begin객체에 글로벌 변수 설정.
							logger.debug("\tSet global {} = {}", name, value);
							ast.getBegin().addGlobal(name, value);
							break;
						}
						case "var": {
							// Set a bot variable.
							// Begin객체에 변수설정
							logger.debug("\tSet bot variable {} = {}", name, value);
							ast.getBegin().addVar(name, value);
							break;
						}
						case "array": {
							// Set an array.
							// Begin객체에 배열설정(동의어 같은 것인가?)
							logger.debug("\tSet array {} = {}", name, value);

							/**
							 * "^"로 정의된 배열일 수도 있다.
							 */
							String[] parts = value.split("<crlf>");

							// Process each line of array data.
							List<String> fields = new ArrayList<>();
							for (String val : parts) {
								if (val.contains("|")) {
									fields.addAll(Arrays.asList(val.split("\\|")));
								} else {
									fields.addAll(Arrays.asList(val.split("\\s+")));
								}
							}

							// Convert any remaining \s's over.
							for (int i = 0; i < fields.size(); i++) {
								fields.set(i, fields.get(i).replaceAll("\\\\s", " "));
							}

							ast.getBegin().addArray(name, fields);
							break;
						}
						case "sub": {
							// Substitutions.
							// 대체어를 Begin 객체에 설정한다.
							logger.debug("\tSet substitution {} = {}", name, value);
							ast.getBegin().addSub(name, value);
							break;
						}
						case "person": {
							// Person substitutions.
							logger.debug("\tSet person substitution {} = {}", name, value);
							ast.getBegin().addPerson(name, value);
							break;
						}
						default:
							logger.warn("Unknown definition type '{}' found at {} line {}", kind, filename, lineno);
					}
					break;
				}
				case ">": { 
					/**
					 * 1)topic name
					 * 2)object name language
					 * 3)begin
					 */
					String[] temp = line.trim().split(" ");
					String kind = temp[0];
					temp = Arrays.copyOfRange(temp, 1, temp.length);
					String name = "";
					String[] fields = new String[0];
					if (temp.length > 0) {
						name = temp[0];
						temp = Arrays.copyOfRange(temp, 1, temp.length);
					}
					/**
					 * 만약 토픽이 다른 토픽을 포함&상속하고 있다면
					 * temp에는 아직 데이터가 남아있다.
					 */
					if (temp.length > 0) {
						fields = temp;
					}

					/**
					 * "> begin"은 결국 "__begin__"이라는 토픽으로 치환된다. 
					 */
					if (kind.equals("begin")) {
						logger.debug("Found the BEGIN block at {} line {}", filename, lineno);
						kind = "topic";
						name = "__begin__";
					}
					if (kind.equals("topic")) {
						// Force case on topics.
						if (forceCase) {
							name = name.toLowerCase();
						}

						logger.debug("Set topic to {}", name);
						currentTrigger = null;
						topic = name;

						/**
						 * "topic"이라면 해당 name으로 토픽트리를 초기화한다.
						 */
						ast.addTopic(topic);

						/**
						 * 다른 토픽을 포함하고 있거나 상속받고 있다면
						 * 방금 초기화한 토픽트리에 includes 혹은 inherits를 설정해준다.
						 */
						String mode = "";
						if (fields.length >= 2) {
							for (String field : fields) {
								if (field.equals("includes") || field.equals("inherits")) {
									mode = field;
								} else if (mode.equals("includes")) {
									ast.getTopic(topic).addInclude(field, true);
								} else if (mode.equals("inherits")) {
									ast.getTopic(topic).addInherit(field, true);
								}
							}
						}
					} else if (kind.equals("object")) {
						/**
						 * fields가 존재한다면 이 fields는 프로그래밍언어가 문자열로 들어있다.
						 */
						String language = "";
						if (fields.length > 0) {
							language = fields[0].toLowerCase();
						}

						/**
						 * ObjectMacro 설정을 위한 변수세팅.
						 */
						objectName = name;
						objectLanguage = language;
						objectBuffer = new ArrayList<>();
						inObject = true;

						// Missing language?
						//언어명이 명시되지 않았다면 __unknown__ 설정
						if (language.equals("")) {
							logger.warn("No programming language specified for object '{}' at {} line", name, filename, lineno);
							objectLanguage = "__unknown__";
							continue;
						}
					} else {
						logger.warn("Unknown label type '{}' at {} line {}", kind, filename, lineno);
					}
					break;
				}
				case "<": { // < Label
					String kind = line;
					if (kind.equals("begin") || kind.equals("topic")) {
						logger.debug("\tEnd the topic label.");
						topic = "random"; // Go back to default topic.
					} else if (kind.equals("object")) {
						logger.debug("\tEnd the object label.");
						inObject = false;
					} else {
						logger.warn("Unknown end topic type '{}' at {} line {}", kind, filename, lineno);
					}
					break;
				}
				case "+": { // + Trigger
					//+ 트리거는 토픽 단위로 추가된다.
					logger.debug("\tTrigger pattern: {}", line);

					/**
					 * 트리거트리를 초기화한다.
					 */
					currentTrigger = new Trigger();
					currentTrigger.setTrigger(line);
					currentTrigger.setPrevious(previous);
					ast.getTopic(topic).addTrigger(currentTrigger);
					break;
				}
				case "-": { // - Response
					/**
					 * 현재 "+"트리거가 존재하지 않은 상태에서
					 * "-"가 나온다면 예외 발생
					 * 반드시 "+" 뒤에 과정 중에서 "-"가 따라온다.
					 */
					if (currentTrigger == null) {
						logger.warn("Response found before trigger at {} line {}", filename, lineno);
						continue;
					}

					/**
					 * "@" srai와 "-"는 섞어서 사용할 수 없다.
					 */
					if (currentTrigger.getRedirect() != null) {
						logger.warn("You can't mix @Redirects with -Replies at {} line {}", filename, lineno);
					}

					logger.debug("\tResponse: {}", line);
					currentTrigger.addReply(line);
					break;
				}
				case "*": { // * Condition
					/**
					 * "-"와 동일하게 "*"는 반드시 "+"이후에 나와야한다.
					 */
					if (currentTrigger == null) {
						logger.warn("Condition found before trigger at {} line {}", filename, lineno);
						continue;
					}

					logger.debug("\tCondition: {}", line);
					currentTrigger.addCondition(line);
					break;
				}
				case "%": { // % Previous
					// This was handled above.
					continue;
				}
				case "^": { // ^ Continue
					// This was handled above.
					continue;
				}
				case "@": { // @ Redirect
					/**
					 * "-"와 동일하게 "@"는 반드시 "+"이후에 나와야한다.
					 */
					if (currentTrigger == null) {
						logger.warn("Redirect found before trigger at {} line {}", filename, lineno);
						continue;
					}

					logger.debug("\tRedirect response to: {}", line);
					currentTrigger.setRedirect(line);
					break;
				}
				default:
					logger.warn("Unknown command '{}' found at {} line {}", cmd, filename, lineno);
			}
		}

		if (logger.isDebugEnabled()) {
			long elapsedTime = System.currentTimeMillis() - startTime;
			logger.debug("Parsing {} completed in {} ms", filename, elapsedTime);
		}

		return ast;
	}

	/**
	 * Checks the syntax of a RiveScript command.
	 * RiveScript의 트리거 구문 체크 메소드.
	 * @param cmd  the single character command symbol, 트리거 심볼(기호)
	 * @param line the rest of the line after the command, 트리거 기호를 제외한 라인데이터.
	 * @throws ParserException in case of syntax error
	 */
	private void checkSyntax(String cmd, String line) throws ParserException {
		// Run syntax tests based on the command used.
		if (cmd.equals("!")) {
			/**
			 * "!" 트리거는 반드시 밑의 포맷의 구문과 일치해야한다.
			 * 1) ! type name = value
			 * 2) ! type = value
			 */
			if (!line.matches("^(version|local|global|var|array|sub|person)(?:\\s+.+|)\\s*=\\s*.+?$")) {
				throw new ParserException("Invalid format for !Definition line: must be '! type name = value' OR '! type = value'");
			} else if (line.matches("^array")) {
				if (line.matches("\\=\\s?\\||\\|\\s?$")) {
					throw new ParserException("Piped arrays can't begin or end with a |");
				} else if (line.matches("\\|\\|")) {
					throw new ParserException("Piped arrays can't include blank entries");
				}
			}
		} else if (cmd.equals(">")) {
			/**
			 * ">" 트리거는 반드시 밑의 포맷의 구문과 일치해야한다.
			 * 1) "begin"은 반드시 "begin"이라는 문자 하나만 와야한다.
			 * 2) "topic"은 반드시 소문자로만 구성되어야한다. 그리고 다른 토픽을 상속받을 수 있다.
			 * 3) "object"은 "topic"과 동일한 규칙을 갖지만 반드시 소문자는 아니어도 된다.(문자와 숫자만 포함가능)
			 */
			String[] parts = line.split("\\s+");
			if (parts[0].equals("begin") && parts.length > 1) {
				throw new ParserException("The 'begin' label takes no additional arguments");
			} else if (parts[0].equals("topic")) {
				if (!forceCase && line.matches("[^a-z0-9_\\-\\s]")) {
					throw new ParserException("Topics should be lowercased and contain only letters and numbers");
				} else if (line.matches("[^A-Za-z0-9_\\-\\s]")) {
					throw new ParserException("Topics should contain only letters and numbers in forceCase mode");
				}
			} else if (parts[0].equals("object")) {
				if (line.matches("[^A-Za-z0-9\\_\\-\\s]")) {
					throw new ParserException("Objects can only contain numbers and letters");
				}
			}
		} else if (cmd.equals("+") || cmd.equals("%") || cmd.equals("@")) {
			/**
			 * + Trigger, % Previous, % Redirect
			 * 위의 트리거들은 엄격한 구문 체크를 가진다.
			 * 이 트리거들은 모두 패턴검사 엔진을 통해서 구문분석이 이루어지고, 
			 * 반드시 패턴검사 엔진에서 통과되어야만 한다.
			 * 
			 * 해당 트리거에서는 대문자와 백슬래시, "."은 포함될 수 없다.(UTF-8일때)
			 * ( | ) [ ] * _ # { } < > = 기호들은 사용할 수 있다.
			 * 괄호 수 체크를 엄격하게 진행한다.
			 */
			int parens = 0, square = 0, curly = 0, angle = 0; 

			if (utf8) {
				if (line.matches("[A-Z\\\\.]")) {
					throw new ParserException("Triggers can't contain uppercase letters, backslashes or dots in UTF-8 mode");
				}
			} else if (line.matches("[^a-z0-9(|)\\[\\]*_#@{}<>=\\/\\s]")) {
				throw new ParserException(
						"Triggers may only contain lowercase letters, numbers, and these symbols: ( | ) [ ] * _ # { } < > = /");
			} else if (line.matches("\\(\\||\\|\\)")) {
				throw new ParserException("Piped alternations can't begin or end with a |");
			} else if (line.matches("\\([^\\)].+\\|\\|.+\\)")) {
				throw new ParserException("Piped alternations can't include blank entries");
			} else if (line.matches("\\[\\||\\|\\]")) {
				throw new ParserException("Piped optionals can't begin or end with a |");
			} else if (line.matches("\\[[^\\]].+\\|\\|.+\\]")) {
				throw new ParserException("Piped optionals can't include blank entries");
			}

			/**
			 * 괄호 짝 체크
			 */
			for (char c : line.toCharArray()) {
				switch (c) {
					case '(':
						parens++;
						break;
					case ')':
						parens--;
						break;
					case '[':
						square++;
						break;
					case ']':
						square--;
						break;
					case '{':
						curly++;
						break;
					case '}':
						curly--;
						break;
					case '<':
						angle++;
						break;
					case '>':
						angle--;
						break;
				}
			}

			/**
			 * 괄호의 짝이 맞는지 체크한다.
			 */
			if (parens != 0) {
				throw new ParserException("Unmatched parenthesis brackets");
			}
			if (square != 0) {
				throw new ParserException("Unmatched square brackets");
			}
			if (curly != 0) {
				throw new ParserException("Unmatched curly brackets");
			}
			if (angle != 0) {
				throw new ParserException("Unmatched angle brackets");
			}
		} else if (cmd.equals("*")) {
			/**
			 * "*" 조건 트리거는 밑의 포맷을 따라야한다.
			 * ex) * <get name> ==  undefined => 답변
			 *     -> * value symbol value => response
			 */
			if (!line.matches("^.+?\\s*(?:==|eq|!=|ne|<>|<|<=|>|>=)\\s*.+?=>.+?$")) {
				throw new ParserException("Invalid format for !Condition: should be like '* value symbol value => response'");
			}
		}
	}
}
