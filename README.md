LUAFORMATTER
=====================
luaformatter is a lua source code formatter written in Java by Neoe.

It use a handcrafted parser as little as 300 lines of Java code.

# How to use
* install java (JRE from java.com) if not installed
* run.cmd (java -jar luaformatter.jar <args>),
args: 
  
```
-o  -- overwrite source
-e<ENCODING> -- use ENCODING
input-files-or-dirs
```
Files cause formatting error will be picked up automatically and save to .fmt-err.lua files.


# Why
I have tried several lua formatters online but no one work good, so I wrote this really works thing.
