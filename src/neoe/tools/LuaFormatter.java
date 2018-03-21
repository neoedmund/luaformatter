package neoe.tools;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Stack;

import neoe.util.FileUtil;

public class LuaFormatter {

	private static final boolean TESTING_LEVEL = false;
	private static final boolean DEBUG = false;

	public static void main(String[] args) throws Exception {
		new LuaFormatter().formatFile(args[0]);

	}

	public static String ts() {
		return Long.toString(System.currentTimeMillis(), 36);
	}

	private Writer debug;

	public void formatFile(String fn) throws Exception {
		if (DEBUG)
			debug = new BufferedWriter(new OutputStreamWriter(new FileOutputStream("debug.log"), "utf8"));
		if (DEBUG)
			debug.write("read " + fn + "\n");
		String txt = FileUtil.readString(new FileInputStream(fn), "GBK");

		String res = format(txt);
		File f2 = new File(fn + /* "." + ts() + */ ".fmt.lua");
		FileUtil.save(res.getBytes("UTF8"), f2.getAbsolutePath());
		System.out.println("wrote to "+f2.getAbsolutePath());
		if (DEBUG)
			debug.close();
	}

	StringBuilder sb;
	LuaTokens tokens;

	public String format(String txt) throws Exception {
		sb = new StringBuilder();
		tokens = new LuaTokens(txt);
		loop(LuaTokenType.SPACE, null, null);

		return sb.toString();
	}

	private void loop(LuaTokenType preType, String until, LuaTokenType operator) throws Exception {
		lastType = preType;
		addSpace();
		while (true) {
			Object[] tt = tokens.next();
			if (tt == null)
				break;
			LuaTokenType type = (LuaTokenType) tt[0];
			String token = (String) tt[1];
			if (token == null)
				break;
			if (DEBUG)
				debug.write(String.format("t:%s,v:%s\n", type, token));

			addToken(type, token);

			if (LuaTokenType.OPERATOR.equals(operator) && token.indexOf(until) >= 0) {
				break;
			}
			if (until != null && token.equals(until)) {
				break;
			}
		}

	}

	LuaTokenType lastType = LuaTokenType.SPACE;
	private int indent;
	private int changedLine;
	private String lastToken;
	private boolean forcedChangeLine;
	private Stack stack = new Stack();

	private void addToken(LuaTokenType type, String token) throws Exception {
		forcedChangeLine = false;
		if (type.equals(LuaTokenType.COMMENT)) {
			if (changedLine > 0) {
				printIndent();
				changedLine = 0;
			}
			addSpace();
			if (!isMultiLineToken(token)) {
				sb.append(normalComment(token.trim()));
				changeLine();
			} else {
				sb.append(token);
			}

		} else if (type.equals(LuaTokenType.SPACE)) {

			if (!lastType.equals(LuaTokenType.SPACE)) {
				int cnt = 0;
				for (char c : token.toCharArray()) {
					if (c == '\n') {
						if (cnt >= changedLine) {
							// sb.append("[A]");
							sb.append(c);
						}
						cnt++;
					}
				}
				if (cnt <= 0 && !lastType.equals(LuaTokenType.SPACE)) {
					sb.append(" ");
				} else {
					if (changedLine > 0) {
						printIndent();
						changedLine = 0;
					} else {
						changedLine = cnt;
					}
				}
			}
		} else if (type.equals(LuaTokenType.IDENTIFIER)) {
			if ("end".equals(token)) {
				String key = decIndent();
				if (changedLine <= 0) {
					sb.append("\n");
				}
				printIndent();
				changedLine = 0;

				sb.append(token);
				changeLine();
				if ("function".equals(key)) {
					sb.append("\n");
				}
			} else if ("else".equals(token)) {
				String key = decIndent();
				if (changedLine <= 0) {
					sb.append("\n");
				}
				printIndent();
				changedLine = 0;
				sb.append(token);
				incIndent(key);
				changeLine();

			} else if ("elseif".equals(token)) {
				String key = decIndent();
				if (changedLine <= 0) {
					sb.append("\n");
				}
				printIndent();
				changedLine = 0;

				sb.append(token);
				incIndent(key);
			} else {
				if (changedLine > 0) {
					printIndent();
					changedLine = 0;
				}
				addSpace();
				sb.append(token);
				if ("function".equals(token)) {
					loop(type, ")", LuaTokenType.OPERATOR);
					changeLine();
					incIndent(token);
				} else if ("until".equals(token)) {
					decIndent();
					changeLine();
				} else if ("while".equals(token)) {
					loop(type, "do", null);
					incIndent(token);
					changeLine();
				} else if ("for".equals(token)) {
					loop(type, "do", null);
					incIndent(token);
					changeLine();
				} else if ("if".equals(token)) {
					loop(type, "then", null);
					incIndent(token);
					changeLine();
				} else if ("repeat".equals(token)) {
					decIndent();
					changeLine();
				}

			}

		} else if (type.equals(LuaTokenType.OPERATOR) && token.startsWith("}"))

		{
			String key = decIndent();
			if (changedLine > 0) {
				printIndent();
				changedLine = 0;
			} else {
				addSpace();
			}
			sb.append(token);
			if (indent == 0) {
				sb.append("\n");
			}
		} else if (type.equals(LuaTokenType.OPERATOR) && token.equals(".")) {
			sb.append(token);
		} else {
			if (changedLine > 0) {
				printIndent();
				changedLine = 0;
			}
			addSpace();
			sb.append(token);
			if (type.equals(LuaTokenType.OPERATOR)) {
				for (char c : token.toCharArray()) {
					if (c == '{') {
						incIndent("{");
					} else if (c == '}') {
						decIndent();
					} else if (c == ')') {
						decIndent();
					} else if (c == '(') {
						incIndent("(");
					} else if (c == ']') {
						decIndent();
					} else if (c == '[') {
						incIndent("[");
					}
				}

			}
		}
		lastType = type;
		lastToken = token;

		if (forcedChangeLine) {
			lastType = LuaTokenType.SPACE;
		}

	}

	private String normalComment(String s) {

		if (s.startsWith("--") && s.length() > 2 && s.charAt(2) != ' ' && s.charAt(2) != '-') {
			s = "-- " + s.substring(2);

		}
		return s;
	}

	private boolean isMultiLineToken(String token) {
		return token.endsWith("]--");
	}

	private void printIndent() {
		if (TESTING_LEVEL)
			sb.append("[" + indent + "]");
		for (int i = 0; i < indent; i++) {
			sb.append("\t");
		}
		// if (indent > 0)
		lastType = LuaTokenType.SPACE;
	}

	// private void passToOP(String s) {
	// while (true) {
	// Object[] tt = tokens.next();
	// if (tt == null) {
	// return;
	// }
	// String token = (String) tt[1];
	// sb.append(token);
	// if (LuaTokenType.OPERATOR.equals(tt[0]) && token.indexOf(s) >= 0) {
	// return;
	// }
	// }
	//
	// }

	private void incIndent(String key) {
		// sb.append("[i++]");
		indent++;
		stack.push(key);
	}

	// private void passTo(String s) {
	// while (true) {
	// Object[] tt = tokens.next();
	// if (tt == null) {
	// return;
	// }
	// String token = (String) tt[1];
	// sb.append(token);
	// if (token.equals(s)) {
	// return;
	// }
	// }
	//
	// }

	private void changeLine() {
		if (changedLine <= 0) {
			sb.append("\n");
		}
		changedLine = 1;
		forcedChangeLine = true;
	}

	private String decIndent() {
		indent--;
		// if (stack.isEmpty()) return "";
		return (String) stack.pop();
	}

	private void addSpace() {
		if (!lastType.equals(LuaTokenType.SPACE) && !".".equals(lastToken)) {
			sb.append(" ");
		}
	}

}
/*-
 *  stat ::= while exp do block end
	stat ::= repeat block until exp
	stat ::= if exp then block {elseif exp then block} [else block] end
	stat ::= for Name ‘=’ exp ‘,’ exp [‘,’ exp] do block end
	stat ::= for namelist in explist do block end
	function f () body end
 * 
 */
