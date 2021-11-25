package neoe.formatter.lua;

import java.util.Arrays;
import java.util.List;

public class LuaTokens {

	private String txt;
	private int p;
	private LuaTokenType type;

	public LuaTokens(String txt) {
		this.txt = txt;
		this.p = 0;
	}

	StringBuilder sb = new StringBuilder();

	public TypeAndValue next() {
		if (p >= txt.length())
			return null;

		sb.setLength(0);
		char c = txt.charAt(p);
		if (isSpace(c)) {
			type = LuaTokenType.SPACE;
			sb.append(c);
			p++;
			char c2;
			while (p < txt.length()) {
				if (isSpace(c2 = txt.charAt(p))) {
					sb.append(c2);
					p++;
				} else {
					break;
				}
			}
			return submit(type, sb.toString());
		} else if (isIdentifier(c) || (/* negtive number */c == '-' && Character.isDigit(peek(1)))) {
			type = LuaTokenType.IDENTIFIER;
			sb.append(c);
			p++;
			char c2;
			while (p < txt.length()) {
				if (isIdentifier(c2 = txt.charAt(p))) {
					sb.append(c2);
					p++;
				} else {
					break;
				}
			}
			return submit(type, sb.toString());
		} else if (c == '-' && peek(1) == '-') {
			type = LuaTokenType.COMMENT;
			sb.append("--");
			p += 2;
			int level = peekLongBrackets();
			if (level < 0) {
				readUntil("\n");
				if (p < txt.length()) {
					sb.setLength(sb.length() - 1);
					p--;
				}
			} else {
				readUntilLongBrackets(level);
			}

			return submit(type, sb.toString());
		} else if (c == '\'' || c == '"') {
			type = LuaTokenType.STRING;
			sb.append(c);
			p++;
			while (true) {
				if (p >= txt.length())
					break;
				char c2 = txt.charAt(p++);
				sb.append(c2);
				if (c2 == c) {
					break;
				} else if (c2 == '\\') {
					sb.append(txt.charAt(p++));
				}
			}
			// readUntil("" + (char) c);
			return submit(type, sb.toString());
		} else {
			int level = peekLongBrackets();
			if (level < 0) {
				type = LuaTokenType.OPERATOR;
				sb.append(c);
				p++;

				while (true) {
					String t = sb.toString() + (char) peek(0);

					if (longOperaters.contains(t)) {
						sb.append(txt.charAt(p++));
					} else {
						break;
					}
				}

				return submit(type, sb.toString());
			} else {
				type = LuaTokenType.STRING;
				readUntilLongBrackets(level);
				return submit(type, sb.toString());
			}
		}
	}

	final static List<String> longOperaters = Arrays.asList(new String[] { "<=", ">=", "==", "~=", "//", ">>", "<<" });

	// private boolean isOp1(char c) {
	// return "[]{}()".indexOf(c) >= 0;
	// }

	public static class TypeAndValue {
		private final LuaTokenType type;
		private final String value;

		public TypeAndValue(LuaTokenType type, String value) {
			this.type = type;
			this.value = value;
		}

		public LuaTokenType getType() {
			return type;
		}

		public String getValue() {
			return value;
		}

		@Override
		public String toString() {
			return "TypeAndValue{" +
					"type=" + type +
					", value='" + value + '\'' +
					'}';
		}
	}

	private TypeAndValue submit(LuaTokenType type, String s) {
		return new TypeAndValue(type, s);
	}

	// private boolean isOperator(char c) {
	// return !isIdentifier(c) && !isSpace(c) && c != '"' && c != '\'' && !isOp1(c);
	// }

	private void readUntilLongBrackets(int level) {
		// sb.append("<LV:" + level + ">");

		StringBuilder sb = new StringBuilder("]");
		for (int i = 0; i < level; i++) {
			sb.append('=');
		}

		sb.append(']');

		readUntil(sb.toString());
	}

	private int peekLongBrackets() {
		if (peek(0) == '[') {
			int lv = 0;
			while (peek(lv + 1) == '=') {
				lv++;
			}
			if (peek(1 + lv) == '[') {
				return lv;
			}
		}
		return -1;
	}

	private void readUntil(String s) {
		int p1 = txt.indexOf(s, p);
		if (p1 < 0) {
			sb.append(txt.substring(p));
			p = txt.length();
		} else {
			sb.append(txt.substring(p, p1 + s.length()));
			p = p1 + s.length();
		}

	}

	private char peek(int i) {
		if (p + i >= txt.length())
			return 0;
		return txt.charAt(p + i);
	}

	private boolean isIdentifier(char c) {
		return Character.isDigit(c) || Character.isLetter(c) || c == '_';
	}

	private boolean isSpace(char c) {
		return Character.isWhitespace(c);
	}

}
