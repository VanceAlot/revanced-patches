package app.revanced.patches.layout

import app.revanced.patcher.cache.Cache
import app.revanced.patcher.extensions.AccessFlagExtensions.Companion.or
import app.revanced.patcher.extensions.addInstructions
import app.revanced.patcher.patch.*
import app.revanced.patcher.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.revanced.patcher.signature.MethodSignature
import app.revanced.patcher.smali.asInstructions
import app.revanced.patches.SHARED_METADATA
import org.jf.dexlib2.AccessFlags
import org.jf.dexlib2.Opcode
import org.jf.dexlib2.builder.instruction.BuilderInstruction22c
import org.jf.dexlib2.builder.instruction.BuilderInstruction35c
import org.jf.dexlib2.iface.instruction.formats.Instruction22c
import org.jf.dexlib2.iface.instruction.formats.Instruction35c
import org.jf.dexlib2.immutable.ImmutableMethod
import org.jf.dexlib2.immutable.ImmutableMethodImplementation

class HideSuggestionsPatch : Patch(
    PatchMetadata(
        "hide-suggestions",
        "TODO",
        "TODO"
    )
) {
    override fun execute(cache: Cache): PatchResult {
        val map = cache.methodMap["hide-suggestions-patch"].findParentMethod(
            MethodSignature(
                "hide-suggestions-method",
                SHARED_METADATA,
                "V",
                AccessFlags.PUBLIC or AccessFlags.FINAL,
                listOf("Z"),
                listOf(
                    Opcode.IPUT_BOOLEAN,
                    Opcode.IGET_OBJECT,
                    Opcode.IGET_BOOLEAN,
                    Opcode.INVOKE_VIRTUAL,
                    Opcode.RETURN_VOID
                )
            )
        ) ?: return PatchResultError("Parent method hide-suggestions-method has not been found")

        // deep clone the method in order to add a new register
        // TODO: replace by a mutable method implementation with settable register count when available
        val originalMethod = map.immutableMethod
        val originalImplementation = originalMethod.implementation!!
        val clonedMethod = ImmutableMethod(
            originalMethod.returnType,
            originalMethod.name,
            originalMethod.parameters,
            originalMethod.returnType,
            originalMethod.accessFlags,
            originalMethod.annotations,
            originalMethod.hiddenApiRestrictions,
            ImmutableMethodImplementation(
                originalImplementation.registerCount + 1, // additional register for the boolean
                originalImplementation.instructions,
                originalImplementation.tryBlocks,
                originalImplementation.debugItems,
            )
        ).toMutable() // create mutable clone out of the immutable method clone

        val clonedImplementation = clonedMethod.implementation!!

        return PatchResultSuccess() // TODO: fix below

        // fix the instructions registers
        clonedImplementation.instructions.forEachIndexed { index, it ->
            val opcode = it.opcode
            // increment all registers (instance register and object register) by 1
            // due to adding a new virtual register for the boolean value
            clonedImplementation.replaceInstruction(
                index,
                when (it) {
                    is Instruction22c -> BuilderInstruction22c(
                        opcode,
                        it.registerA + 1, // increment register
                        it.registerB + 1, // increment register
                        it.reference
                    )
                    is Instruction35c -> BuilderInstruction35c(
                        opcode,
                        1,
                        it.registerC + 1, // increment register
                        0,
                        0,
                        0,
                        0,
                        it.reference
                    )
                    else -> return@forEachIndexed
                }
            )
        }

        // resolve the class proxy
        val clazz = map.definingClassProxy.resolve()

        // remove the old method & add the clone with our additional register
        clazz.methods.remove(originalMethod)
        clazz.methods.add(clonedMethod)

        // Proxy the first parameter of our clone by passing it to the RemoveSuggestions method
        // TODO: this crashes, find out why
        clonedImplementation.addInstructions(
            0,
            """
                invoke-static/range { v2 .. v2 }, Ljava/lang/Boolean;->valueOf(Z)Ljava/lang/Boolean;
                move-result-object v0
                invoke-static { v0 }, Lfi/razerman/youtube/XAdRemover;->RemoveSuggestions(Ljava/lang/Boolean;)Ljava/lang/Boolean;
                move-result-object v0
                invoke-virtual/range { v0 .. v0 }, Ljava/lang/Boolean;->booleanValue()Z
                move-result v2
            """.trimIndent().asInstructions()
        )
        return PatchResultSuccess()
    }
}