package de.stuebingerb.kgraphql.stitched.schema.structure

import de.stuebingerb.kgraphql.ExperimentalAPI
import de.stuebingerb.kgraphql.KGraphQL
import de.stuebingerb.kgraphql.request.Introspection
import de.stuebingerb.kgraphql.stitched.schema.dsl.StitchedSchemaConfigurationDSL
import de.stuebingerb.kgraphql.stitched.schema.structure.IntrospectedSchemaTest.AnotherInterface
import de.stuebingerb.kgraphql.stitched.schema.structure.IntrospectedSchemaTest.Implementation1
import de.stuebingerb.kgraphql.stitched.schema.structure.IntrospectedSchemaTest.Implementation2
import de.stuebingerb.kgraphql.stitched.schema.structure.IntrospectedSchemaTest.Interface
import de.stuebingerb.kgraphql.stitched.schema.structure.IntrospectedSchemaTest.SubType1
import de.stuebingerb.kgraphql.stitched.schema.structure.IntrospectedSchemaTest.Union
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test

@OptIn(ExperimentalAPI::class)
class RemoteSchemaCompilationTest {
    @Test
    fun `compiled schema should have proper possibleTypes`() {
        val schema = KGraphQL.schema {
            query("getUnion") {
                resolver { -> SubType1("st1") }.returns<Union>()
            }
            query("getInterface") {
                resolver { -> Implementation1(1, "impl1") }.returns<Interface>()
            }
            type<AnotherInterface>()
            type<Implementation1>()
            type<Implementation2>()
        }

        val schemaFromIntrospection = IntrospectedSchema.fromIntrospectionResponse(
            schema.executeBlocking(Introspection.query(Introspection.SpecLevel.WorkingDraft))
        )

        val compiledSchema = RemoteSchemaCompilation(StitchedSchemaConfigurationDSL().apply {
            remoteExecutor = StitchedSchemaTest.DummyRemoteRequestExecutor
        }.build()).perform("ignored", schemaFromIntrospection)

        val unionType = compiledSchema.find { it.name == "Union" }
        unionType shouldNotBe null
        unionType?.possibleTypes?.map { it.name } shouldContainExactlyInAnyOrder listOf("SubType1", "SubType2")

        val interfaceType = compiledSchema.find { it.name == "Interface" }
        interfaceType shouldNotBe null
        interfaceType?.possibleTypes?.map { it.name } shouldContainExactlyInAnyOrder listOf(
            "Implementation1",
            "Implementation2"
        )

        val anotherInterfaceType = compiledSchema.find { it.name == "AnotherInterface" }
        anotherInterfaceType shouldNotBe null
        anotherInterfaceType?.possibleTypes?.map { it.name } shouldContainExactlyInAnyOrder listOf(
            "Implementation2"
        )
    }
}
