parser grammar SearchExpressionParser;

// Antlr4 generates imports with .*
@header {
// CHECKSTYLE:OFF
}

options { tokenVocab=SearchExpressionLexer; }

// Parser rules

// Should only be used for recipes. Thanks to antlr inability to properly
// inherit grammars, this is included where it's not supposed to be
recipeSearchExpression
    : (first=RECIPE_INGREDIENTS SPACE* searchExpression[0, $first.getText().length() > 1] SPACE*)* (second=RECIPE_RESULT SPACE* searchExpression[1, $second.getText().length() > 1] SPACE*)* (third=RECIPE_OTHERS SPACE* searchExpression[2, $third.getText().length() > 1] SPACE*)*
    | searchExpression[0, false]
    ;

// General search expression rules
searchExpression[int type, boolean allRecipe]
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


