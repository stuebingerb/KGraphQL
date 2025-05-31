package com.apurebase.kgraphql.request

import com.apurebase.kgraphql.schema.directive.DirectiveLocation
import com.apurebase.kgraphql.schema.model.ast.ArgumentNode
import com.apurebase.kgraphql.schema.model.ast.DefinitionNode
import com.apurebase.kgraphql.schema.model.ast.DirectiveNode
import com.apurebase.kgraphql.schema.model.ast.DocumentNode
import com.apurebase.kgraphql.schema.model.ast.EnumValueDefinitionNode
import com.apurebase.kgraphql.schema.model.ast.FieldDefinitionNode
import com.apurebase.kgraphql.schema.model.ast.InputValueDefinitionNode
import com.apurebase.kgraphql.schema.model.ast.Location
import com.apurebase.kgraphql.schema.model.ast.NameNode
import com.apurebase.kgraphql.schema.model.ast.OperationTypeDefinitionNode
import com.apurebase.kgraphql.schema.model.ast.OperationTypeNode
import com.apurebase.kgraphql.schema.model.ast.OperationTypeNode.MUTATION
import com.apurebase.kgraphql.schema.model.ast.OperationTypeNode.QUERY
import com.apurebase.kgraphql.schema.model.ast.OperationTypeNode.SUBSCRIPTION
import com.apurebase.kgraphql.schema.model.ast.SelectionNode
import com.apurebase.kgraphql.schema.model.ast.SelectionSetNode
import com.apurebase.kgraphql.schema.model.ast.Source
import com.apurebase.kgraphql.schema.model.ast.Token
import com.apurebase.kgraphql.schema.model.ast.TokenKindEnum
import com.apurebase.kgraphql.schema.model.ast.TokenKindEnum.AMP
import com.apurebase.kgraphql.schema.model.ast.TokenKindEnum.AT
import com.apurebase.kgraphql.schema.model.ast.TokenKindEnum.BANG
import com.apurebase.kgraphql.schema.model.ast.TokenKindEnum.BLOCK_STRING
import com.apurebase.kgraphql.schema.model.ast.TokenKindEnum.BRACE_L
import com.apurebase.kgraphql.schema.model.ast.TokenKindEnum.BRACE_R
import com.apurebase.kgraphql.schema.model.ast.TokenKindEnum.BRACKET_L
import com.apurebase.kgraphql.schema.model.ast.TokenKindEnum.BRACKET_R
import com.apurebase.kgraphql.schema.model.ast.TokenKindEnum.COLON
import com.apurebase.kgraphql.schema.model.ast.TokenKindEnum.DOLLAR
import com.apurebase.kgraphql.schema.model.ast.TokenKindEnum.EOF
import com.apurebase.kgraphql.schema.model.ast.TokenKindEnum.EQUALS
import com.apurebase.kgraphql.schema.model.ast.TokenKindEnum.FLOAT
import com.apurebase.kgraphql.schema.model.ast.TokenKindEnum.INT
import com.apurebase.kgraphql.schema.model.ast.TokenKindEnum.NAME
import com.apurebase.kgraphql.schema.model.ast.TokenKindEnum.PAREN_L
import com.apurebase.kgraphql.schema.model.ast.TokenKindEnum.PAREN_R
import com.apurebase.kgraphql.schema.model.ast.TokenKindEnum.PIPE
import com.apurebase.kgraphql.schema.model.ast.TokenKindEnum.SOF
import com.apurebase.kgraphql.schema.model.ast.TokenKindEnum.SPREAD
import com.apurebase.kgraphql.schema.model.ast.TokenKindEnum.STRING
import com.apurebase.kgraphql.schema.model.ast.TypeNode
import com.apurebase.kgraphql.schema.model.ast.ValueNode
import com.apurebase.kgraphql.schema.model.ast.VariableDefinitionNode

internal class Parser {
    private val options: Options

    constructor(options: Options? = null) {
        this.options = options ?: Options()
    }

    /**
     * Converts a name lex token into a name parse node.
     */
    private fun Lexer.parseName(): NameNode {
        val token = expectToken(NAME)
        return NameNode(
            value = token.value!!,
            loc = loc(token)
        )
    }

    // Implements the parsing rules in the Document section.

    /**
     * Document : Definition+
     */
    fun parseDocument(input: Source): DocumentNode {
        val lexer = Lexer(input)
        val start = lexer.token

        return DocumentNode(
            definitions = lexer.many(
                SOF,
                { lexer.parseDefinition() },
                EOF
            ),
            loc = lexer.loc(start)
        )
    }

    /**
     * Definition :
     *   - ExecutableDefinition
     *   - TypeSystemDefinition
     *   - TypeSystemExtension
     *
     * ExecutableDefinition :
     *   - OperationDefinition
     *   - FragmentDefinition
     */
    private fun Lexer.parseDefinition(): DefinitionNode {
        return when {
            peek(NAME) -> when (token.value) {
                "query", "mutation", "subscription" -> parseOperationDefinition()
                "fragment" -> parseFragmentDefinition()
                "schema", "scalar", "type", "interface", "union", "enum", "input", "directive" -> parseTypeSystemDefinition()
                "extend" -> throw NotImplementedError("Extend is not supported")
                else -> throw unexpected()
            }

            peek(BRACE_L) -> parseOperationDefinition()
            peekDescription() -> parseTypeSystemDefinition()
            else -> throw unexpected()
        }
    }

    /**
     * OperationDefinition :
     *  - SelectionSet
     *  - OperationType Name? VariableDefinitions? Directives? SelectionSet
     */
    private fun Lexer.parseOperationDefinition(): DefinitionNode.ExecutableDefinitionNode.OperationDefinitionNode {
        val start = token
        if (peek(BRACE_L)) {
            return DefinitionNode.ExecutableDefinitionNode.OperationDefinitionNode(
                operation = QUERY,
                name = null,
                variableDefinitions = listOf(),
                directives = listOf(),
                selectionSet = parseSelectionSet(null),
                loc = loc(start)
            )
        }
        val operation = parseOperationType()
        var name: NameNode? = null
        if (peek(NAME)) {
            name = parseName()
        }
        return DefinitionNode.ExecutableDefinitionNode.OperationDefinitionNode(
            operation = operation,
            name = name,
            variableDefinitions = parseVariableDefinitions(),
            directives = parseDirectives(false),
            selectionSet = parseSelectionSet(null),
            loc = loc(start)
        )
    }

    /**
     * OperationType : one of query mutation subscription
     */
    private fun Lexer.parseOperationType(): OperationTypeNode {
        val operationToken = expectToken(NAME)
        return when (operationToken.value) {
            "query" -> QUERY
            "mutation" -> MUTATION
            "subscription" -> SUBSCRIPTION
            else -> throw unexpected(operationToken)
        }
    }

    /**
     * VariableDefinitions : ( VariableDefinition+ )
     */
    private fun Lexer.parseVariableDefinitions() = optionalMany(
        PAREN_L,
        { parseVariableDefinition() },
        PAREN_R
    )

    /**
     * VariableDefinition : Variable : Type DefaultValue? Directives{Const}?
     */
    private fun Lexer.parseVariableDefinition(): VariableDefinitionNode {
        val start = token
        return VariableDefinitionNode(
            variable = parseVariable(),
            type = expectToken(COLON).let { parseTypeReference() },
            defaultValue = if (expectOptionalToken(EQUALS) != null) {
                parseValueLiteral(true)
            } else {
                null
            },
            directives = parseDirectives(true),
            loc = loc(start)
        )
    }

    /**
     * Variable : $ Name
     */
    private fun Lexer.parseVariable(): ValueNode.VariableNode {
        val start = token
        expectToken(DOLLAR)
        return ValueNode.VariableNode(
            name = parseName(),
            loc = loc(start)
        )
    }

    /**
     * SelectionSet : { Selection+ }
     */
    private fun Lexer.parseSelectionSet(parent: SelectionNode?): SelectionSetNode {
        val start = token
        return SelectionSetNode(
            selections = many(
                BRACE_L,
                { parseSelection(parent) },
                BRACE_R
            ),
            loc = loc(start)
        )
    }

    /**
     * Selection :
     *   - Field
     *   - FragmentSpread
     *   - InlineFragment
     */
    private fun Lexer.parseSelection(parent: SelectionNode?): SelectionNode {
        return if (peek(SPREAD)) parseFragment(parent) else parseField(parent)
    }

    /**
     * Field : Alias? Name Arguments? Directives? SelectionSet?
     *
     * Alias : Name :
     */
    private fun Lexer.parseField(parent: SelectionNode?): SelectionNode.FieldNode {
        val start = token

        val nameOrAlias = parseName()
        val alias: NameNode?
        val name: NameNode

        if (expectOptionalToken(COLON) != null) {
            alias = nameOrAlias
            name = parseName()
        } else {
            alias = null
            name = nameOrAlias
        }

        val newNode = SelectionNode.FieldNode(
            parent = parent,
            alias = alias,
            name = name,
            arguments = parseArguments(false),
            directives = parseDirectives(false)
        )
        return newNode.finalize(
            selectionSet = if (peek(BRACE_L)) parseSelectionSet(newNode) else null,
            loc = loc(start)
        )
    }

    /**
     * Arguments{Const} : ( Argument[?Const]+ )
     */
    private fun Lexer.parseArguments(isConst: Boolean): MutableList<ArgumentNode> {
        val item = if (isConst) ({ parseConstArgument() }) else ({ parseArgument() })
        return optionalMany(PAREN_L, item, PAREN_R)
    }

    /**
     * Argument{Const} : Name : Value[?Const]
     */
    private fun Lexer.parseArgument(): ArgumentNode {
        val start = token
        val name = parseName()

        expectToken(COLON)
        return ArgumentNode(
            name = name,
            value = parseValueLiteral(false),
            loc = loc(start)
        )
    }

    private fun Lexer.parseConstArgument(): ArgumentNode {
        val start = token
        return ArgumentNode(
            name = parseName(),
            value = expectToken(COLON).let { parseValueLiteral(true) },
            loc = loc(start)
        )
    }

    /**
     * Corresponds to both FragmentSpread and InlineFragment in the spec.
     *
     * FragmentSpread : ... FragmentName Directives?
     *
     * InlineFragment : ... TypeCondition? Directives? SelectionSet
     */
    private fun Lexer.parseFragment(parent: SelectionNode?): SelectionNode.FragmentNode {
        val start = token
        expectToken(SPREAD)

        val hasTypeCondition = expectOptionalKeyword("on")
        if (!hasTypeCondition && peek(NAME)) {
            return SelectionNode.FragmentNode.FragmentSpreadNode(
                parent = parent,
                name = parseFragmentName(),
                directives = parseDirectives(false),
                loc = loc(start)
            )
        }
        val newNode = SelectionNode.FragmentNode.InlineFragmentNode(
            parent = parent,
            typeCondition = if (hasTypeCondition) parseNamedType() else null,
            directives = parseDirectives(false)
        )

        return newNode.finalize(
            selectionSet = parseSelectionSet(newNode),
            loc = loc(start)
        )
    }

    /**
     * FragmentDefinition :
     *   - fragment FragmentName on TypeCondition Directives? SelectionSet
     *
     * TypeCondition : NamedType
     */
    private fun Lexer.parseFragmentDefinition(): DefinitionNode.ExecutableDefinitionNode.FragmentDefinitionNode {
        val start = token
        expectKeyword("fragment")
        return DefinitionNode.ExecutableDefinitionNode.FragmentDefinitionNode(
            name = parseFragmentName(),
            typeCondition = expectKeyword("on").let { parseNamedType() },
            directives = parseDirectives(false),
            selectionSet = parseSelectionSet(null),
            loc = loc(start)
        )
    }

    /**
     * FragmentName : Name but not `on`
     */
    private fun Lexer.parseFragmentName(): NameNode {
        if (token.value == "on") {
            throw unexpected()
        }
        return parseName()
    }

    /**
     * Value{Const} :
     *   - [~Const] Variable
     *   - IntValue
     *   - FloatValue
     *   - StringValue
     *   - BooleanValue
     *   - NullValue
     *   - EnumValue
     *   - ListValue[?Const]
     *   - ObjectValue[?Const]
     *
     * BooleanValue : one of `true` `false`
     *
     * NullValue : `null`
     *
     * EnumValue : Name but not `true`, `false` or `null`
     */
    internal fun Lexer.parseValueLiteral(isConst: Boolean): ValueNode {
        val token = token

        return when (token.kind) {
            BRACKET_L -> parseList(isConst)
            BRACE_L -> parseObject(isConst)
            INT -> {
                advance()
                ValueNode.NumberValueNode(
                    value = token.value!!,
                    loc = loc(token)
                )
            }

            FLOAT -> {
                advance()
                ValueNode.DoubleValueNode(
                    value = token.value!!,
                    loc = loc(token)
                )
            }

            STRING, BLOCK_STRING -> parseStringLiteral()
            NAME -> {
                if (token.value == "true" || token.value == "false") {
                    advance()
                    ValueNode.BooleanValueNode(
                        value = token.value == "true",
                        loc = loc(token)
                    )
                } else if (token.value == "null") {
                    advance()
                    ValueNode.NullValueNode(loc(token))
                } else {
                    advance()
                    ValueNode.EnumValueNode(
                        value = token.value!!,
                        loc = loc(token)
                    )
                }
            }

            DOLLAR -> if (!isConst) parseVariable() else throw unexpected()
            else -> throw unexpected()
        }
    }

    private fun Lexer.parseStringLiteral(): ValueNode.StringValueNode {
        val token = token
        advance()
        return ValueNode.StringValueNode(
            value = token.value!!,
            block = token.kind == BLOCK_STRING,
            loc = loc(token)
        )
    }

    /**
     * ListValue{Const} :
     *   - [ ]
     *   - [ Value[?Const]+ ]
     */
    private fun Lexer.parseList(isConst: Boolean): ValueNode.ListValueNode {
        val start = token
        val item = { parseValueLiteral(isConst) }

        return ValueNode.ListValueNode(
            values = any(BRACKET_L, item, BRACKET_R),
            loc = loc(start)
        )
    }

    /**
     * ObjectValue{Const} :
     *   - { }
     *   - { ObjectField[?Const]+ }
     */
    private fun Lexer.parseObject(isConst: Boolean): ValueNode.ObjectValueNode {
        val start = token
        val item = { parseObjectField(isConst) }
        return ValueNode.ObjectValueNode(
            fields = any(BRACE_L, item, BRACE_R),
            loc = loc(start)
        )
    }

    /**
     * ObjectField{Const} : Name : Value[?Const]
     */
    private fun Lexer.parseObjectField(isConst: Boolean): ValueNode.ObjectValueNode.ObjectFieldNode {
        val start = token
        val name = parseName()
        expectToken(COLON)

        return ValueNode.ObjectValueNode.ObjectFieldNode(
            name = name,
            value = parseValueLiteral(isConst),
            loc = loc(start)
        )
    }

    /**
     * Directives{Const} : Directive[?Const]+
     */
    private fun Lexer.parseDirectives(isConst: Boolean): MutableList<DirectiveNode> {
        val directives = mutableListOf<DirectiveNode>()
        while (peek(AT)) {
            directives.add(parseDirective(isConst))
        }
        return directives
    }

    /**
     * Directive{Const} : @ Name Arguments[?Const]?
     */
    private fun Lexer.parseDirective(isConst: Boolean): DirectiveNode {
        val start = token
        expectToken(AT)
        return DirectiveNode(
            name = parseName(),
            arguments = parseArguments(isConst),
            loc = loc(start)
        )
    }

    /**
     * Type :
     *   - NamedType
     *   - ListType
     *   - NonNullType
     */
    internal fun Lexer.parseTypeReference(): TypeNode {
        val start = token
        var type: TypeNode?
        if (expectOptionalToken(BRACKET_L) != null) {
            type = parseTypeReference()
            expectToken(BRACKET_R)
            type = TypeNode.ListTypeNode(
                type = type,
                loc = loc(start)
            )
        } else {
            type = parseNamedType()
        }

        if (expectOptionalToken(BANG) != null) {
            return TypeNode.NonNullTypeNode(
                type = type,
                loc = loc(start)
            )
        }
        return type
    }

    /**
     * NamedType : Name
     */
    private fun Lexer.parseNamedType(): TypeNode.NamedTypeNode {
        val start = token
        return TypeNode.NamedTypeNode(
            name = parseName(),
            loc = loc(start)
        )
    }

    /**
     * TypeSystemDefinition :
     *   - SchemaDefinition
     *   - TypeDefinition
     *   - DirectiveDefinition
     *
     * TypeDefinition :
     *   - ScalarTypeDefinition
     *   - ObjectTypeDefinition
     *   - InterfaceTypeDefinition
     *   - UnionTypeDefinition
     *   - EnumTypeDefinition
     *   - InputObjectTypeDefinition
     */
    private fun Lexer.parseTypeSystemDefinition(): DefinitionNode.TypeSystemDefinitionNode {
        // Many definitions begin with a description and require a lookahead.
        val keywordToken = if (peekDescription()) {
            lookahead()
        } else {
            token
        }

        if (keywordToken.kind == NAME) {
            return when (keywordToken.value) {
                "schema" -> parseSchemaDefinition()
                "scalar" -> parseScalarTypeDefinition()
                "type" -> parseObjectTypeDefinition()
                "interface" -> parseInterfaceTypeDefinition()
                "union" -> parseUnionTypeDefinition()
                "enum" -> parseEnumTypeDefinition()
                "input" -> parseInputObjectTypeDefinition()
                "directive" -> parseDirectiveDefinition()
                else -> throw unexpected(keywordToken)
            }
        }
        throw unexpected(keywordToken)
    }

    private fun Lexer.peekDescription() = peek(STRING) || peek(BLOCK_STRING)

    /**
     * Description : StringValue
     */
    private fun Lexer.parseDescription() = if (peekDescription()) {
        parseStringLiteral()
    } else {
        null
    }

    /**
     * SchemaDefinition : schema Directives{Const}? { OperationTypeDefinition+ }
     */
    private fun Lexer.parseSchemaDefinition(): DefinitionNode.TypeSystemDefinitionNode.SchemaDefinitionNode {
        val start = token
        expectKeyword("schema")
        val directives = parseDirectives(true)
        val operationTypes = many(
            BRACE_L,
            { parseOperationTypeDefinition() },
            BRACE_R
        )
        return DefinitionNode.TypeSystemDefinitionNode.SchemaDefinitionNode(
            directives = directives,
            operationTypes = operationTypes,
            loc = loc(start)
        )
    }

    /**
     * OperationTypeDefinition : OperationType : NamedType
     */
    private fun Lexer.parseOperationTypeDefinition(): OperationTypeDefinitionNode {
        val start = token
        val operation = parseOperationType()
        expectToken(COLON)
        val type = parseNamedType()
        return OperationTypeDefinitionNode(
            operation = operation,
            type = type,
            loc = loc(start)
        )
    }

    /**
     * ScalarTypeDefinition : Description? scalar Name Directives{Const}?
     */
    private fun Lexer.parseScalarTypeDefinition(): DefinitionNode.TypeSystemDefinitionNode.TypeDefinitionNode.ScalarTypeDefinitionNode {
        val start = token
        val description = parseDescription()
        expectKeyword("scalar")
        val name = parseName()
        val directives = parseDirectives(true)
        return DefinitionNode.TypeSystemDefinitionNode.TypeDefinitionNode.ScalarTypeDefinitionNode(
            description = description,
            name = name,
            directives = directives,
            loc = loc(start)
        )
    }

    /**
     * ObjectTypeDefinition :
     *   Description?
     *   type Name ImplementsInterfaces? Directives{Const}? FieldsDefinition?
     */
    private fun Lexer.parseObjectTypeDefinition(): DefinitionNode.TypeSystemDefinitionNode.TypeDefinitionNode.ObjectTypeDefinitionNode {
        val start = token
        val description = parseDescription()
        expectKeyword("type")
        val name = parseName()
        val interfaces = parseImplementsInterfaces()
        val directives = parseDirectives(true)
        val fields = parseFieldsDefinition()
        return DefinitionNode.TypeSystemDefinitionNode.TypeDefinitionNode.ObjectTypeDefinitionNode(
            description = description,
            name = name,
            interfaces = interfaces,
            directives = directives,
            fields = fields,
            loc = loc(start)
        )
    }

    /**
     * ImplementsInterfaces :
     *   - implements `&`? NamedType
     *   - ImplementsInterfaces & NamedType
     */
    private fun Lexer.parseImplementsInterfaces(): MutableList<TypeNode.NamedTypeNode> {
        val types = mutableListOf<TypeNode.NamedTypeNode>()
        if (expectOptionalKeyword("implements")) {
            // Optional leading ampersand
            expectOptionalToken(AMP)
            do {
                types.add(parseNamedType())
            } while (
                expectOptionalToken(AMP) != null ||
                // Legacy support for the SDL?
                (this@Parser.options.allowLegacySDLImplementsInterfaces && peek(NAME))
            )
        }
        return types
    }

    /**
     * FieldsDefinition : { FieldDefinition+ }
     */
    private fun Lexer.parseFieldsDefinition(): MutableList<FieldDefinitionNode> {
        // Legacy support for the SDL?
        if (
            this@Parser.options.allowLegacySDLEmptyFields &&
            peek(BRACE_L) &&
            lookahead().kind == BRACE_R
        ) {
            advance()
            advance()
            return mutableListOf()
        }
        return optionalMany(
            BRACE_L,
            { parseFieldDefinition() },
            BRACE_R
        )
    }

    /**
     * FieldDefinition :
     *   - Description? Name ArgumentsDefinition? : Type Directives{Const}?
     */
    private fun Lexer.parseFieldDefinition(): FieldDefinitionNode {
        val start = token
        val description = parseDescription()
        val name = parseName()
        val args = parseArgumentDefs()
        expectToken(COLON)
        val type = parseTypeReference()
        val directives = parseDirectives(true)

        return FieldDefinitionNode(
            description = description,
            name = name,
            arguments = args,
            type = type,
            directives = directives,
            loc = loc(start)
        )
    }

    /**
     * ArgumentsDefinition : ( InputValueDefinition+ )
     */
    private fun Lexer.parseArgumentDefs() = optionalMany(
        PAREN_L,
        { parseInputValueDef() },
        PAREN_R
    )

    /**
     * InputValueDefinition :
     *   - Description? Name ArgumentsDefinition? : Type DefaultValue? Directives{Const}?
     */
    private fun Lexer.parseInputValueDef(): InputValueDefinitionNode {
        val start = token
        val description = parseDescription()
        val name = parseName()
        val args = parseArgumentDefs()
        expectToken(COLON)
        val type = parseTypeReference()
        val defaultValue = expectOptionalToken(EQUALS)?.let { parseValueLiteral(true) }
        val directives = parseDirectives(true)

        return InputValueDefinitionNode(
            description = description,
            arguments = args,
            name = name,
            type = type,
            defaultValue = defaultValue,
            directives = directives,
            loc = loc(start)
        )
    }

    /**
     * InterfaceTypeDefinition :
     *   - Description? interface Name Directives{Const}? FieldsDefinition?
     */
    private fun Lexer.parseInterfaceTypeDefinition(): DefinitionNode.TypeSystemDefinitionNode.TypeDefinitionNode.InterfaceTypeDefinitionNode {
        val start = token
        val description = parseDescription()
        expectKeyword("interface")
        val name = parseName()
        val directives = parseDirectives(true)
        val fields = parseFieldsDefinition()

        return DefinitionNode.TypeSystemDefinitionNode.TypeDefinitionNode.InterfaceTypeDefinitionNode(
            description = description,
            name = name,
            directives = directives,
            fields = fields,
            loc = loc(start)
        )
    }

    /**
     * UnionTypeDefinition :
     *   - Description? union Name Directives{Const}? UnionMemberTypes?
     */
    private fun Lexer.parseUnionTypeDefinition(): DefinitionNode.TypeSystemDefinitionNode.TypeDefinitionNode.UnionTypeDefinitionNode {
        val start = token
        val description = parseDescription()
        expectKeyword("union")
        val name = parseName()
        val directives = parseDirectives(true)
        val types = parseUnionMemberTypes()

        return DefinitionNode.TypeSystemDefinitionNode.TypeDefinitionNode.UnionTypeDefinitionNode(
            description = description,
            name = name,
            directives = directives,
            types = types,
            loc = loc(start)
        )
    }

    /**
     * UnionMemberTypes :
     *   - = `|`? NamedType
     *   - UnionMemberTypes | NamedType
     */
    private fun Lexer.parseUnionMemberTypes(): MutableList<TypeNode.NamedTypeNode> {
        val types = mutableListOf<TypeNode.NamedTypeNode>()
        if (expectOptionalToken(EQUALS) != null) {
            // Optional leading pipe
            expectOptionalToken(PIPE)
            do {
                types.add(parseNamedType())
            } while (expectOptionalToken(PIPE) != null)
        }
        return types
    }

    /**
     * EnumTypeDefinition :
     *   - Description? enum Name Directives{Const}? EnumValuesDefinition?
     */
    private fun Lexer.parseEnumTypeDefinition(): DefinitionNode.TypeSystemDefinitionNode.TypeDefinitionNode.EnumTypeDefinitionNode {
        val start = token
        val description = parseDescription()
        expectKeyword("enum")
        val name = parseName()
        val directives = parseDirectives(true)
        val values = parseEnumValuesDefinition()
        return DefinitionNode.TypeSystemDefinitionNode.TypeDefinitionNode.EnumTypeDefinitionNode(
            description = description,
            name = name,
            directives = directives,
            values = values,
            loc = loc(start)
        )
    }

    /**
     * EnumValuesDefinition : { EnumValueDefinition+ }
     */
    private fun Lexer.parseEnumValuesDefinition() = optionalMany(
        BRACE_L,
        { parseEnumValueDefinition() },
        BRACE_R
    )

    /**
     * EnumValueDefinition : Description? EnumValue Directives{Const}?
     *
     * EnumValue : Name
     */
    private fun Lexer.parseEnumValueDefinition(): EnumValueDefinitionNode {
        val start = token
        val description = parseDescription()
        val name = parseName()
        val directives = parseDirectives(true)

        return EnumValueDefinitionNode(
            description = description,
            name = name,
            directives = directives,
            loc = loc(start)
        )
    }

    /**
     * InputObjectTypeDefinition :
     *   - Description? input Name Directives{Const}? InputFieldsDefinition?
     */
    private fun Lexer.parseInputObjectTypeDefinition(): DefinitionNode.TypeSystemDefinitionNode.TypeDefinitionNode.InputObjectTypeDefinitionNode {
        val start = token
        val description = parseDescription()
        expectKeyword("input")
        val name = parseName()
        val directives = parseDirectives(true)
        val fields = parseInputFieldsDefinition()

        return DefinitionNode.TypeSystemDefinitionNode.TypeDefinitionNode.InputObjectTypeDefinitionNode(
            description = description,
            name = name,
            directives = directives,
            fields = fields,
            loc = loc(start)
        )
    }

    /**
     * InputFieldsDefinition : { InputValueDefinition+ }
     */
    private fun Lexer.parseInputFieldsDefinition() = optionalMany(
        BRACE_L,
        { parseInputValueDef() },
        BRACE_R
    )

    /**
     * DirectiveDefinition :
     *   - Description? directive @ Name ArgumentsDefinition? `repeatable`? on DirectiveLocations
     */
    private fun Lexer.parseDirectiveDefinition(): DefinitionNode.TypeSystemDefinitionNode.DirectiveDefinitionNode {
        val start = token
        val description = parseDescription()
        expectKeyword("directive")
        expectToken(AT)
        val name = parseName()
        val args = parseArgumentDefs()
        val repeatable = expectOptionalKeyword("repeatable")
        expectKeyword("on")
        val locations = parseDirectiveLocations()

        return DefinitionNode.TypeSystemDefinitionNode.DirectiveDefinitionNode(
            description = description,
            name = name,
            arguments = args,
            repeatable = repeatable,
            locations = locations,
            loc = loc(start)
        )
    }

    /**
     * DirectiveLocations :
     *   - `|`? DirectiveLocation
     *   - DirectiveLocations | DirectiveLocation
     */
    private fun Lexer.parseDirectiveLocations(): MutableList<NameNode> {
        // Optional leading pipe
        expectOptionalToken(PIPE)
        val locations = mutableListOf<NameNode>()
        do {
            locations.add(parseDirectiveLocation())
        } while (expectOptionalToken(PIPE) != null)
        return locations
    }

    /*
     * DirectiveLocation :
     *   - ExecutableDirectiveLocation
     *   - TypeSystemDirectiveLocation
     *
     * ExecutableDirectiveLocation : one of
     *   `QUERY`
     *   `MUTATION`
     *   `SUBSCRIPTION`
     *   `FIELD`
     *   `FRAGMENT_DEFINITION`
     *   `FRAGMENT_SPREAD`
     *   `INLINE_FRAGMENT`
     *   `VARIABLE_DEFINITION`
     *
     * TypeSystemDirectiveLocation : one of
     *   `SCHEMA`
     *   `SCALAR`
     *   `OBJECT`
     *   `FIELD_DEFINITION`
     *   `ARGUMENT_DEFINITION`
     *   `INTERFACE`
     *   `UNION`
     *   `ENUM`
     *   `ENUM_VALUE`
     *   `INPUT_OBJECT`
     *   `INPUT_FIELD_DEFINITION`
     */
    private fun Lexer.parseDirectiveLocation(): NameNode {
        val start = token
        val name = parseName()
        if (DirectiveLocation.from(name.value) != null) {
            return name
        }
        throw unexpected(start)
    }

    /**
     * Returns a location object, used to identify the place in
     * the source that created a given parsed object.
     */
    private fun Lexer.loc(startToken: Token): Location? {
        if (this@Parser.options.noLocation != true) {
            return Location(startToken, lastToken, source)
        }
        return null
    }

    /**
     * Determines if the next token is of a given kind
     */
    private fun Lexer.peek(kind: TokenKindEnum) = token.kind == kind

    /**
     * If the next token is of the given kind, return that token after advancing
     * the lexer. Otherwise, do not change the parser state and throw an error.
     */
    internal fun Lexer.expectToken(kind: TokenKindEnum): Token {
        val token = token
        if (token.kind == kind) {
            advance()
            return token
        }

        throw syntaxError(
            source,
            token.start,
            "Expected ${getTokenKindDesc(kind)}, found ${
                getTokenDesc(
                    token
                )
            }."
        )
    }

    /**
     * If the next token is of the given kind, return that token after advancing
     * the lexer. Otherwise, do not change the parser state and return undefined.
     */
    private fun Lexer.expectOptionalToken(kind: TokenKindEnum): Token? {
        val token = token
        if (token.kind == kind) {
            advance()
            return token
        }
        return null
    }

    /**
     * If the next token is a given keyword, advance the lexer.
     * Otherwise, do not change the parser state and throw an error.
     */
    private fun Lexer.expectKeyword(value: String) {
        val token = token
        if (token.kind == NAME && token.value == value) {
            advance()
        } else {
            throw syntaxError(
                source,
                token.start,
                "Expected \"${value}\", found ${getTokenDesc(token)}."
            )
        }
    }

    /**
     * If the next token is a given keyword, return "true" after advancing
     * the lexer. Otherwise, do not change the parser state and return "false".
     */
    private fun Lexer.expectOptionalKeyword(value: String): Boolean {
        val token = token
        if (token.kind == NAME && token.value == value) {
            advance()
            return true
        }
        return false
    }

    /**
     * Helper function for creating an error when an unexpected lexed token
     * is encountered.
     */
    private fun Lexer.unexpected(token: Token = this.token) = syntaxError(
        source,
        token.start,
        "Unexpected ${getTokenDesc(token)}."
    )

    /**
     * Returns a possibly empty list of parse nodes, determined by
     * the parseFn. This list begins with a lex token of openKind
     * and ends with a lex token of closeKind. Advances the parser
     * to the next lex token after the closing token.
     */
    private fun <T> Lexer.any(
        openKind: TokenKindEnum,
        parseFn: () -> T,
        closeKind: TokenKindEnum
    ): MutableList<T> {
        expectToken(openKind)
        val nodes = mutableListOf<T>()
        while (expectOptionalToken(closeKind) == null) {
            nodes.add(parseFn())
        }
        return nodes
    }

    /**
     * Returns a list of parse nodes, determined by the parseFn.
     * It can be empty only if open token is missing otherwise it will always
     * return non-empty list that begins with a lex token of openKind and ends
     * with a lex token of closeKind. Advances the parser to the next lex token
     * after the closing token.
     */
    private fun <T> Lexer.optionalMany(
        openKind: TokenKindEnum,
        parseFn: () -> T,
        closeKind: TokenKindEnum
    ): MutableList<T> {
        if (expectOptionalToken(openKind) != null) {
            val nodes = mutableListOf<T>()
            do {
                nodes.add(parseFn())
            } while (expectOptionalToken(closeKind) == null)
            return nodes
        }
        return mutableListOf()
    }

    /**
     * Returns a non-empty list of parse nodes, determined by
     * the parseFn. This list begins with a lex token of openKind
     * and ends with a lex token of closeKind. Advances the parser
     * to the next lex token after the closing token.
     */
    private fun <T> Lexer.many(
        openKind: TokenKindEnum,
        parseFn: () -> T,
        closeKind: TokenKindEnum
    ): MutableList<T> {
        expectToken(openKind)
        val nodes = mutableListOf<T>()
        do {
            nodes.add(parseFn())
        } while (expectOptionalToken(closeKind) == null)
        return nodes
    }

    /**
     * Configuration options to control parser behavior
     */
    data class Options(
        /**
         * By default, the parser creates AST nodes that know the location
         * in the source that they correspond to. This configuration flag
         * disables that behavior for performance or testing.
         */
        val noLocation: Boolean? = null,

        /**
         * If enabled, the parser will parse empty fields sets in the Schema
         * Definition Language. Otherwise, the parser will follow the current
         * specification.
         *
         * This option is provided to ease adoption of the final SDL specification
         * and will be removed in v16.
         */
        val allowLegacySDLEmptyFields: Boolean = false,

        /**
         * If enabled, the parser will parse implemented interfaces with no `&`
         * character between each interface. Otherwise, the parser will follow the
         * current specification.
         *
         * This option is provided to ease adoption of the final SDL specification
         * and will be removed in v16.
         */
        val allowLegacySDLImplementsInterfaces: Boolean = false
    )

    companion object {

        /**
         * A helper function to describe a token as a string for debugging
         */
        fun getTokenDesc(token: Token): String {
            val value = token.value
            return getTokenKindDesc(token.kind) + (if (value != null) " \"$value\"" else "")
        }

        /**
         * A helper function to describe a token kind as a string for debugging
         */
        fun getTokenKindDesc(kind: TokenKindEnum): String {
            return if (kind.isPunctuatorTokenKind) "\"$kind\"" else kind.str
        }
    }
}
