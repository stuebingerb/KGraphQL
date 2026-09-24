package de.stuebingerb.kgraphql.integration

import de.stuebingerb.kgraphql.InvalidInputValueException
import de.stuebingerb.kgraphql.KGraphQL
import de.stuebingerb.kgraphql.ValidationException
import de.stuebingerb.kgraphql.expect
import de.stuebingerb.kgraphql.expectExecutionError
import de.stuebingerb.kgraphql.expectRequestError
import de.stuebingerb.kgraphql.schema.SchemaException
import de.stuebingerb.kgraphql.schema.scalar.ID
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class InputObjectTest {
    data class Person(val name: String, val age: Int)

    @Test
    fun `property name should default to Kotlin name`() {
        val schema = KGraphQL.schema {
            query("getPerson") {
                resolver { name: String -> Person(name = name, age = 42) }
            }

            mutation("addPerson") {
                resolver { person: Person -> person }
            }
        }

        val sdl = schema.printSchema()
        sdl shouldBe """
            type Mutation {
              addPerson(person: PersonInput!): Person!
            }
            
            type Person {
              age: Int!
              name: String!
            }
            
            type Query {
              getPerson(name: String!): Person!
            }
            
            input PersonInput {
              age: Int!
              name: String!
            }
            
        """.trimIndent()

        schema.executeBlocking(
            """
            query {
              getPerson(name: "foo") { name age }
            }
        """.trimIndent()
        ) shouldBe """
            {"data":{"getPerson":{"name":"foo","age":42}}}
        """.trimIndent()

        schema.executeBlocking(
            """
            mutation {
              addPerson(person: { name: "bar", age: 20 }) { name age }
            }
        """.trimIndent()
        ) shouldBe """
            {"data":{"addPerson":{"name":"bar","age":20}}}
        """.trimIndent()

        val variables = """
            { "person": { "name": "foobar", "age": 60 } }
        """.trimIndent()
        schema.executeBlocking(
            """
            mutation(${'$'}person: PersonInput!) {
              addPerson(person: ${'$'}person) { name age }
            }
        """.trimIndent(),
            variables = variables
        ) shouldBe """
            {"data":{"addPerson":{"name":"foobar","age":60}}}
        """.trimIndent()
    }

    @Test
    fun `property name should be configurable`() {
        val schema = KGraphQL.schema {
            inputType<Person> {
                name = "PersonInput"
                property(Person::age) {
                    name = "inputAge"
                }
                property(Person::name) {
                    name = "inputName"
                }
            }

            query("getPerson") {
                resolver { name: String -> Person(name = name, age = 42) }
            }

            mutation("addPerson") {
                resolver { person: Person -> person }
            }
        }

        val sdl = schema.printSchema()
        sdl shouldBe """
            type Mutation {
              addPerson(person: PersonInput!): Person!
            }
            
            type Person {
              age: Int!
              name: String!
            }
            
            type Query {
              getPerson(name: String!): Person!
            }
            
            input PersonInput {
              inputAge: Int!
              inputName: String!
            }
            
        """.trimIndent()

        schema.executeBlocking(
            """
            query {
              getPerson(name: "foo") { name age }
            }
        """.trimIndent()
        ) shouldBe """
            {"data":{"getPerson":{"name":"foo","age":42}}}
        """.trimIndent()

        schema.executeBlocking(
            """
            mutation {
              addPerson(person: { inputName: "bar", inputAge: 20 }) { name age }
            }
        """.trimIndent()
        ) shouldBe """
            {"data":{"addPerson":{"name":"bar","age":20}}}
        """.trimIndent()

        val variables = """
            { "person": { "inputName": "foobar", "inputAge": 60 } }
        """.trimIndent()
        schema.executeBlocking(
            """
            mutation(${'$'}person: PersonInput!) {
              addPerson(person: ${'$'}person) { name age }
            }
        """.trimIndent(),
            variables = variables
        ) shouldBe """
            {"data":{"addPerson":{"name":"foobar","age":60}}}
        """.trimIndent()
    }

    @Test
    fun `property name must not start with __ when configured`() {
        expect<SchemaException>("Unable to handle input type 'Person': Illegal name '__name'. Names starting with '__' are reserved for introspection system") {
            KGraphQL.schema {
                inputType<Person> {
                    property(Person::name) {
                        name = "__name"
                    }
                }

                query("getPerson") {
                    resolver { person: Person -> person }
                }
            }
        }
    }

    // https://spec.graphql.org/September2025/#sec-OneOf-Input-Objects.Input-Coercion
    @Test
    fun `oneOf input objects should be coerced according to the spec`() {
        data class ExampleOneOfInputObject(val a: String?, val b: Int?)

        val schema = KGraphQL.schema {
            inputType<ExampleOneOfInputObject> {
                isOneOf = true
            }

            query("example") {
                resolver { input: ExampleOneOfInputObject -> input.toString() }
            }
        }

        schema.executeBlocking(
            """
            query {
              example(input: {a: "abc"}) 
            }
            """.trimIndent()
        ) shouldBe """
            {"data":{"example":"ExampleOneOfInputObject(a=abc, b=null)"}}
        """.trimIndent()

        schema.executeBlocking(
            """
            query {
              example(input: {b: 123}) 
            }
            """.trimIndent()
        ) shouldBe """
            {"data":{"example":"ExampleOneOfInputObject(a=null, b=123)"}}
        """.trimIndent()

        schema.executeBlocking(
            """
            query(${'$'}var: ExampleOneOfInputObject!) {
                example(input: ${'$'}var)
            }
            """.trimIndent(),
            variables = """{ "var": { "a": "abc" } }"""
        ) shouldBe """
            {"data":{"example":"ExampleOneOfInputObject(a=abc, b=null)"}}
        """.trimIndent()

        expectRequestError<ValidationException>("Value for member field 'a' must be non-null") {
            schema.executeBlocking(
                """
            query {
              example(input: {a: null}) 
            }
            """.trimIndent()
            )
        }

        expectExecutionError<InvalidInputValueException>("Value for member field 'a' must be non-null") {
            schema.executeBlocking(
                """
            query(${'$'}var: ExampleOneOfInputObject!) {
                example(input: ${'$'}var)
            }
            """.trimIndent(),
                variables = """{ "var": { "a": null } }"""
            )
        }

        expectExecutionError<InvalidInputValueException>("Value for member field 'a' must be non-null") {
            schema.executeBlocking(
                """
            query(${'$'}a: String!) {
                example(input: { a: ${'$'}a })
            }
            """.trimIndent()
            )
        }

        expectRequestError<ValidationException>("OneOf input object 'ExampleOneOfInputObject' must have exactly one field set, but 2 were provided") {
            schema.executeBlocking(
                """
            query {
              example(input: {a: "abc", b: 123}) 
            }
            """.trimIndent()
            )
        }

        expectRequestError<ValidationException>("OneOf input object 'ExampleOneOfInputObject' must have exactly one field set, but 2 were provided") {
            schema.executeBlocking(
                """
            query {
              example(input: {a: 456, b: "xyz"}) 
            }
            """.trimIndent()
            )
        }

        expectExecutionError<InvalidInputValueException>("OneOf input object 'ExampleOneOfInputObject' must have exactly one field set, but 2 were provided") {
            schema.executeBlocking(
                """
            query(${'$'}var: ExampleOneOfInputObject!) {
                example(input: ${'$'}var)
            }
            """.trimIndent(),
                variables = """{ "var": { "a": "abc", "b": 123 } }"""
            )
        }

        expectRequestError<ValidationException>("OneOf input object 'ExampleOneOfInputObject' must have exactly one field set, but 2 were provided") {
            schema.executeBlocking(
                """
            query {
              example(input: {a: "abc", b: null}) 
            }
            """.trimIndent()
            )
        }

        expectRequestError<ValidationException>("OneOf input object 'ExampleOneOfInputObject' must have exactly one field set, but 2 were provided") {
            schema.executeBlocking(
                """
            query(${'$'}b: Int!) {
              example(input: {a: "abc", b: ${'$'}b}) 
            }
            """.trimIndent()
            )
        }

        expectRequestError<ValidationException>("OneOf input object 'ExampleOneOfInputObject' must have exactly one field set, but 2 were provided") {
            schema.executeBlocking(
                """
            query(${'$'}a: String!, ${'$'}b: Int!) {
              example(input: {a: ${'$'}a, b: ${'$'}b}) 
            }
            """.trimIndent()
            )
        }

        expectRequestError<ValidationException>("OneOf input object 'ExampleOneOfInputObject' must have exactly one field set, but 0 were provided") {
            schema.executeBlocking(
                """
            query {
              example(input: {}) 
            }
            """.trimIndent()
            )
        }

        expectExecutionError<InvalidInputValueException>("OneOf input object 'ExampleOneOfInputObject' must have exactly one field set, but 0 were provided") {
            schema.executeBlocking(
                """
            query(${'$'}var: ExampleOneOfInputObject!) {
                example(input: ${'$'}var)
            }
            """.trimIndent(),
                variables = """{ "var": {} }"""
            )
        }
    }

    @Test
    fun `nested oneOf input objects should be coerced according to the spec`() {
        data class ExampleOneOfInputObject(val a: String?, val b: Int?)
        data class NestedInputObject(val a: String, val nestedObject: ExampleOneOfInputObject?)

        val schema = KGraphQL.schema {
            inputType<ExampleOneOfInputObject> {
                isOneOf = true
            }

            query("example") {
                resolver { input: NestedInputObject -> input.toString() }
            }
        }

        schema.executeBlocking(
            """
            query {
              example(input: {a: "abc", nestedObject: {a: "abc"}})
            }
            """.trimIndent()
        ) shouldBe """
            {"data":{"example":"NestedInputObject(a=abc, nestedObject=ExampleOneOfInputObject(a=abc, b=null))"}}
        """.trimIndent()

        schema.executeBlocking(
            """
            query {
              example(input: {a: "abc", nestedObject: {b: 123}})
            }
            """.trimIndent()
        ) shouldBe """
            {"data":{"example":"NestedInputObject(a=abc, nestedObject=ExampleOneOfInputObject(a=null, b=123))"}}
        """.trimIndent()

        schema.executeBlocking(
            """
            query(${'$'}var: NestedInputObjectInput!) {
                example(input: ${'$'}var)
            }
            """.trimIndent(),
            variables = """{ "var": { "a": "abc", "nestedObject": { "a": "abc" } } }"""
        ) shouldBe """
            {"data":{"example":"NestedInputObject(a=abc, nestedObject=ExampleOneOfInputObject(a=abc, b=null))"}}
        """.trimIndent()

        expectRequestError<ValidationException>("Value for member field 'a' must be non-null") {
            schema.executeBlocking(
                """
            query {
              example(input: {a: "abc", nestedObject: {a: null}}) 
            }
            """.trimIndent()
            )
        }

        expectExecutionError<InvalidInputValueException>("Value for member field 'a' must be non-null") {
            schema.executeBlocking(
                """
            query(${'$'}var: NestedInputObjectInput!) {
                example(input: ${'$'}var)
            }
            """.trimIndent(),
                variables = """{ "var": { "a": "abc", "nestedObject": { "a": null } } }"""
            )
        }

        expectExecutionError<InvalidInputValueException>("Value for member field 'a' must be non-null") {
            schema.executeBlocking(
                """
            query(${'$'}a: String!) {
                example(input: { a: "abc", nestedObject: { a: ${'$'}a } })
            }
            """.trimIndent()
            )
        }

        expectRequestError<ValidationException>("OneOf input object 'ExampleOneOfInputObject' must have exactly one field set, but 2 were provided") {
            schema.executeBlocking(
                """
            query {
              example(input: {a: "abc", nestedObject: {a: "abc", b: 123}}) 
            }
            """.trimIndent()
            )
        }

        expectRequestError<ValidationException>("OneOf input object 'ExampleOneOfInputObject' must have exactly one field set, but 2 were provided") {
            schema.executeBlocking(
                """
            query {
              example(input: {a: "abc", nestedObject: {a: 456, b: "xyz"}}) 
            }
            """.trimIndent()
            )
        }

        expectExecutionError<InvalidInputValueException>("OneOf input object 'ExampleOneOfInputObject' must have exactly one field set, but 2 were provided") {
            schema.executeBlocking(
                """
            query(${'$'}var: NestedInputObjectInput!) {
                example(input: ${'$'}var)
            }
            """.trimIndent(),
                variables = """{ "var": { "a": "abc", "nestedObject": { "a": "abc", "b": 123 } } }"""
            )
        }

        expectRequestError<ValidationException>("OneOf input object 'ExampleOneOfInputObject' must have exactly one field set, but 2 were provided") {
            schema.executeBlocking(
                """
            query {
              example(input: {a: "abc", nestedObject: {a: "abc", b: null}}) 
            }
            """.trimIndent()
            )
        }

        expectRequestError<ValidationException>("OneOf input object 'ExampleOneOfInputObject' must have exactly one field set, but 2 were provided") {
            schema.executeBlocking(
                """
            query(${'$'}b: Int!) {
              example(input: {a: "abc", nestedObject: {a: "abc", b: ${'$'}b}}) 
            }
            """.trimIndent()
            )
        }

        expectRequestError<ValidationException>("OneOf input object 'ExampleOneOfInputObject' must have exactly one field set, but 2 were provided") {
            schema.executeBlocking(
                """
            query(${'$'}a: String!, ${'$'}b: Int!) {
              example(input: {a: "abc", nestedObject: {a: ${'$'}a, b: ${'$'}b}}) 
            }
            """.trimIndent()
            )
        }

        expectRequestError<ValidationException>("OneOf input object 'ExampleOneOfInputObject' must have exactly one field set, but 0 were provided") {
            schema.executeBlocking(
                """
            query {
              example(input: {a: "abc", nestedObject: {}}) 
            }
            """.trimIndent()
            )
        }

        expectExecutionError<InvalidInputValueException>("OneOf input object 'ExampleOneOfInputObject' must have exactly one field set, but 0 were provided") {
            schema.executeBlocking(
                """
            query(${'$'}var: NestedInputObjectInput!) {
                example(input: ${'$'}var)
            }
            """.trimIndent(),
                variables = """{ "var": { "a": "abc", "nestedObject": {} } }"""
            )
        }

        expectExecutionError<InvalidInputValueException>("OneOf input object 'ExampleOneOfInputObject' must have exactly one field set, but 0 were provided") {
            schema.executeBlocking(
                """
            query(${'$'}var: ExampleOneOfInputObject!) {
                example(input: { a: "abc", nestedObject: ${'$'}var })
            }
            """.trimIndent(),
                variables = """{ "var": {} }"""
            )
        }
    }

    @Test
    fun `invalid properties should result in a proper validation error`() {
        data class ExampleOneOfInputObject(val a: String?, val b: Int?)
        data class NestedInputObject(
            val a: String,
            val nestedObject: ExampleOneOfInputObject?,
            val nestedObjects: List<ExampleOneOfInputObject>?,
            val nestedNestedObjects: List<List<ExampleOneOfInputObject>>?
        )

        val schema = KGraphQL.schema {
            inputType<ExampleOneOfInputObject> {
                isOneOf = true
            }
            inputType<NestedInputObject> {
                name = "NestedInputObjectInput"
                property(NestedInputObject::nestedObject) {
                    name = "nestedObjectRenamed"
                }
            }

            query("example") {
                resolver { input: NestedInputObject -> input.toString() }
            }
        }

        expectRequestError<ValidationException>("Property 'nestedObject' on 'NestedInputObjectInput' does not exist") {
            schema.executeBlocking(
                """
            query {
              example(input: {a: "abc", nestedObject: {a: "abc"}})
            }
            """.trimIndent()
            )
        }

        expectRequestError<ValidationException>("Property 'invalidString' on 'NestedInputObjectInput' does not exist") {
            schema.executeBlocking(
                """
            query {
              example(input: {a: "abc", invalidString: "example"})
            }
            """.trimIndent()
            )
        }

        expectRequestError<ValidationException>("Property 'invalid' on 'ExampleOneOfInputObject' does not exist") {
            schema.executeBlocking(
                """
            query {
              example(input: {a: "abc", nestedObjectRenamed: {invalid: "example"}})
            }
            """.trimIndent()
            )
        }

        expectRequestError<ValidationException>("Property 'invalid' on 'ExampleOneOfInputObject' does not exist") {
            schema.executeBlocking(
                """
            query {
              example(input: {a: "abc", nestedObjects: [{invalid: "example"}]})
            }
            """.trimIndent()
            )
        }

        expectRequestError<ValidationException>("Property 'invalid' on 'ExampleOneOfInputObject' does not exist") {
            schema.executeBlocking(
                """
            query {
              example(input: {a: "abc", nestedNestedObjects: [[{invalid: "example"}]]})
            }
            """.trimIndent()
            )
        }
    }

    @Test
    fun `lists of oneOf input objects should be coerced according to the spec`() {
        data class ExampleOneOfInputObject(val a: String?, val b: Int?)

        val schema = KGraphQL.schema {
            inputType<ExampleOneOfInputObject> {
                isOneOf = true
            }

            query("example") {
                resolver { inputs: List<ExampleOneOfInputObject> -> inputs.toString() }
            }
        }

        schema.executeBlocking(
            """
            query {
              example(inputs: [{a: "abc"}]) 
            }
            """.trimIndent()
        ) shouldBe """
            {"data":{"example":"[ExampleOneOfInputObject(a=abc, b=null)]"}}
        """.trimIndent()

        schema.executeBlocking(
            """
            query {
              example(inputs: [{b: 123}]) 
            }
            """.trimIndent()
        ) shouldBe """
            {"data":{"example":"[ExampleOneOfInputObject(a=null, b=123)]"}}
        """.trimIndent()

        schema.executeBlocking(
            """
            query(${'$'}var: [ExampleOneOfInputObject!]!) {
                example(inputs: ${'$'}var)
            }
            """.trimIndent(),
            variables = """{ "var": [{ "a": "abc" }] }"""
        ) shouldBe """
            {"data":{"example":"[ExampleOneOfInputObject(a=abc, b=null)]"}}
        """.trimIndent()

        expectRequestError<ValidationException>("Value for member field 'a' must be non-null") {
            schema.executeBlocking(
                """
            query {
              example(inputs: [{a: null}]) 
            }
            """.trimIndent()
            )
        }

        expectExecutionError<InvalidInputValueException>("Value for member field 'a' must be non-null") {
            schema.executeBlocking(
                """
            query(${'$'}var: [ExampleOneOfInputObject!]!) {
                example(inputs: ${'$'}var)
            }
            """.trimIndent(),
                variables = """{ "var": [{ "a": null }] }"""
            )
        }

        expectExecutionError<InvalidInputValueException>("Value for member field 'a' must be non-null") {
            schema.executeBlocking(
                """
            query(${'$'}a: String!) {
                example(inputs: [{ a: ${'$'}a }])
            }
            """.trimIndent()
            )
        }

        expectRequestError<ValidationException>("OneOf input object 'ExampleOneOfInputObject' must have exactly one field set, but 2 were provided") {
            schema.executeBlocking(
                """
            query {
              example(inputs: [{a: "abc", b: 123}]) 
            }
            """.trimIndent()
            )
        }

        expectRequestError<ValidationException>("OneOf input object 'ExampleOneOfInputObject' must have exactly one field set, but 2 were provided") {
            schema.executeBlocking(
                """
            query {
              example(inputs: [{a: 456, b: "xyz"}]) 
            }
            """.trimIndent()
            )
        }

        expectExecutionError<InvalidInputValueException>("OneOf input object 'ExampleOneOfInputObject' must have exactly one field set, but 2 were provided") {
            schema.executeBlocking(
                """
            query(${'$'}var: [ExampleOneOfInputObject!]!) {
                example(inputs: ${'$'}var)
            }
            """.trimIndent(),
                variables = """{ "var": [{ "a": "abc", "b": 123 }] }"""
            )
        }

        expectRequestError<ValidationException>("OneOf input object 'ExampleOneOfInputObject' must have exactly one field set, but 2 were provided") {
            schema.executeBlocking(
                """
            query {
              example(inputs: [{a: "abc", b: null}]) 
            }
            """.trimIndent()
            )
        }

        expectRequestError<ValidationException>("OneOf input object 'ExampleOneOfInputObject' must have exactly one field set, but 2 were provided") {
            schema.executeBlocking(
                """
            query(${'$'}b: Int!) {
              example(inputs: [{a: "abc", b: ${'$'}b}]) 
            }
            """.trimIndent()
            )
        }

        expectRequestError<ValidationException>("OneOf input object 'ExampleOneOfInputObject' must have exactly one field set, but 2 were provided") {
            schema.executeBlocking(
                """
            query(${'$'}a: String!, ${'$'}b: Int!) {
              example(inputs: [{a: ${'$'}a, b: ${'$'}b}]) 
            }
            """.trimIndent()
            )
        }

        expectRequestError<ValidationException>("OneOf input object 'ExampleOneOfInputObject' must have exactly one field set, but 0 were provided") {
            schema.executeBlocking(
                """
            query {
              example(inputs: [{}]) 
            }
            """.trimIndent()
            )
        }

        expectExecutionError<InvalidInputValueException>("OneOf input object 'ExampleOneOfInputObject' must have exactly one field set, but 0 were provided") {
            schema.executeBlocking(
                """
            query(${'$'}var: ExampleOneOfInputObject!) {
                example(inputs: [${'$'}var])
            }
            """.trimIndent(),
                variables = """{ "var": {} }"""
            )
        }
    }

    @Test
    fun `errors on renamed properties should be reported with their actual name`() {
        data class ExampleOneOfInputObject(val a: String?, val b: Int?)

        val schema = KGraphQL.schema {
            inputType<ExampleOneOfInputObject> {
                isOneOf = true
                property(ExampleOneOfInputObject::a) {
                    name = "aRenamed"
                }
            }

            query("example") {
                resolver { input: ExampleOneOfInputObject -> input.toString() }
            }
        }

        expectRequestError<ValidationException>("Value for member field 'aRenamed' must be non-null") {
            schema.executeBlocking(
                """
            query {
              example(input: {aRenamed: null}) 
            }
            """.trimIndent()
            )
        }

        expectExecutionError<InvalidInputValueException>("Value for member field 'aRenamed' must be non-null") {
            schema.executeBlocking(
                """
            query(${'$'}var: ExampleOneOfInputObject!) {
                example(input: ${'$'}var)
            }
            """.trimIndent(),
                variables = """{ "var": { "aRenamed": null } }"""
            )
        }
    }

    sealed interface PostElement
    data class Paragraph(val text: String) : PostElement
    data class BlockQuote(val text: String, val attribution: String?, val attributionUrl: String?) : PostElement
    data class Gallery(val imageUrls: List<String>, val caption: String?, val attribution: String?) : PostElement
    data class PostElementInput(val paragraph: Paragraph?, val blockquote: BlockQuote?, val gallery: Gallery?)
    data class Post(val id: ID, val elements: List<PostElement>)

    @Test
    fun `input polymorphism with oneOf input objects`() {
        val posts = mutableListOf<Post>()
        val schema = KGraphQL.schema {
            configure {
                useDefaultPrettyPrinter = true
            }

            unionType<PostElement>()
            inputType<PostElementInput> {
                isOneOf = true
            }
            query("post") {
                resolver { id: ID -> posts.find { it.id == id } }
            }
            mutation("createPost") {
                resolver { elements: List<PostElementInput> ->
                    val postElements = elements.map {
                        it.paragraph ?: it.blockquote ?: it.gallery ?: throw IllegalArgumentException("Invalid PostElementInput")
                    }
                    val post = Post(id = ID((posts.size + 1).toString()), elements = postElements)
                    posts.add(post)
                    post
                }
            }
        }

        schema.executeBlocking("""
            mutation {
                createPost(elements: [
                    { paragraph: { text: "First Paragraph" } },
                    { blockquote: { text: "This is a great post!", attribution: "Me", attributionUrl: "https://example.com" } }
                    { paragraph: { text: "Second Paragraph" } },
                    { gallery: { imageUrls: ["https://example.com/image1.jpg", "https://example.com/image2.jpg"], caption: "My Gallery", attribution: "Me Again" } }
                ]) { id }
            }
        """.trimIndent()) shouldBe """
            {
              "data" : {
                "createPost" : {
                  "id" : "1"
                }
              }
            }
        """.trimIndent()

        schema.executeBlocking("""
            query {
                post(id: "1") {
                    id
                    elements {
                        __typename
                        ... on Paragraph { text }
                        ... on BlockQuote { text attribution attributionUrl }
                        ... on Gallery { imageUrls caption attribution }
                    }
                }
            }
        """.trimIndent()) shouldBe """
            {
              "data" : {
                "post" : {
                  "id" : "1",
                  "elements" : [ {
                    "__typename" : "Paragraph",
                    "text" : "First Paragraph"
                  }, {
                    "__typename" : "BlockQuote",
                    "text" : "This is a great post!",
                    "attribution" : "Me",
                    "attributionUrl" : "https://example.com"
                  }, {
                    "__typename" : "Paragraph",
                    "text" : "Second Paragraph"
                  }, {
                    "__typename" : "Gallery",
                    "imageUrls" : [ "https://example.com/image1.jpg", "https://example.com/image2.jpg" ],
                    "caption" : "My Gallery",
                    "attribution" : "Me Again"
                  } ]
                }
              }
            }
        """.trimIndent()
    }
}
