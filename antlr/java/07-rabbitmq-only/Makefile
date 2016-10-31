PROJECT=CmdLine
ANTLR=/usr/share/java/antlr-complete.jar
RABBITMQ=/home/frank/1Uni/MA/ma/drift-dev/rabbitmq/rabbitmq_jars/rabbitmq-client.jar

all: grammar
	javac -cp .:${ANTLR}:${RABBITMQ} ${PROJECT}*.java

grammar:
	antlr4 ${PROJECT}.g4

clean:
	rm -rf ${PROJECT}Lexer.java ${PROJECT}Listener.java ${PROJECT}Parser.java ${PROJECT}BaseListener.java
	rm -rf *~ *.class *.tokens
