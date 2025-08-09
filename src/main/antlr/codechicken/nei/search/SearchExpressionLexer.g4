lexer grammar SearchExpressionLexer;

// Antlr4 generates imports with .*
@header {
// CHECKSTYLE:OFF
}

// Lexer rules
REGEX_LEFT        : 'r'? '/' -> pushMode(REGEX) ;
QUOTE_LEFT        : '"' -> pushMode(QUOTED) ;
DASH              : '-' ;
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
SPACE             : ' ' ;

fragment SPECIAL_SYMBOLS         : [|/\\ "] ;
fragment PREFIXES                : [@#&$%] ;
fragment ESCAPED_SPECIAL_SYMBOLS : '\\' SPECIAL_SYMBOLS ;
fragment ESCAPED_PREFIXES        : '\\' PREFIXES ;

// Thanks to antlr4 regression have to specify everything manually
fragment CLEAN_SYMBOLS           : ~[|/\\ "@#&$%] ;

mode REGEX;

REGEX_CONTENT     : (~[/] | '\\/')+ ;
REGEX_RIGHT       : '/' -> popMode ;

mode QUOTED;

QUOTED_CONTENT    : (~["] | '\\"')+ ;
QUOTE_RIGHT       : '"' -> popMode ;
