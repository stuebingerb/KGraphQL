package de.stuebingerb.kgraphql.stitched.schema.execution

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import de.stuebingerb.kgraphql.Context
import de.stuebingerb.kgraphql.helpers.toValueNode
import de.stuebingerb.kgraphql.request.Variables
import de.stuebingerb.kgraphql.schema.execution.ArgumentTransformer
import de.stuebingerb.kgraphql.schema.execution.Execution
import de.stuebingerb.kgraphql.schema.execution.GenericTypeResolver
import de.stuebingerb.kgraphql.schema.introspection.TypeKind
import de.stuebingerb.kgraphql.schema.introspection.__InputValue
import de.stuebingerb.kgraphql.schema.model.FunctionWrapper
import de.stuebingerb.kgraphql.schema.model.ast.ArgumentNode
import de.stuebingerb.kgraphql.schema.model.ast.ArgumentNodes
import de.stuebingerb.kgraphql.schema.model.ast.NameNode
import de.stuebingerb.kgraphql.schema.model.ast.ValueNode
import de.stuebingerb.kgraphql.schema.structure.Field
import de.stuebingerb.kgraphql.schema.structure.InputValue

class RemoteArgumentTransformer(val objectMapper: ObjectMapper, genericTypeResolver: GenericTypeResolver) :
    ArgumentTransformer(genericTypeResolver) {
    override fun transformArguments(
        funName: String,
        receiver: Any?,
        inputValues: List<InputValue<*>>,
        args: ArgumentNodes?,
        variables: Variables,
        executionNode: Execution,
        requestContext: Context,
        field: FunctionWrapper<*>
    ): List<Any?>? = if (executionNode is Execution.Remote) {
        val argsFromParent: Map<__InputValue, String> =
            (field as? Field.RemoteOperation<*, *>)?.field?.argsFromParent.orEmpty()
        // Resolve input arguments before calling remote schemas
        val nodeArgs = executionNode.arguments.orEmpty()
        val mappedNodeArgs = nodeArgs.map {
            it.key to it.value
        } + argsFromParent.map {
            val parentValue = when (receiver) {
                is ObjectNode -> receiver.get(it.value)
                else -> (objectMapper.valueToTree(receiver) as? ObjectNode)?.get(it.value)
            }.toValueNode(it.key.type)
            if (parentValue is ValueNode.NullValueNode && it.key.type.kind == TypeKind.NON_NULL) {
                // parentValue is null but required for the remote operation; return null to skip call
                // TODO: improve architecture - can we use directives?
                //  or would it be wrong to use exceptions (once we hav proper field errors)
                //   - you shouldn't request fields where you cannot provide proper arguments, should you?
                return null
            }
            it.key.name to parentValue
        }
        val mappedArgumentNodes = ArgumentNodes(mappedNodeArgs.map {
            ArgumentNode(null, NameNode(it.first, null), it.second)
        })
        inputValues.mapNotNull { parameter ->
            when {
                parameter.type.isInstance(requestContext) -> requestContext + variables
                parameter.type.isInstance(executionNode) -> Execution.Remote(
                    selectionNode = executionNode.selectionNode,
                    field = executionNode.field,
                    children = executionNode.children,
                    arguments = mappedArgumentNodes,
                    directives = executionNode.directives,
                    variables = executionNode.variables,
                    namedFragments = executionNode.namedFragments,
                    remoteUrl = executionNode.remoteUrl,
                    remoteOperation = executionNode.remoteOperation,
                    operationType = executionNode.operationType,
                    parent = executionNode.parent
                )

                else -> null
            }
        }
    } else {
        super.transformArguments(
            funName,
            receiver,
            inputValues,
            args,
            variables,
            executionNode,
            requestContext,
            field
        )
    }
}
