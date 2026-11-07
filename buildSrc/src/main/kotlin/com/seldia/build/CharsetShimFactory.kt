package com.seldia.build

import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.ClassContext
import com.android.build.api.instrumentation.ClassData
import com.android.build.api.instrumentation.InstrumentationParameters
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

abstract class CharsetShimFactory : AsmClassVisitorFactory<InstrumentationParameters.None> {

    override fun createClassVisitor(classContext: ClassContext, nextClassVisitor: ClassVisitor): ClassVisitor =
        CharsetShimClassVisitor(Opcodes.ASM9, nextClassVisitor)

    override fun isInstrumentable(classData: ClassData): Boolean = true
}

private class CharsetShimClassVisitor(api: Int, next: ClassVisitor) : ClassVisitor(api, next) {

    override fun visitMethod(
        access: Int,
        name: String?,
        descriptor: String?,
        signature: String?,
        exceptions: Array<out String>?
    ): MethodVisitor {
        val methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions)
        return object : MethodVisitor(api, methodVisitor) {
            override fun visitMethodInsn(
                opcode: Int,
                owner: String,
                name: String,
                descriptor: String,
                isInterface: Boolean
            ) {
                val replacement = REPLACEMENTS["$owner.$name$descriptor"]
                if (opcode == Opcodes.INVOKESTATIC && replacement != null) {
                    super.visitMethodInsn(
                        Opcodes.INVOKEVIRTUAL,
                        "java/nio/charset/Charset",
                        "name",
                        "()Ljava/lang/String;",
                        false
                    )
                    super.visitMethodInsn(opcode, owner, name, replacement, isInterface)
                } else {
                    super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
                }
            }
        }
    }

    private companion object {
        const val STRING_STRING_TO_STRING = "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"

        val REPLACEMENTS = mapOf(
            "java/net/URLDecoder.decode(Ljava/lang/String;Ljava/nio/charset/Charset;)Ljava/lang/String;" to STRING_STRING_TO_STRING,
            "java/net/URLEncoder.encode(Ljava/lang/String;Ljava/nio/charset/Charset;)Ljava/lang/String;" to STRING_STRING_TO_STRING
        )
    }
}
