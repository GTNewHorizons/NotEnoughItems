parser grammar SearchExpressionParser;

// Antlr4 generates imports with .*
@header {
import net.minecraft.util.EnumChatFormatting;
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
    | token[EnumChatFormatting.RESET]
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
    : DASH smartToken[EnumChatFormatting.RESET]
    | DASH complexUnaryExpression
    ;


modnameExpression
    : MODNAME_PREFIX token[EnumChatFormatting.LIGHT_PURPLE]
    ;

tooltipExpression
    : TOOLTIP_PREFIX token[EnumChatFormatting.YELLOW]
    ;

identifierExpression
    : IDENTIFIER_PREFIX token[EnumChatFormatting.GOLD]
    ;

oredictExpression
    : OREDICT_PREFIX token[EnumChatFormatting.AQUA]
    ;

subsetExpression
    : SUBSET_PREFIX token[EnumChatFormatting.DARK_PURPLE]
    ;

token[EnumChatFormatting format]
    : smartToken[format]
    | PLAIN_TEXT
    ;

smartToken[EnumChatFormatting format]
    : DASH
    | regex[format]
    | quoted[format]
    ;

regex[EnumChatFormatting format]
    : REGEX_LEFT REGEX_CONTENT REGEX_RIGHT?
    ;

quoted[EnumChatFormatting format]
    : QUOTE_LEFT QUOTED_CONTENT QUOTE_RIGHT?
    ;

