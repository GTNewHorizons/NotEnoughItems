lexer grammar SearchExpressionLexer;

// Antlr4 generates imports with .*
@header {
// CHECKSTYLE:OFF
import codechicken.nei.SearchTokenParser;
}

@members {
private SearchTokenParser searchParser;

public SearchExpressionLexer(CharStream input, SearchTokenParser searchParser) {
    this(input);
    this.searchParser = searchParser;
}
}

// Lexer rules
REGEX_LEFT        : 'r'? '/' -> pushMode(REGEX) ;
QUOTE_LEFT        : '"' -> pushMode(QUOTED) ;
DASH              : '-' ;
PREFIX            : CLEAN_SYMBOL {searchParser.hasRedefinedPrefix(getText().charAt(0))}? ;
OR                : '|' ;
LEFT_BRACKET      : '{' ;
RIGHT_BRACKET     : '}' ;
PLAIN_TEXT        : (CLEAN_SYMBOL | ESCAPED_SYMBOL)+ {!searchParser.hasRedefinedPrefix(getText().charAt(0))}? ;
NEWLINE_OR_TAB    : [\t\r\n] -> skip ;
SPACE             : ' ' ;

fragment ESCAPED_SYMBOL         : '\\' . ;
fragment CLEAN_SYMBOL           : ~[-{}|/\\ "] ;

mode REGEX;

REGEX_CONTENT     : (~[/] | '\\/')+ ;
REGEX_RIGHT       : '/' -> popMode ;

mode QUOTED;

QUOTED_CONTENT    : (~["] | '\\"')+ ;
QUOTE_RIGHT       : '"' -> popMode ;

