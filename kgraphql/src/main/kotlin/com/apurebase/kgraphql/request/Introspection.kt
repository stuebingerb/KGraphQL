package com.apurebase.kgraphql.request

import com.apurebase.kgraphql.schema.directive.DirectiveLocation
import com.apurebase.kgraphql.schema.introspection.TypeKind
import com.apurebase.kgraphql.schema.introspection.__Directive
import com.apurebase.kgraphql.schema.introspection.__EnumValue
import com.apurebase.kgraphql.schema.introspection.__Field
import com.apurebase.kgraphql.schema.introspection.__InputValue
import com.apurebase.kgraphql.schema.introspection.__Schema
import com.apurebase.kgraphql.schema.introspection.__Type
import com.apurebase.kgraphql.schema.model.TypeDef

/**
 * Functionality regarding schema introspection and its types.
 */
object Introspection {
    /**
     * Level of specification according to https://spec.graphql.org/. Different levels
     * will support different features, and the introspection query should adapt.
     */
    enum class SpecLevel {
        October2021, WorkingDraft
    }

    /**
     * Returns whether the given [request] is considered an introspection query, i.e.
     * contains any of ("__schema", "__type").
     */
    fun isIntrospection(request: String) = request.contains("__schema") || request.contains("__type")

    /**
     * Returns the introspection query corresponding to the given [specLevel].
     */
    fun query(specLevel: SpecLevel = SpecLevel.October2021) = when (specLevel) {
        // Default introspection query from GraphiQL
        SpecLevel.October2021 ->
            """
            query IntrospectionQuery {
              __schema {
                queryType { name }
                mutationType { name }
                subscriptionType { name }
                types {
                  ...FullType
                }
                directives {
                  name
                  description
                  locations
                  args {
                    ...InputValue
                  }
                }
              }
            }
            
            fragment FullType on __Type {
              kind
              name
              description
              fields(includeDeprecated: true) {
                name
                description
                args {
                  ...InputValue
                }
                type {
                  ...TypeRef
                }
                isDeprecated
                deprecationReason
              }
              inputFields {
                ...InputValue
              }
              interfaces {
                ...TypeRef
              }
              enumValues(includeDeprecated: true) {
                name
                description
                isDeprecated
                deprecationReason
              }
              possibleTypes {
                ...TypeRef
              }
            }
            
            fragment InputValue on __InputValue {
              name
              description
              type { ...TypeRef }
              defaultValue
            }
            
            fragment TypeRef on __Type {
              kind
              name
              ofType {
                kind
                name
                ofType {
                  kind
                  name
                  ofType {
                    kind
                    name
                    ofType {
                      kind
                      name
                      ofType {
                        kind
                        name
                        ofType {
                          kind
                          name
                          ofType {
                            kind
                            name
                            ofType {
                              kind
                              name
                              ofType {
                                kind
                                name
                              }
                            }
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
            """

        // SpecLevel.October2021 with supported extensions from the current draft
        SpecLevel.WorkingDraft ->
            """
            query IntrospectionQuery {
              __schema {
                queryType { name }
                mutationType { name }
                subscriptionType { name }
                types {
                  ...FullType
                }
                directives {
                  name
                  description
                  locations
                  args(includeDeprecated: true) {
                    ...InputValue
                  }
                  isRepeatable
                }
              }
            }
            
            fragment FullType on __Type {
              kind
              name
              description
              fields(includeDeprecated: true) {
                name
                description
                args(includeDeprecated: true) {
                  ...InputValue
                }
                type {
                  ...TypeRef
                }
                isDeprecated
                deprecationReason
              }
              inputFields(includeDeprecated: true) {
                ...InputValue
              }
              interfaces {
                ...TypeRef
              }
              enumValues(includeDeprecated: true) {
                name
                description
                isDeprecated
                deprecationReason
              }
              possibleTypes {
                ...TypeRef
              }
            }
            
            fragment InputValue on __InputValue {
              name
              description
              type { ...TypeRef }
              defaultValue
              isDeprecated
              deprecationReason
            }
            
            fragment TypeRef on __Type {
              kind
              name
              ofType {
                kind
                name
                ofType {
                  kind
                  name
                  ofType {
                    kind
                    name
                    ofType {
                      kind
                      name
                      ofType {
                        kind
                        name
                        ofType {
                          kind
                          name
                          ofType {
                            kind
                            name
                            ofType {
                              kind
                              name
                              ofType {
                                kind
                                name
                              }
                            }
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
            """
    }
}

/**
 * Returns whether the current [__Type] is an introspection type (i.e. has a name starting with "__")
 */
internal fun __Type.isIntrospectionType() = name?.startsWith("__") == true

private val introspectionTypes = setOf(
    __Schema::class,
    __Directive::class,
    __InputValue::class,
    __Type::class,
    __EnumValue::class,
    __Field::class,
    TypeKind::class,
    DirectiveLocation::class
)

/**
 * Returns whether the current [TypeDef] is an introspection type from [introspectionTypes]
 */
internal fun TypeDef.isIntrospectionType() = (this as? TypeDef.Kotlin<*>)?.kClass in introspectionTypes
