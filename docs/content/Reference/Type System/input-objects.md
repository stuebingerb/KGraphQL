# Input Objects

A GraphQL Input Object defines a set of input fields; the input fields are either scalars, enums, or other input
objects. Like Object and Interface types, Input Object types are inferred from defined operations, but can be explicitly
declared as well.

## Schema

Input Objects take their fields from the primary constructor, supporting property and non-property parameters.

=== "Example"
    ```kotlin
    // Non-data class with a constructor parameter that is not a property
    class NonDataClass(param1: String = "Hello", val param3: Boolean?) {
        var param2: Int = param1.length
    }
    
    val schema = KGraphQL.schema {
        query("test") {
            resolver { input: NonDataClass -> input }
        }
    }
    ```
=== "SDL"
    ```graphql
    type NonDataClass {
      param2: Int!
      param3: Boolean
    }
    
    type Query {
      test(input: NonDataClassInput!): NonDataClass!
    }
    
    input NonDataClassInput {
      param1: String!
      param3: Boolean
    }
    ```

## Input Object Name

To avoid equal names between input and query types, Input Objects get an automated `"Input"` suffix unless their name
already ends with `"Input"` or they have explicit configuration in the DSL via `inputType {}`.

In this case, the Input Object `Type` will automatically be renamed to `TypeInput` but `FooInput` will not become
`FooInputInput`:

=== "Example"
    ```kotlin
    class Type(val name: String)
    data class FooInput(val name: String)
    data class Foo(val name: String)

    val schema = KGraphQL.schema {
        query("getFoo") {
            resolver { -> Foo("bar") }
        }
        mutation("addType") {
            resolver { input: Type -> input }
        }
        mutation("addFoo") {
            resolver { input: FooInput -> Foo(input.name) }
        }
    }
    ```
=== "SDL"
    ```graphql
    type Foo {
      name: String!
    }
    
    type Mutation {
      addFoo(input: FooInput!): Foo!
      addType(input: TypeInput!): Type!
    }
    
    type Query {
      getFoo: Foo!
    }
    
    type Type {
      name: String!
    }
    
    input FooInput {
      name: String!
    }
    
    input TypeInput {
      name: String!
    }
    ```

This behavior applies recursively to nested types as well. The nested `ChildType` will also be renamed to `ChildTypeInput`:

=== "Example"
    ```kotlin
    class ChildType(val childName: String)
    class ParentType(val parentName: String, val child: ChildType)
    
    val schema = KGraphQL.schema {
        query("getParent") {
            resolver { -> ParentType("parent", ChildType("child")) }
        }
        mutation("addParent") {
            resolver { input: ParentType -> input }
        }
    }
    ```
=== "SDL"
    ```graphql
    type ChildType {
      childName: String!
    }
    
    type Mutation {
      addParent(input: ParentTypeInput!): ParentType!
    }
    
    type ParentType {
      child: ChildType!
      parentName: String!
    }
    
    type Query {
      getParent: ParentType!
    }
    
    input ChildTypeInput {
      childName: String!
    }
    
    input ParentTypeInput {
      child: ChildTypeInput!
      parentName: String!
    }
    ```

## OneOf Input Objects

Sealed classes and interfaces, or Input Objects configured with `isOneOf = true` in the `inputType` DSL result in a
[OneOf Input Object](https://spec.graphql.org/September2025/#sec-OneOf-Input-Objects).

OneOf Input Objects are a special variant of an Input Object where exactly one field must be set and non-null, all others being omitted.
This is useful for representing situations where an input may be one of many different options.

All fields of a OneOf Input Object must be nullable, and must not have default values.

### Use Cases

**Simplified Entrypoints**

For example, you might find a user by their ID, email address, or username. Traditionally, that meant multiple root-level fields:

=== "SDL"
    ```graphql
    type Query {
      user(id: ID!): User
      userByEmail(email: String!): User
      userByUsername(username: String!): User
    }
    ```

With oneOf this can now be represented in a more concise and user-friendly way:

=== "SDL"
    ```graphql
    input UserBy @oneOf {
      id: ID
      email: String
      username: String
    }

    type Query {
      user(by: UserBy!): User
    }
    ```

**Polymorph Inputs**

Imagine a blogging website with posts that consist of different types of content, such as paragraphs, images, and quotes.
OneOf Input Objects allow you to define a mutation that accepts a type-safe list of such elements:

=== "SDL"
    ```graphql
    type Mutation {
      createPost(elements: [PostElementInput]): Post
    }
    
    input PostElementInput @oneOf {
      paragraph: ParagraphInput
      blockquote: BlockQuoteInput
      gallery: GalleryInput
    }
    
    input ParagraphInput {
      text: String!
    }
    
    input BlockQuoteInput {
      text: String!
      attribution: String
      attributionUrl: String
    }
    
    input GalleryInput {
      imageUrls: [String!]!
      caption: String
      attribution: String
    }
    ```

## Runtime

Input Objects are instantiated via their primary constructor. Kotlin default values are used unless a different value
is provided explicitly. Due to a limitation in Kotlin, such default values are not visible in the generated schema, though.

OneOf Input Objects out of sealed classes or interfaces will by default have one field for each allowed subtype, with
the name of the Kotlin class as lowercased name. Input coercion will result in the corresponding actual sub type passed
to the resolver.

=== "Example"
    ```kotlin
    sealed interface PostElement
    data class Paragraph(val text: String) : PostElement
    data class BlockQuote(val text: String, val attribution: String?, val attributionUrl: String?) : PostElement
    data class Gallery(val imageUrls: List<String>, val caption: String?, val attribution: String?) : PostElement

    val schema = KGraphQL.schema {
        mutation("createPost") {
            // will receive a list of BlockQuote, Paragraph, or Gallery instances
            resolver { elements: List<PostElement> ->
                elements.forEach { element ->
                    when (element) {
                        is Paragraph -> {
                            // handle paragraph
                        }
                        is BlockQuote -> {
                            // handle blockquote
                        }
                        is Gallery -> {
                            // handle gallery
                        }
                    }
                }
            }
        }
    }
    ```

OneOf Input Objects out of regular classes will have exactly one input field set and non-null.

=== "Example"
    ```kotlin
    data class UserBy(val id: ID?, val email: String?, val username: String?)

    val schema = KGraphQL.schema {
        inputType<UserBy> {
            isOneOf = true
        }
        query("user") {
            // will receive an instance of UserBy with exactly one non-null property
            resolver { by: UserBy ->
                if (by.id != null) {
                    // find user by ID
                } else if (by.email != null) {
                    // find user by email
                } else if (by.username != null) {
                    // find user by username
                } else {
                    // not possible
                }
            }
        }
    }
    ```

## Configuration

Input Objects can be configured via the `inputType` DSL. This can be used to set a custom name, description, or
deprecate or hide certain fields.

=== "Example"
    ```kotlin
    data class InputObject(val foo: String?, val bar: String?)
    
    val schema = KGraphQL.schema {
        query("test") {
            resolver { input: InputObject -> input.foo }
        }
        inputType<InputObject> {
            name = "MyInputObject"
            description = "Description for input object"
        
            InputObject::foo.configure {
                deprecate("Deprecated old foo")
            }
            InputObject::bar.configure {
                name = "newFoo"
                description = "The new foo"
            }
        }
    }
    ```
=== "SDL"
    ```graphql
    "Query object"
    type Query {
        test(input: MyInputObject!): String
    }
    
    "Description for input object"
    input MyInputObject {
        foo: String @deprecated(reason: "Deprecated old foo")
        "The new foo"
        newFoo: String
    }
    ```

OneOf Input Objects can exclude certain subtypes via `ignore`, or specify a custom name, description, or deprecation.

=== "Example"
    ```kotlin
    sealed class SealedInput(val foo: String)
    data class SealedInputSub1(val bar: String) : SealedInput("bar")
    data class SealedInputSub2(val foobar: Int) : SealedInput("foobar")
    data class SealedInputSub3(val barfoo: Boolean) : SealedInput("barfoo")
    
    val schema = KGraphQL.schema {
        query("test") {
            resolver { input: SealedInput -> input.foo }
        }
        inputType<SealedInput> {
            SealedInputSub1::class.configure {
                name = "sub"
            }
            SealedInputSub2::class.configure {
                name = "otherSub"
                description = "Some description"
                deprecate("Deprecated for testing purposes")
            }
            SealedInputSub3::class.configure {
                ignore = true
            }
        }
    } 
    ```
=== "SDL"
    ```graphql
    "Query object"
    type Query {
        test(input: SealedInput!): String!
    }
    
    input SealedInput @oneOf {
        "Some description"
        otherSub: SealedInputSub2Input @deprecated(reason: "Deprecated for testing purposes")
        sub: SealedInputSub1Input
    }
    
    input SealedInputSub1Input {
        bar: String!
    }
    
    input SealedInputSub2Input {
        foobar: Int!
    }
    ```

Whenever an Input Object is configured via DSL the automatic suffix will *not* be appended, regardless of the name.

Here, both `InputType` and `ParentType` are explicitly configured, and therefore will keep their original name (a custom
`name` is not required).

=== "Example"
    ```kotlin
    class InputType(val name: String)
    class Type(val name: String)
    class ChildType(val childName: String)
    class ParentType(val parentName: String, val child: ChildType)
    
    val schema = KGraphQL.schema {
        query("getType") {
            resolver { -> Type("type") }
        }
        mutation("addType") {
            resolver { input: InputType -> Type(input.name) }
        }
        mutation("addParent") {
            resolver { input: ParentType -> input }
        }
        inputType<InputType>()
        inputType<ParentType> {
            name = "MyParentInputType"
        }
    }
    ```
=== "SDL"
    ```graphql
    type ChildType {
      childName: String!
    }
    
    type Mutation {
      addParent(input: MyParentInputType!): ParentType!
      addType(input: InputType!): Type!
    }
    
    type ParentType {
      child: ChildType!
      parentName: String!
    }
    
    type Query {
      getType: Type!
    }
    
    type Type {
      name: String!
    }
    
    input ChildTypeInput {
      childName: String!
    }
    
    input InputType {
      name: String!
    }
    
    input MyParentInputType {
      child: ChildTypeInput!
      parentName: String!
    }
    ```

## Limitations

Although non-property constructor parameters are supported, it is not possible to add a description or deprecate them.
