package neoe.tools;

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
				File f2 = new File(fn + (overwritesource ? "" : ".fmt.lua"));
				FileUtil.save(res.getBytes(encoding), f2.getAbsolutePath());
				System.out.println("wrote to " + f2.getAbsolutePath());
			} catch (Exception e) {
				e.printStackTrace();
				File f2 = new File(fn + ".fmt-err.lua");
				FileUtil.save(sb.toString().getBytes(encoding), f2.getAbsolutePath());
				System.out.println("wrote to " + f2.getAbsolutePath());
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
		loop(LuaTokenType.SPACE, null, null, env);
		return sb.toString();
	}

	private void loop(LuaTokenType preType, String until, LuaTokenType operator, Env env) throws Exception {
		env.lastType = preType;
		addSpaceOnNeed(env);
		while (true) {
			Object[] tt = tokens.next();
			if (tt == null)
				break;
			LuaTokenType type = (LuaTokenType) tt[0];
			String token = (String) tt[1];
			if (token == null)
				break;

			if (DEBUG) {
				debug.write(String.format("t:%s,v:%s\n", type, token));
				debug.flush();
			}

			addToken(type, token, env);

			if (LuaTokenType.OPERATOR.equals(operator) && token.indexOf(until) >= 0) {
				break;
			}
			if (until != null && token.equals(until)) {
				break;
			}
		}

	}

	static class Env {
		LuaTokenType lastType = LuaTokenType.SPACE;
		int indent;
		int changedLine;
		String lastToken;
		boolean forcedChangeLine;
		Stack stack = new Stack();
	}

	private void addToken(LuaTokenType type, String token, Env env) throws Exception {
		env.forcedChangeLine = false;
		if (type.equals(LuaTokenType.COMMENT)) {
			pre(env);
			if (isMultiLineToken(token)) {
				sb.append(token);
			} else {
				sb.append(normalComment(token.trim()));
			}

		} else if (type.equals(LuaTokenType.SPACE)) {
			// sb.append("<sp cl=" + env.changedLine + ">");
			int cnt = printSpaceLines(env, token);

			if (cnt <= 0) {
				addSpaceOnNeed(env);
			} else {
				env.changedLine = cnt;
			}
			// sb.append("</sp>");

		} else if (type.equals(LuaTokenType.IDENTIFIER)) {

			if ("end".equals(token)) {
				String key = decIndent(env);
				changeLineOnNeed(env);
				printToken(env, token);
				changeLineOnNeed(env);
				if ("function".equals(key)) {
					sb.append("\n");
					env.changedLine++;
				}
			} else if ("else".equals(token)) {
				String key = decIndent(env);
				changeLineOnNeed(env);
				printToken(env, token);
				incIndent(env, key);
				changeLineOnNeed(env);
			} else if ("elseif".equals(token)) {
				changeLineOnNeed(env);
				String key = decIndent(env);
				printToken(env, token);
			} else if ("until".equals(token)) {
				decIndent(env);
				printToken(env, token);
			} else if ("do".equals(token)) {
				printToken(env, token);
				incIndent(env, token);
				changeLineOnNeed(env);
			} else if ("local".equals(token)) {
				changeLineOnNeed(env);
				printToken(env, token);
			} else if ("if".equals(token)) {
				changeLineOnNeed(env);
				printToken(env, token);
			} else if ("then".equals(token)) {
				printToken(env, token);
				forceChangeLine(env);
				incIndent(env, token);
			} else if ("function".equals(token)) {
				printToken(env, token);
				env.changedLine = 0;
				loop(type, ")", LuaTokenType.OPERATOR, env);
				forceChangeLine(env);
				incIndent(env, token);
			} else if ("while".equals(token)) {
				changeLineOnNeed(env);
				printToken(env, token);
			} else if ("for".equals(token)) {
				changeLineOnNeed(env);
				printToken(env, token);
			} else if ("print".equals(token)) {
				changeLineOnNeed(env);
				printToken(env, token);
			} else if ("repeat".equals(token)) {
				changeLineOnNeed(env);
				printToken(env, token);
				forceChangeLine(env);
				incIndent(env, token);
				loop(type, "until", null, env);
			} else {
				printToken(env, token);
			}

		} else if (type.equals(LuaTokenType.OPERATOR)) {
			if (token.equals(".")) {
				sb.append(token);
			} else if ("}])".indexOf(token) >= 0) {
				decIndent(env);
				printToken(env, token);
			} else if ("{[(".indexOf(token) >= 0) {
				printToken(env, token);
				incIndent(env, token);
			} else {
				printToken(env, token);
			}
		} else {
			printToken(env, token);
		}
		env.lastType = type;
		env.lastToken = token;

	}

	private void printToken(Env env, String token) {
		pre(env);
		sb.append(token);
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
	private void pre(Env env) {
		// sb.append("[cl=" + env.changedLine + "]");
		if (env.changedLine > 0) {
			printIndent(env);
			env.changedLine = 0;
		} else {
			addSpaceOnNeed(env);
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

	private void addSpaceOnNeed(Env env) {
		if (!env.lastType.equals(LuaTokenType.SPACE) && !".".equals(env.lastToken) && env.changedLine <= 0) {
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
