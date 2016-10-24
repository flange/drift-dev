grammar CmdLine;

script
  : cmd';'*
  | query
  ;

query
  : '$'name
  ;

cmd
  : syncCmd
  | asyncCmd
  ;

syncCmd
  : name '=' service ('|' service)*
  ;


asyncCmd
  : name '=' service '&'
  ;

service:
  binary args*
  ;

name
  : ID_S
  ;

binary
  : ID_B
  ;

args
  : ID_S
  | FILE
  ;

ID_S   : [a-z]+ ;
ID_B   : [A-Z][a-z]+ ;
FILE   : ID_S '.' ID_S;

WS  : [ \t\r\n]+ -> skip ;


