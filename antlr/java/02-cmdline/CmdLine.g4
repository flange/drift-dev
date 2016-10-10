grammar CmdLine;

script : cmd*;
cmd    : Binary Args*;

Binary : [a-z]+ ;
Args   : [a-z]+('.'[a-z]+)? ;

WS  : [ \t\r\n]+ -> skip ;


