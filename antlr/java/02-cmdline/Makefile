all: grammar
	javac CmdLine*.java

grammar:
	antlr4 CmdLine.g4

clean:
	rm -rf *Lexer.java *Listener.java *Parser.java *BaseListener.java
	rm -rf *~ *.class *.tokens
