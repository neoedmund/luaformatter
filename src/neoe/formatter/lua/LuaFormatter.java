package neoe.formatter.lua;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import neoe.util.FileIterator;
import neoe.util.FileUtil;

public class LuaFormatter {

	private static boolean TESTING_LEVEL = false;
	private static boolean DEBUG = false;

	public static void main(String[] args) throws Exception {
		if (args.length == 0) {
			usage();
		} else {
			new LuaFormatter().run(args);
		}

	}

	private static void usage() {
		System.out.println(
				"luaformatter: args:\n -o  -- overwrite source\n -e<ENCODING> -- use ENCODING\n -d -- debug\n  input-files\n");

	}

	List<String> fs = new ArrayList<>();
	private String encoding = "utf8";
	private boolean overwritesource = false;

	public void run(String[] args) throws Exception {

		for (String s : args) {
			if (s.startsWith("-")) {
				doOpt(s);
			} else {
				addFile(s);
			}
		}
		if (fs.isEmpty()) {
			System.out.println("no input files");
		} else {
			for (String fn : fs) {
				formatFile(fn);
			}
		}
		System.out.printf("OK: %d, Error:%d, skip: %d\n", ok, err, skip);
	}

	private void addFile(String fn) {
		File f = new File(fn);
		if (f.isDirectory()) {
			for (File f1 : new FileIterator(fn)) {
				String name = f1.getName();
				if (name.endsWith(".lua") && !name.endsWith(".fmt.lua") && !name.endsWith(".fmt-err.lua")) {
					fs.add(f1.getAbsolutePath());
				}
			}
		} else if (f.isFile()) {
			fs.add(fn);
		}
	}

	private void doOpt(String s) {
		if (s.startsWith("-e")) {
			encoding = s.substring(2);
		} else if (s.startsWith("-o")) {
			overwritesource = true;
		} else if (s.startsWith("-d")) {
			DEBUG = true;
			TESTING_LEVEL = true;
			overwritesource = false;
		}

	}

	private Writer debug;
	private int err;
	private int ok;
	private int skip;

	public void formatFile(String fn) {
		try {
			Env env = new Env();
			if (DEBUG)
				debug = new BufferedWriter(new OutputStreamWriter(new FileOutputStream("debug.log"), "utf8"));
			if (DEBUG)
				debug.write("read " + fn + "\n");
			String txt = FileUtil.readString(new FileInputStream(fn), encoding);
			try {
				String res = format(txt, env);
				if (env.indent != 0) {
					env.indent = 0;
					throw new RuntimeException("indent not correct:" + fn);
				}
				{// testing , and for some mysterious case
					String res2 = format(res, new Env());
					byte[] bs1 = res.getBytes(encoding);
					byte[] bs2 = res2.getBytes(encoding);
					if (bs1.length != bs2.length) {
						FileUtil.save(bs1, fn + ".fmt-err1.lua");
						FileUtil.save(bs2, fn + ".fmt-err2.lua");
						byte[] bs3 = FileUtil.read(new FileInputStream(fn + ".fmt-err1.lua"));
						System.out.printf("bs1 vs bs3(after wrote),len %d vs %d\n", bs1.length, bs3.length);
						{
							int len = Math.min(bs1.length, bs2.length);
							for (int i = 0; i < len; i++) {
								if (bs1[i] != bs2[i]) {
									System.out.printf("pos %,d not match:%x vs %x, '%s' vs '%s'\n", i, bs1[i], bs2[i],
											new String(bs1, i, 10), new String(bs2, i, 10));
									break;
								}
							}
						}
						throw new RuntimeException(String.format("reformat not test ok, size  %,d -> %,d , fn:%s, debug[%x %x %x %x]",
								bs1.length, bs2.length, fn, bs1[0], bs2[0], bs1[bs1.length - 1], bs2[bs2.length - 1]));

					}
				}

				if (txt.equals(res)) {
					skip++;
				} else {

					File f2 = new File(fn + (overwritesource ? "" : ".fmt.lua"));
					FileUtil.save(res.getBytes(encoding), f2.getAbsolutePath());
					if (DEBUG)
						System.out.println("wrote to " + f2.getAbsolutePath());
					ok++;
				}
			} catch (Exception e) {
				e.printStackTrace();
				File f2 = new File(fn + ".fmt-err.lua");
				FileUtil.save(sb.toString().getBytes(encoding), f2.getAbsolutePath());
				System.out.println("wrote to " + f2.getAbsolutePath());
				err++;
			}

			if (DEBUG)
				debug.close();
		} catch (Exception e) {
			e.printStackTrace();
			System.err.printf("when formatting [%s]\n", fn);
		}
	}

	StringBuilder sb;
	LuaTokens tokens;

	public String format(String txt, Env env) throws Exception {
		sb = new StringBuilder();
		tokens = new LuaTokens(txt);
		loop(null, null, null, env);
		return sb.toString();
	}

	private void loop(LuaTokenType preType, String until, LuaTokenType operator, Env env) throws Exception {
		env.lastType = preType;
		while (true) {
			LuaTokens.TypeAndValue tt = tokens.next();
			if (tt == null)
				break;
			addSpaceOnNeed(env, tt);
			LuaTokenType type = tt.getType();
			String token = tt.getValue();
			if (token == null)
				break;

			if (DEBUG) {
				debug.write(String.format("t:%s,v:%s\n", type, token));
				debug.flush();
			}

			addToken(env, tt);

			if (LuaTokenType.OPERATOR.equals(operator) && token.indexOf(until) >= 0) {
				break;
			}
			if (token.equals(until)) {
				break;
			}
		}

	}

	public static class Env {
		LuaTokenType lastType = LuaTokenType.SPACE;
		int indent;
		int changedLine;
		String lastToken;
		boolean forcedChangeLine;
		Stack stack = new Stack();
	}

	private void addToken(Env env, LuaTokens.TypeAndValue tt) throws Exception {
		LuaTokenType type = tt.getType();
		String token = tt.getValue();
		env.forcedChangeLine = false;
		if (type.equals(LuaTokenType.COMMENT)) {
			pre(env, tt);
			if (isMultiLineToken(token)) {
				sb.append(token);
			} else {
				sb.append(normalComment(token.trim()));
			}

		} else if (type.equals(LuaTokenType.SPACE)) {
			// sb.append("<sp cl=" + env.changedLine + ">");
			int cnt = printSpaceLines(env, token);

			if (cnt <= 0) {
				addSpaceOnNeed(env, tt);
			} else {
				env.changedLine = cnt;
			}
			// sb.append("</sp>");

		} else if (type.equals(LuaTokenType.IDENTIFIER)) {

			if ("end".equals(token)) {
				String key = decIndent(env);
				changeLineOnNeed(env);
				printToken(env, tt);
				changeLineOnNeed(env);
				if ("function".equals(key)) {
					sb.append("\n");
					env.changedLine++;
				}
			} else if ("else".equals(token)) {
				String key = decIndent(env);
				changeLineOnNeed(env);
				printToken(env, tt);
				incIndent(env, key);
				changeLineOnNeed(env);
			} else if ("elseif".equals(token)) {
				changeLineOnNeed(env);
				String key = decIndent(env);
				printToken(env, tt);
			} else if ("until".equals(token)) {
				decIndent(env);
				printToken(env, tt);
			} else if ("do".equals(token)) {
				printToken(env, tt);
				incIndent(env, token);
				changeLineOnNeed(env);
			} else if ("local".equals(token)) {
				changeLineOnNeed(env);
				printToken(env, tt);
			} else if ("if".equals(token)) {
				changeLineOnNeed(env);
				printToken(env, tt);
			} else if ("then".equals(token)) {
				printToken(env, tt);
				forceChangeLine(env);
				incIndent(env, token);
			} else if ("function".equals(token)) {
				printToken(env, tt);
				env.changedLine = 0;
				loop(type, ")", LuaTokenType.OPERATOR, env);
				forceChangeLine(env);
				incIndent(env, token);
			} else if ("while".equals(token)) {
				changeLineOnNeed(env);
				printToken(env, tt);
			} else if ("for".equals(token)) {
				changeLineOnNeed(env);
				printToken(env, tt);
			} else if ("print".equals(token)) {
				changeLineOnNeed(env);
				printToken(env, tt);
			} else if ("repeat".equals(token)) {
				changeLineOnNeed(env);
				printToken(env, tt);
				forceChangeLine(env);
				incIndent(env, token);
				loop(type, "until", null, env);
			} else {
				printToken(env, tt);
			}

		} else if (type.equals(LuaTokenType.OPERATOR)) {
			if (token.equals(".")) {
				sb.append(token);
			} else if ("}])".indexOf(token) >= 0) {
				decIndent(env);
				printToken(env, tt);
			} else if ("{[(".indexOf(token) >= 0) {
				printToken(env, tt);
				incIndent(env, token);
			} else if (token.equals(";")) {
				if (env.changedLine <= 0) {
					newline();
					env.forcedChangeLine = true;
					env.changedLine = 1;
				} else {
					if (!env.lastType.equals(LuaTokenType.SPACE)) {
						if (env.changedLine <= 0)
							sb.append(" ");
						type = LuaTokenType.SPACE;
					}
				}
			} else {
				printToken(env, tt);
			}
		} else {
			printToken(env, tt);
		}
		env.lastType = type;
		env.lastToken = token;

	}

	private void printToken(Env env, LuaTokens.TypeAndValue tt) {
		pre(env, tt);
		sb.append(tt.getValue());
	}

	private int printSpaceLines(Env env, String token) {
		int cnt = 0;
		for (char c : token.toCharArray()) {
			if (c == '\n') {
				if (cnt >= env.changedLine) {
					newline();
				}
				cnt++;
			}
		}
		return cnt;
	}

	/** print indent if in newline or add a space */
	private void pre(Env env, LuaTokens.TypeAndValue tt) {
		// sb.append("[cl=" + env.changedLine + "]");
		if (env.changedLine > 0) {
			printIndent(env);
			env.changedLine = 0;
		} else {
			addSpaceOnNeed(env, tt);
		}

	}

	private String normalComment(String s) {

		if (s.startsWith("--") && s.length() > 2 && s.charAt(2) != ' ' && s.charAt(2) != '-') {
			s = "-- " + s.substring(2);

		}
		return s;
	}

	private boolean isMultiLineToken(String token) {
		return token.startsWith("--[") && token.endsWith("]");// && token.contains("\n");
	}

	private void printIndent(Env env) {
		if (TESTING_LEVEL)
			sb.append("[" + env.indent + "]");
		for (int i = 0; i < env.indent; i++) {
			sb.append("\t");
		}
	}

	private void incIndent(Env env, String key) {
		// sb.append("[i++]");
		env.indent++;
		env.stack.push(key);
	}

	private void changeLineOnNeed(Env env) {
		if (env.changedLine <= 0) {
			newline();
			env.forcedChangeLine = true;
			env.changedLine = 1;
		}

	}

	private void newline() {
		int p = sb.length();
		while (true) {
			if (p <= 0)
				break;
			char c = sb.charAt(p - 1);
			if (c == ' ' || c == '\t') {
				p--;
				sb.setLength(p);
			} else
				break;
		}
		sb.append("\n");
	}

	private void forceChangeLine(Env env) {
		newline();
		env.forcedChangeLine = true;
		env.changedLine = 1;
	}

	private String decIndent(Env env) {
		env.indent--;
		// if (stack.isEmpty()) return "";
		return (String) env.stack.pop();
	}

	private void addSpaceOnNeed(Env env, LuaTokens.TypeAndValue currentToken) {
		if (env.lastType != null && !LuaTokenType.SPACE.equals(env.lastType) && !".".equals(env.lastToken)
				&& env.changedLine <= 0) {

			if ("(".equals(env.lastToken) || "[".equals(env.lastToken))
				return;
			if (currentToken != null) {
				if (")".equals(currentToken.getValue()) ||
						"(".equals(currentToken.getValue()) ||
						",".equals(currentToken.getValue()) ||
						"[".equals(currentToken.getValue()) ||
						"]".equals(currentToken.getValue())
				)
					return;
			}

			sb.append(" ");
			env.lastType = LuaTokenType.SPACE;
			env.changedLine = 0;
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
