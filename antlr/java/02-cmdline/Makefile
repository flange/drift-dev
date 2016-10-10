PROJECT=CmdLine

all: grammar
	javac ${PROJECT}*.java

grammar:
	antlr4 ${PROJECT}.g4

clean:
	rm -rf ${PROJECT}Lexer.java ${PROJECT}Listener.java ${PROJECT}Parser.java ${PROJECT}BaseListener.java
	rm -rf *~ *.class *.tokens
