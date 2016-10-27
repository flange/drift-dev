grammar CmdLine;

script
  : cmd
  | query
  | ls
  | cd
  | rm
  ;

cd
  : 'cd ' namespace
  | 'cd ' up
  ;

up
  : UP ('/')?
  | UP ('/'UP('/')?)*
  ;

ls
  : 'ls'
  | 'ls ' namespace
  ;

rm
  : 'rm ' targetName
  | 'rm ' targetNamespace
  ;

query
  : '$'targetName
  | '$'targetNamespace
  ;

cmd
  : syncCmd
  | asyncCmd
  ;

syncCmd
  : service (' | ' service)*
  | targetName ' = ' service (' | ' service)*
  | targetNamespace ' = ' serviceNsOut
  ;


asyncCmd
  : targetName ' = ' service (' | ' service)* ' &'
  | targetNamespace ' = ' serviceNsOut ' &'
  ;

service
  : binary args* argName? (' 'argName)*
  ;

serviceNsOut
  : binary'*' args* argName? (' 'argName)*
  ;

serviceNsIn
  : '*'binary args* argName? (' 'argName)*
  ;

serviceNsInOut
  : '*'binary'*' args* argName? (' 'argName)*
  ;

argName
  : name
  | namespace
  ;

name
  : ID_S ('/'ID_S)*
  ;

namespace
  : ID_S'/'(ID_S'/')*
  ;

targetName
  : name
  ;

targetNamespace
  : namespace
  ;

binary
  : ID_B
  ;

args
  : OPTION
  ;

ID_S   : [a-z]('a'..'z' | 'A'..'Z' | '0'..'9' | '.')* ;
ID_B   : [A-Z]('a'..'z' | 'A'..'Z' | '0'..'9')* ;
FILE   : ID_S '.' ID_S ;
OPTION : '-'ID_S ;
UP     : '..' ;



WS  : [ \t\n]+ -> skip ;


