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
    : recipeClauseExpression*
    ;

recipeClauseExpression
    : ingredientsPrefix=RECIPE_INGREDIENTS searchExpression[0, $ingredientsPrefix.getText().length() > 1]
    | resultPrefix=RECIPE_RESULT searchExpression[1, $resultPrefix.getText().length() > 1]
    | othersPrefix=RECIPE_OTHERS searchExpression[2, $othersPrefix.getText().length() > 1]
    | searchExpression[3, false]
    ;

// General search expression rules
searchExpression[int type, boolean allRecipe]
    : orExpression
    ;

orExpression
    : sequenceExpression (OR sequenceExpression)*
    ;

sequenceExpression
    : (SPACE* unaryExpression SPACE*)+
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


