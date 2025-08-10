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
    : complexUnaryExpression
    | token['\0']
    ;

complexUnaryExpression
    : LEFT_BRACKET orExpression RIGHT_BRACKET?
    | prefixedExpression
    | negateExpression
    ;

negateExpression
    : DASH smartToken['\0']
    | DASH complexUnaryExpression
    ;

prefixedExpression
    locals [
        Character prefix
    ]
    : prefixToken=PREFIX { $prefix =  $prefixToken.getText().charAt(0); } token[$prefix]
    ;

token[Character prefix]
    : smartToken[prefix]
    | PLAIN_TEXT
    ;

smartToken[Character prefix]
    : DASH
    | regex[prefix]
    | quoted[prefix]
    ;

regex[Character prefix]
    : REGEX_LEFT REGEX_CONTENT REGEX_RIGHT?
    ;

quoted[Character prefix]
    : QUOTE_LEFT QUOTED_CONTENT QUOTE_RIGHT?
    ;


