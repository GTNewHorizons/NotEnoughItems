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
REGEX_LEFT         : 'r'? '/' -> pushMode(REGEX) ;
QUOTE_LEFT         : '"' -> pushMode(QUOTED) ;

DASH               : '-' ;

RECIPE_INGREDIENTS : '<&' | '<' ;
RECIPE_RESULT      : '>&' | '>' ;
RECIPE_OTHERS      : '^&' | '^' ;

OR                 : '|' ;
LEFT_BRACKET       : '{' ;
RIGHT_BRACKET      : '}' ;
NEWLINE_OR_TAB     : [\t\r\n] -> skip ;
SPACE              : ' ' ;
ESCAPED_SYMBOL     : '\\' . ;

PREFIX             : CLEAN_SYMBOL {searchParser.hasRedefinedPrefix(getText().charAt(0))}? ;
CLEAN_SYMBOL       : ~[-<>^{}|/\\ "] ;

mode REGEX;

REGEX_CONTENT     : (~[/] | '\\/')+ ;
REGEX_RIGHT       : '/' -> popMode ;

mode QUOTED;

QUOTED_CONTENT    : (~["] | '\\"')+ ;
QUOTE_RIGHT       : '"' -> popMode ;

