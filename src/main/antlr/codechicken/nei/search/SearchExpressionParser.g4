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
    | token[-1]
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
    : DASH smartToken[-1]
    | DASH complexUnaryExpression
    ;


modnameExpression
    : MODNAME_PREFIX token[MODNAME_PREFIX]
    ;

tooltipExpression
    : TOOLTIP_PREFIX token[TOOLTIP_PREFIX]
    ;

identifierExpression
    : IDENTIFIER_PREFIX token[IDENTIFIER_PREFIX]
    ;

oredictExpression
    : OREDICT_PREFIX token[OREDICT_PREFIX]
    ;

subsetExpression
    : SUBSET_PREFIX token[SUBSET_PREFIX]
    ;

token[Integer parentType]
    : smartToken[parentType]
    | PLAIN_TEXT
    ;

smartToken[Integer parentType]
    : DASH
    | regex[parentType]
    | quoted[parentType]
    ;

regex[Integer parentType]
    : REGEX_LEFT REGEX_CONTENT REGEX_RIGHT?
    ;

quoted[Integer parentType]
    : QUOTE_LEFT QUOTED_CONTENT QUOTE_RIGHT?
    ;

