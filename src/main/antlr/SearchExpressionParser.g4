parser grammar SearchExpressionParser;

// Antlr4 generates imports with .*
@header {
// CHECKSTYLE:OFF
}

options { tokenVocab=SearchExpressionLexer; }

// Parser rules
searchExpression
    : orExpression
    ;

orExpression
    : sequenceExpression (SPACE* OR sequenceExpression)*
    ;

sequenceExpression
    : (SPACE* unaryExpression)+
    ;

unaryExpression
    : LEFT_BRACKET orExpression RIGHT_BRACKET?
    | prefixedExpression
    | negateExpression
    | token['\0']
    ;

negateExpression
    : DASH unaryExpression
    ;

prefixedExpression
    locals [
        Character prefix
    ]
    : prefixToken=PREFIX { $prefix =  $prefixToken.getText().charAt(0); } token[$prefix]
    ;

token[Character prefix]
    : regex[prefix]
    | quoted[prefix]
    | PLAIN_TEXT
    ;

regex[Character prefix]
    : REGEX_LEFT REGEX_CONTENT REGEX_RIGHT?
    ;

quoted[Character prefix]
    : QUOTE_LEFT QUOTED_CONTENT QUOTE_RIGHT?
    ;


