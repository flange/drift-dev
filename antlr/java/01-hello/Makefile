all: grammar
	javac Hello*.java

grammar:
	antlr4 Hello.g4

clean:
	rm -rf *Lexer.java *Listener.java *Parser.java *BaseListener.java
	rm -rf *~ *.class *.tokens
