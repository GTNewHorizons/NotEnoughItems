grammar SearchExpression;

// Antlr4 generates imports with .*
@header {
// CHECKSTYLE:OFF
}

// Parser rules
searchExpression
    : orExpression EOF
    ;

orExpression
    : sequenceExpression ((' ')* '|' (' ')* sequenceExpression)*
    ;

sequenceExpression
    : unaryExpression ((' ')* unaryExpression)*
    ;

unaryExpression
    : '\\(' orExpression '\\)'
    | token
    | modnameExpression
    | tooltipExpression
    | identifierExpression
    | oredictExpression
    | subsetExpression
    | negateExpression
    ;

negateExpression
    : '-' unaryExpression
    ;


modnameExpression
    : '@' token
    ;

tooltipExpression
    : '#' token
    ;

identifierExpression
    : '&' token
    ;

oredictExpression
    : '$' token
    ;

subsetExpression
    : '%' token
    ;

token
    : PLAIN_TEXT
    | REGEX
    | QUOTED
    ;

// Lexer rules
REGEX          : 'r'? '/' (~[/] | '\\/')+ '/' ;
PLAIN_TEXT     : (CLEAN_SYMBOLS | ESCAPED_SPECIAL_SYMBOLS | ESCAPED_PREFIXES)+ ;
QUOTED         : '"' (~["] | '\\"')+ '"' ;
NEWLINE_OR_TAB : [\t\r\n] -> skip ;

fragment SPECIAL_SYMBOLS         : [-|/\\ "] ;
fragment PREFIXES                : [@#&$%] ;
fragment ESCAPED_SPECIAL_SYMBOLS : '\\' SPECIAL_SYMBOLS ;
fragment ESCAPED_PREFIXES        : '\\' PREFIXES ;

// Thanks to antlr4 regression have to specify everything manually
fragment CLEAN_SYMBOLS           : ~[-|/\\ "@#&$%] ;

