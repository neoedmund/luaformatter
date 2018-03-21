LUAFORMATTER
=====================
luaformatter is a lua source code formatter written in Java by Neoe.

It use a handcrafted parser as little as 300 lines of Java code.

# How to use
* install java (JRE from java.com) if not installed
* run.cmd (java -jar luaformatter.jar <args>),
args: 
```-o  -- overwrite source
-e<ENCODING> -- use ENCODING
input-files-or-dir```
* if you have problem with encoding(because it use default UTF8), you can modify the source or use it as a library, write a java class calles `LuaFormatter.format(String text)`

