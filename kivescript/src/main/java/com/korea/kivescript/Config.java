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

package com.korea.kivescript;


import java.util.HashMap;
import java.util.Map;
import static com.korea.kivescript.ConcatMode.NONE;
import static com.korea.kivescript.MorphemeMode.NONE_SEPARATION;
import com.korea.kivescript.session.SessionManager;


/**
 * User-configurable properties of the {@link RiveScript} interpreter.
 *
 * @author Noah Petherbridge
 * @author Marcel Overdijk
 */
/**
 * RiveScript 인터프리터의 사용자설정 프로퍼티
 * 빌더 패턴으로 Config 인스턴스를 생성한다.
 * MorphemeMode - 형태소분리 모드 필드추가.
 * @author yun-yeoseong
 */
public class Config {

	/**
	 * The default concat mode.
	 * 기본 concat 모드 - NONE("")
	 */
	public static final ConcatMode DEFAULT_CONCAT = NONE;
	
	/**
	 * 기본 형태소분리 모드 - NONE_SEPARATION
	 */
	public static final MorphemeMode DEFAULT_MORPHEME = NONE_SEPARATION;
	
	/**
	 * The default recursion depth limit.
	 * 기본 재귀 탐색 깊이 한계값. - DEFAULT_DEPTH = 50
	 */
	public static final int DEFAULT_DEPTH = 50;

	/**
	 * The default unicode punctuation pattern.
	 * 기본 유니코드 구두점 패턴식.
	 */
	public static final String DEFAULT_UNICODE_PUNCTUATION_PATTERN = "[.,!?;:]";

	private boolean throwExceptions;
	private boolean strict;
	private boolean utf8;
	private String unicodePunctuation = DEFAULT_UNICODE_PUNCTUATION_PATTERN;
	private boolean forceCase;
	private ConcatMode concat = DEFAULT_CONCAT;
	//형태소분리 여부
	private MorphemeMode morpheme = DEFAULT_MORPHEME;
	private int depth = DEFAULT_DEPTH;
	private SessionManager sessionManager;
	private Map<String, String> errorMessages;

	protected Config() {
	}

	/**
	 * Returns whether exception throwing is enabled.
	 * 예외 처리 사용 여부를 반환.
	 * @return whether exception throwing is enabled
	 */
	public boolean isThrowExceptions() {
		return throwExceptions;
	}

	/**
	 * Returns whether strict syntax checking is enabled.
	 * 규칙이 엄격한 구문 검사 사용 여부를 반환.
	 *
	 * @return whether strict syntax checking is enabled
	 */
	public boolean isStrict() {
		return strict;
	}

	/**
	 * Returns whether UTF-8 mode is enabled for user messages and triggers.
	 * 사용자메시지와 트리거를 UTF-8 인코딩으로 사용할지 여부.
	 * @return whether UTF-8 mode is enabled for user messages and triggers
	 */
	public boolean isUtf8() {
		return utf8;
	}

	/**
	 * Returns the unicode punctuation pattern.
	 * 유니코드 구두점 패턴식을 반환한다.
	 * @return the unicode punctuation pattern
	 */
	public String getUnicodePunctuation() {
		return unicodePunctuation;
	}

	/**
	 * Returns whether forcing triggers to lowercase is enabled.
	 * 강제로 소문자 치환 트리거를 사용하는지 여부 반환.
	 * @return whether forcing triggers to lowercase is enabled
	 */
	public boolean isForceCase() {
		return forceCase;
	}

	/**
	 * Returns the concat mode.
	 * concat 모드를 반환.
	 * @return the concat mode
	 */
	public ConcatMode getConcat() {
		return concat;
	}
	/**
	 * 형태소분리 모드를 반환.
	 * @return the morpheme mode.
	 */
	public MorphemeMode getMorpheme() {
		return morpheme;
	}
	/**
	 * Returns the recursion depth limit.
	 * 재귀 탐색 리밋 값 반환.
	 * @return the recursion depth limit
	 */
	public int getDepth() {
		return depth;
	}

	/**
	 * Returns the {@link SessionManager} for user variables.
	 * 사용자 변수를 위한 SessionManager 객체를 반환.
	 * @return the session manager for user variables
	 */
	public SessionManager getSessionManager() {
		return sessionManager;
	}

	/**
	 * Returns the custom error message overrides.
	 * 사용자 정의 에러 메시지를 반환.
	 * @return the custom error message overrides
	 */
	public Map<String, String> getErrorMessages() {
		return errorMessages;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		Config that = (Config) o;
		if (throwExceptions != that.throwExceptions) {
			return false;
		}
		if (strict != that.strict) {
			return false;
		}
		if (utf8 != that.utf8) {
			return false;
		}
		if (forceCase != that.forceCase) {
			return false;
		}
		if (concat != that.concat) {
			return false;
		}
		if (depth != that.depth) {
			return false;
		}
		if (unicodePunctuation != null ? !unicodePunctuation.equals(that.unicodePunctuation) : that.unicodePunctuation != null) {
			return false;
		}
		if (sessionManager != null ? !sessionManager.equals(that.sessionManager) : that.sessionManager != null) {
			return false;
		}
		return errorMessages != null ? errorMessages.equals(that.errorMessages) : that.errorMessages == null;
	}

	@Override
	public int hashCode() {
		int result = (throwExceptions ? 1 : 0);
		result = 31 * result + (strict ? 1 : 0);
		result = 31 * result + (utf8 ? 1 : 0);
		result = 31 * result + (unicodePunctuation != null ? unicodePunctuation.hashCode() : 0);
		result = 31 * result + (forceCase ? 1 : 0);
		result = 31 * result + (concat != null ? concat.hashCode() : 0);
		result = 31 * result + depth;
		result = 31 * result + (sessionManager != null ? sessionManager.hashCode() : 0);
		result = 31 * result + (errorMessages != null ? errorMessages.hashCode() : 0);
		return result;
	}

	@Override
	public String toString() {
		return "Config{" +
				"throwExceptions=" + throwExceptions +
				", strict=" + strict +
				", utf8=" + utf8 +
				", unicodePunctuation='" + unicodePunctuation + '\'' +
				", forceCase=" + forceCase +
				", concat=" + concat +
				", morpheme="+ morpheme +
				", depth=" + depth +
				", sessionManager=" + sessionManager +
				", errorMessages=" + errorMessages +
				'}';
	}

	/**
	 * Converts this {@link Config} instance to a {@link Builder}.
	 * Config 인스턴스를 빌더 객체로 변환 후 반환.
	 * @return the builder
	 */
	public Builder toBuilder() {
		return newBuilder()
				.throwExceptions(this.throwExceptions)
				.strict(this.strict)
				.utf8(this.utf8)
				.unicodePunctuation(this.unicodePunctuation)
				.forceCase(this.forceCase)
				.concat(this.concat)
				.morpheme(this.morpheme)
				.depth(this.depth)
				.sessionManager(this.sessionManager)
				.errorMessages(this.errorMessages);
	}

	/**
	 * Creates a basic {@link Config}. A basic config has all the defaults, plus {@code strict = true}.
	 * 기본 생성. 기본 생성 설정은 모두 기본값을 갖으며, strict = true 설정을 더한다.
	 * @return the config
	 */
	public static Config basic() {
		return Builder.basic().build();
	}

	/**
	 * Creates a basic {@link Config} with UTF-8 mode enabled.
	 *
	 * @return the config
	 */
	public static Config utf8() {
		return Builder.utf8().build();
	}

	/**
	 * Creates a new {@link Builder}.
	 *
	 * @return the builder
	 */
	public static Builder newBuilder() {
		return new Builder();
	}

	/**
	 * Builder for {@link Config}.
	 */
	public static final class Builder {

		private boolean throwExceptions;
		private boolean strict;
		private boolean utf8;
		private String unicodePunctuation = DEFAULT_UNICODE_PUNCTUATION_PATTERN;
		private boolean forceCase;
		private ConcatMode concat = DEFAULT_CONCAT;
		private MorphemeMode morpheme = DEFAULT_MORPHEME;
		private int depth = DEFAULT_DEPTH;
		private SessionManager sessionManager;
		private Map<String, String> errorMessages;

		private Builder() {
		}

		/**
		 * Sets whether exception throwing is enabled.
		 *
		 * @param throwExceptions whether exception throwing is enabled
		 * @return this builder
		 */
		public Builder throwExceptions(boolean throwExceptions) {
			this.throwExceptions = throwExceptions;
			return this;
		}

		/**
		 * Sets whether strict syntax checking is enabled.
		 *
		 * @param strict whether strict syntax checking is enabled
		 * @return this builder
		 */
		public Builder strict(boolean strict) {
			this.strict = strict;
			return this;
		}

		/**
		 * Sets whether UTF-8 mode is enabled.
		 *
		 * @param utf8 whether UTF-8 is enabled
		 * @return this builder
		 */
		public Builder utf8(boolean utf8) {
			this.utf8 = utf8;
			return this;
		}

		/**
		 * Sets the unicode punctuation pattern (only used when UTF-8 mode is enabled).
		 *
		 * @param unicodePunctuation the unicode punctuation pattern
		 * @return this builder
		 */
		public Builder unicodePunctuation(String unicodePunctuation) {
			this.unicodePunctuation = unicodePunctuation;
			return this;
		}

		/**
		 * Sets whether forcing triggers to lowercase is enabled.
		 *
		 * @param forceCase whether forcing triggers to lowercase is enabled
		 * @return this builder
		 */
		public Builder forceCase(boolean forceCase) {
			this.forceCase = forceCase;
			return this;
		}

		/**
		 * Sets the concat mode.
		 *
		 * @param concat the concat mode
		 * @return this builder
		 */
		public Builder concat(ConcatMode concat) {
			this.concat = concat;
			return this;
		}
		
		/**
		 * 형태소분리 모드를 설정한다.
		 * 
		 * @param morpheme 형태소분리 모드
		 * @return this builder
		 */
		public Builder morpheme(MorphemeMode morpheme) {
			this.morpheme = morpheme;
			return this;
		}

		/**
		 * Sets the recursion depth limit.
		 *
		 * @param depth the recursion depth limit
		 * @return this builder
		 */
		public Builder depth(int depth) {
			this.depth = depth;
			return this;
		}

		/**
		 * Sets the {@link SessionManager} for user variables.
		 *
		 * @param sessionManager the session manager
		 * @return this builder
		 */
		public Builder sessionManager(SessionManager sessionManager) {
			this.sessionManager = sessionManager;
			return this;
		}

		/**
		 * Sets the custom error message overrides.
		 *
		 * @param errorMessages the custom error message overrides
		 * @return this builder
		 */
		public Builder errorMessages(Map<String, String> errorMessages) {
			this.errorMessages = errorMessages;
			return this;
		}

		/**
		 * Adds a custom error message override.
		 *
		 * @param key   the key of the error message
		 * @param value the custom error message
		 * @return this builder
		 */
		public Builder addErrorMessage(String key, String value) {
			if (this.errorMessages == null) {
				this.errorMessages = new HashMap<>();
			}
			this.errorMessages.put(key, value);
			return this;
		}

		/**
		 * Builds the config.
		 *
		 * @return the config
		 */
		public Config build() {
			Config config = new Config();
			config.throwExceptions = this.throwExceptions;
			config.strict = this.strict;
			config.utf8 = this.utf8;
			config.unicodePunctuation = this.unicodePunctuation;
			config.forceCase = this.forceCase;
			config.concat = this.concat;
			config.morpheme = this.morpheme;
			config.depth = this.depth;
			config.sessionManager = this.sessionManager;
			config.errorMessages = this.errorMessages;
			return config;
		}

		/**
		 * Creates a basic {@link Config.Builder}. A basic builder has all the defaults, plus {@code strict = true}.
		 *
		 * @return the builder
		 */
		public static Builder basic() {
			return new Builder().strict(true);
		}

		/**
		 * Creates a basic {@link Config.Builder} with UTF-8 mode enabled.
		 *
		 * @return the builder
		 */
		public static Builder utf8() {
			return basic().utf8(true);
		}
	}
}
