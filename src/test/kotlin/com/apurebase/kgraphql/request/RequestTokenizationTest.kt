package com.apurebase.kgraphql.request


// TODO: Re-create these tests with the new parser
///**
// * Not lots of tests, as tokenization is mostly covered by Query and Mutation tests
// */
//class RequestTokenizationTest {
//
//    private fun testTokenization(input : String, expected : List<String>) {
//        val tokens = tokenizeRequest(input)
//        assertThat(tokens, equalTo(expected))
//    }
//
//    @Test
//    fun `tokenize mutation with args`(){
//        testTokenization(
//            input = "{createHero(name: \"Batman\", appearsIn: \"The Dark Knight\")}",
//            expected = listOf("{", "createHero", "(", "name", ":", "\"Batman\"", "appearsIn", ":", "\"The Dark Knight\"", ")", "}")
//        )
//    }
//
//    @Test
//    fun `tokenize simple query`(){
//        testTokenization(
//            input = "{batman: hero(name: \"Batman\"){ skills : powers }}",
//            expected = listOf("{", "batman", ":", "hero", "(", "name", ":", "\"Batman\"", ")", "{", "skills", ":", "powers", "}", "}")
//        )
//    }
//
//    @Test
//    fun `tokenize query with nested selection set`(){
//        testTokenization(
//            input = "{hero{name appearsIn{title{abbr full} year}}\nvillain{name deeds}}",
//            expected = listOf(
//                "{", "hero", "{", "name", "appearsIn", "{", "title", "{", "abbr", "full", "}", "year", "}", "}",
//                "villain", "{", "name", "deeds", "}", "}"
//            )
//        )
//    }
//
//    @Test
//    fun `Tokenize list argument`(){
//        testTokenization(
//            input = "{List(value : [23, 3, 23])}",
//            expected = listOf(
//                "{", "List","(", "value", ":", "[", "23", "3", "23", "]", ")","}"
//            )
//        )
//    }
//
//    @Test
//    fun `Tokenize required list argument`() {
//        val d = "$"
//        testTokenization(
//            input = "mutation create(${d}agesName1: String!, ${d}ages: [String!]!){ createActorWithAges(name: ${d}agesName1, ages: ${d}ages1) { name, age } }",
//            expected = listOf(
//                "mutation",
//                "create",
//                "(",
//                "${d}agesName1",
//                ":",
//                "String!",
//                "${d}ages",
//                ":",
//                "[",
//                "String!",
//                "]!",
//                ")",
//                "{",
//                "createActorWithAges",
//                "(",
//                "name",
//                ":",
//                "${d}agesName1",
//                "ages",
//                ":",
//                "${d}ages1",
//                ")",
//                "{",
//                "name",
//                "age",
//                "}",
//                "}"
//            )
//        )
//    }
//
//    @Test
//    fun `tokenize input with quotes`(){
//        testTokenization(
//            input = "{hello(name : \"Ted\\\" Mosby\")}",
//            expected = listOf("{", "hello", "(", "name", ":", "\"Ted\\\" Mosby\"", ")", "}")
//        )
//    }
//
//    @Test
//    fun `tokenize input with new lines`() {
//        testTokenization(
//            input = "{lists{\r\ntotalCount\r\nnodes{\r\ntitle\r\n }\r\n}\r\n}",
//            expected = listOf("{", "lists", "{", "totalCount", "nodes", "{", "title", "}", "}", "}")
//        )
//    }
//}
