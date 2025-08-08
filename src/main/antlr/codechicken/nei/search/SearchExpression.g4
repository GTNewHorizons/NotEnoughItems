grammar SearchExpression;

// Antlr4 generates imports with .*
@header {
// CHECKSTYLE:OFF
}

// Parser rules
searchExpression
    : orExpression
    ;

orExpression
    : sequenceExpression ((' ')* OR sequenceExpression)*
    ;

sequenceExpression
    : ((' ')* unaryExpression)+
    ;

unaryExpression
    : complexUnaryExpression
    | token
    ;

complexUnaryExpression
    : LEFT_BRACKET orExpression RIGHT_BRACKET?
    | modnameExpression
    | tooltipExpression
    | identifierExpression
    | oredictExpression
    | subsetExpression
    | negateExpression
    ;

negateExpression
    : DASH smartToken
    | DASH complexUnaryExpression
    ;


modnameExpression
    : MODNAME_PREFIX token
    ;

tooltipExpression
    : TOOLTIP_PREFIX token
    ;

identifierExpression
    : IDENTIFIER_PREFIX token
    ;

oredictExpression
    : OREDICT_PREFIX token
    ;

subsetExpression
    : SUBSET_PREFIX token
    ;

token
    : smartToken
    | PLAIN_TEXT
    ;

smartToken
    : DASH
    | REGEX
    | QUOTED
    ;

// Lexer rules
REGEX             : 'r'? '/' (~[/] | '\\/')+ '/'? ;
DASH              : '-' ;
QUOTED            : '"' (~["] | '\\"')+ '"'? ;
MODNAME_PREFIX    : '@' ;
TOOLTIP_PREFIX    : '#' ;
IDENTIFIER_PREFIX : '&' ;
OREDICT_PREFIX    : '$' ;
SUBSET_PREFIX     : '%' ;
OR                : '|' ;
LEFT_BRACKET      : '\\(' ;
RIGHT_BRACKET     : '\\)' ;
PLAIN_TEXT        : (CLEAN_SYMBOLS | ESCAPED_SPECIAL_SYMBOLS | ESCAPED_PREFIXES)+ ;
NEWLINE_OR_TAB    : [\t\r\n] -> skip ;

fragment SPECIAL_SYMBOLS         : [|/\\ "] ;
fragment PREFIXES                : [@#&$%] ;
fragment ESCAPED_SPECIAL_SYMBOLS : '\\' SPECIAL_SYMBOLS ;
fragment ESCAPED_PREFIXES        : '\\' PREFIXES ;

// Thanks to antlr4 regression have to specify everything manually
fragment CLEAN_SYMBOLS           : ~[|/\\ "@#&$%] ;

