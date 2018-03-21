package neoe.tools;

public class LuaTokens {

	private String txt;
	private int p;
	private LuaTokenType type;

	public LuaTokens(String txt) {
		this.txt = txt;
		this.p = 0;
	}

	StringBuilder sb = new StringBuilder();

	public Object[] next() {
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
		} else if (isIdentifier(c)) {
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
			} else {
				readUntilLongBrackets(level);
			}

			return submit(type, sb.toString());
		} else if (c == '\'' || c == '"') {
			type = LuaTokenType.STRING;
			sb.append(c);
			p++;
			readUntil("" + (char) c);
			return submit(type, sb.toString());
		} else {
			int level = peekLongBrackets();
			if (level < 0) {
				type = LuaTokenType.OPERATOR;
				sb.append(c);
				p++;
				if (isOp1(c)) {

				} else {
					char c2;
					while (p < txt.length()) {
						if (isOperator(c2 = txt.charAt(p))) {
							sb.append(c2);
							p++;
						} else {
							break;
						}
					}
				}
				if (false) {
					String s1 = sb.toString();
					for (int i = 0; i < s1.length(); i++) {
						System.out.printf("%d:%c ", (int) sb.charAt(i), sb.charAt(i));
					}
					System.out.println();
				}
				return submit(type, sb.toString());
			} else {
				type = LuaTokenType.STRING;
				readUntilLongBrackets(level);
				return submit(type, sb.toString());
			}
		}
	}

	private boolean isOp1(char c) {
		return "[]{}()".indexOf(c) >= 0;
	}

	private Object[] submit(LuaTokenType type, String s) {
		return new Object[] { type, s };
	}

	private boolean isOperator(char c) {
		return !isIdentifier(c) && !isSpace(c) && c != '"' && c != '\'' && !isOp1(c);
	}

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
