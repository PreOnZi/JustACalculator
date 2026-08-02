package com.fictioncutshort.justacalculator.gl

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.get
import kotlinx.cinterop.set
import kotlinx.cinterop.toCPointer
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.posix.memcpy

/**
 * Delegates to Kotlin/Native's `platform.gles3` bindings.
 *
 * Two impedance mismatches are absorbed here rather than in the renderers:
 * GL enums and object handles are `UInt` on iOS but `Int` on Android, and the
 * pointer-taking calls need Kotlin arrays pinned for the duration of the call.
 */
@OptIn(ExperimentalForeignApi::class)
actual object Gl {

    actual val GL_ARRAY_BUFFER: Int = platform.gles3.GL_ARRAY_BUFFER.toInt()
    actual val GL_BACK: Int = platform.gles3.GL_BACK.toInt()
    actual val GL_BLEND: Int = platform.gles3.GL_BLEND.toInt()
    actual val GL_CLAMP_TO_EDGE: Int = platform.gles3.GL_CLAMP_TO_EDGE.toInt()
    actual val GL_COLOR_ATTACHMENT0: Int = platform.gles3.GL_COLOR_ATTACHMENT0.toInt()
    actual val GL_COLOR_BUFFER_BIT: Int = platform.gles3.GL_COLOR_BUFFER_BIT.toInt()
    actual val GL_COMPILE_STATUS: Int = platform.gles3.GL_COMPILE_STATUS.toInt()
    actual val GL_CULL_FACE: Int = platform.gles3.GL_CULL_FACE.toInt()
    actual val GL_DEPTH_ATTACHMENT: Int = platform.gles3.GL_DEPTH_ATTACHMENT.toInt()
    actual val GL_DEPTH_BUFFER_BIT: Int = platform.gles3.GL_DEPTH_BUFFER_BIT.toInt()
    actual val GL_DEPTH_COMPONENT16: Int = platform.gles3.GL_DEPTH_COMPONENT16.toInt()
    actual val GL_DEPTH_TEST: Int = platform.gles3.GL_DEPTH_TEST.toInt()
    actual val GL_DYNAMIC_DRAW: Int = platform.gles3.GL_DYNAMIC_DRAW.toInt()
    actual val GL_ELEMENT_ARRAY_BUFFER: Int = platform.gles3.GL_ELEMENT_ARRAY_BUFFER.toInt()
    actual val GL_EXTENSIONS: Int = platform.gles3.GL_EXTENSIONS.toInt()
    actual val GL_FLOAT: Int = platform.gles3.GL_FLOAT.toInt()
    actual val GL_FRAGMENT_SHADER: Int = platform.gles3.GL_FRAGMENT_SHADER.toInt()
    actual val GL_FRAMEBUFFER: Int = platform.gles3.GL_FRAMEBUFFER.toInt()
    actual val GL_FRAMEBUFFER_COMPLETE: Int = platform.gles3.GL_FRAMEBUFFER_COMPLETE.toInt()
    actual val GL_LEQUAL: Int = platform.gles3.GL_LEQUAL.toInt()
    actual val GL_LESS: Int = platform.gles3.GL_LESS.toInt()
    actual val GL_LINEAR: Int = platform.gles3.GL_LINEAR.toInt()
    actual val GL_LINES: Int = platform.gles3.GL_LINES.toInt()
    actual val GL_LINK_STATUS: Int = platform.gles3.GL_LINK_STATUS.toInt()
    actual val GL_ONE: Int = platform.gles3.GL_ONE.toInt()
    actual val GL_ONE_MINUS_SRC_ALPHA: Int = platform.gles3.GL_ONE_MINUS_SRC_ALPHA.toInt()
    actual val GL_RENDERBUFFER: Int = platform.gles3.GL_RENDERBUFFER.toInt()
    actual val GL_RGB: Int = platform.gles3.GL_RGB.toInt()
    actual val GL_RGBA: Int = platform.gles3.GL_RGBA.toInt()
    actual val GL_SCISSOR_TEST: Int = platform.gles3.GL_SCISSOR_TEST.toInt()
    actual val GL_SRC_ALPHA: Int = platform.gles3.GL_SRC_ALPHA.toInt()
    actual val GL_STATIC_DRAW: Int = platform.gles3.GL_STATIC_DRAW.toInt()
    actual val GL_TEXTURE0: Int = platform.gles3.GL_TEXTURE0.toInt()
    actual val GL_TEXTURE_2D: Int = platform.gles3.GL_TEXTURE_2D.toInt()
    actual val GL_TEXTURE_MAG_FILTER: Int = platform.gles3.GL_TEXTURE_MAG_FILTER.toInt()
    actual val GL_TEXTURE_MIN_FILTER: Int = platform.gles3.GL_TEXTURE_MIN_FILTER.toInt()
    actual val GL_TEXTURE_WRAP_S: Int = platform.gles3.GL_TEXTURE_WRAP_S.toInt()
    actual val GL_TEXTURE_WRAP_T: Int = platform.gles3.GL_TEXTURE_WRAP_T.toInt()
    actual val GL_TRIANGLES: Int = platform.gles3.GL_TRIANGLES.toInt()
    actual val GL_TRIANGLE_FAN: Int = platform.gles3.GL_TRIANGLE_FAN.toInt()
    actual val GL_UNIFORM_BUFFER: Int = platform.gles3.GL_UNIFORM_BUFFER.toInt()
    actual val GL_UNSIGNED_BYTE: Int = platform.gles3.GL_UNSIGNED_BYTE.toInt()
    actual val GL_UNSIGNED_INT: Int = platform.gles3.GL_UNSIGNED_INT.toInt()
    actual val GL_VERTEX_SHADER: Int = platform.gles3.GL_VERTEX_SHADER.toInt()

    actual fun glActiveTexture(texture: Int) = platform.gles3.glActiveTexture(texture.toUInt())
    actual fun glAttachShader(program: Int, shader: Int) = platform.gles3.glAttachShader(program.toUInt(), shader.toUInt())
    actual fun glBindBuffer(target: Int, buffer: Int) = platform.gles3.glBindBuffer(target.toUInt(), buffer.toUInt())
    actual fun glBindBufferBase(target: Int, index: Int, buffer: Int) = platform.gles3.glBindBufferBase(target.toUInt(), index.toUInt(), buffer.toUInt())
    actual fun glBindFramebuffer(target: Int, framebuffer: Int) = platform.gles3.glBindFramebuffer(target.toUInt(), framebuffer.toUInt())
    actual fun glBindRenderbuffer(target: Int, renderbuffer: Int) = platform.gles3.glBindRenderbuffer(target.toUInt(), renderbuffer.toUInt())
    actual fun glBindTexture(target: Int, texture: Int) = platform.gles3.glBindTexture(target.toUInt(), texture.toUInt())
    actual fun glBindVertexArray(array: Int) = platform.gles3.glBindVertexArray(array.toUInt())
    actual fun glBlendFunc(sfactor: Int, dfactor: Int) = platform.gles3.glBlendFunc(sfactor.toUInt(), dfactor.toUInt())
    actual fun glCheckFramebufferStatus(target: Int): Int = platform.gles3.glCheckFramebufferStatus(target.toUInt()).toInt()
    actual fun glClear(mask: Int) = platform.gles3.glClear(mask.toUInt())
    actual fun glClearColor(red: Float, green: Float, blue: Float, alpha: Float) = platform.gles3.glClearColor(red, green, blue, alpha)
    actual fun glCompileShader(shader: Int) = platform.gles3.glCompileShader(shader.toUInt())
    actual fun glCreateProgram(): Int = platform.gles3.glCreateProgram().toInt()
    actual fun glCreateShader(type: Int): Int = platform.gles3.glCreateShader(type.toUInt()).toInt()
    actual fun glCullFace(mode: Int) = platform.gles3.glCullFace(mode.toUInt())
    actual fun glDeleteProgram(program: Int) = platform.gles3.glDeleteProgram(program.toUInt())
    actual fun glDeleteShader(shader: Int) = platform.gles3.glDeleteShader(shader.toUInt())
    actual fun glDepthFunc(func: Int) = platform.gles3.glDepthFunc(func.toUInt())
    actual fun glDepthMask(flag: Boolean) = platform.gles3.glDepthMask(if (flag) 1u else 0u)
    actual fun glDisable(cap: Int) = platform.gles3.glDisable(cap.toUInt())
    actual fun glDisableVertexAttribArray(index: Int) = platform.gles3.glDisableVertexAttribArray(index.toUInt())
    actual fun glDrawArrays(mode: Int, first: Int, count: Int) = platform.gles3.glDrawArrays(mode.toUInt(), first, count)
    actual fun glEnable(cap: Int) = platform.gles3.glEnable(cap.toUInt())
    actual fun glEnableVertexAttribArray(index: Int) = platform.gles3.glEnableVertexAttribArray(index.toUInt())
    actual fun glFramebufferRenderbuffer(target: Int, attachment: Int, rbTarget: Int, renderbuffer: Int) = platform.gles3.glFramebufferRenderbuffer(target.toUInt(), attachment.toUInt(), rbTarget.toUInt(), renderbuffer.toUInt())
    actual fun glFramebufferTexture2D(target: Int, attachment: Int, texTarget: Int, texture: Int, level: Int) = platform.gles3.glFramebufferTexture2D(target.toUInt(), attachment.toUInt(), texTarget.toUInt(), texture.toUInt(), level)
    actual fun glGetAttribLocation(program: Int, name: String): Int = platform.gles3.glGetAttribLocation(program.toUInt(), name)
    actual fun glGetProgramInfoLog(program: Int): String = iosInfoLog(program, program = true)
    actual fun glGetShaderInfoLog(shader: Int): String = iosInfoLog(shader, program = false)
    actual fun glGetUniformBlockIndex(program: Int, name: String): Int = platform.gles3.glGetUniformBlockIndex(program.toUInt(), name).toInt()
    actual fun glGetUniformLocation(program: Int, name: String): Int = platform.gles3.glGetUniformLocation(program.toUInt(), name)
    actual fun glLinkProgram(program: Int) = platform.gles3.glLinkProgram(program.toUInt())
    actual fun glRenderbufferStorage(target: Int, internalformat: Int, width: Int, height: Int) = platform.gles3.glRenderbufferStorage(target.toUInt(), internalformat.toUInt(), width, height)
    actual fun glScissor(x: Int, y: Int, width: Int, height: Int) = platform.gles3.glScissor(x, y, width, height)
    actual fun glShaderSource(shader: Int, source: String) = iosShaderSource(shader, source)
    actual fun glTexParameteri(target: Int, pname: Int, param: Int) = platform.gles3.glTexParameteri(target.toUInt(), pname.toUInt(), param)
    actual fun glUniform1f(location: Int, x: Float) = platform.gles3.glUniform1f(location, x)
    actual fun glUniform1i(location: Int, x: Int) = platform.gles3.glUniform1i(location, x)
    actual fun glUniform3f(location: Int, x: Float, y: Float, z: Float) = platform.gles3.glUniform3f(location, x, y, z)
    actual fun glUniform4f(location: Int, x: Float, y: Float, z: Float, w: Float) = platform.gles3.glUniform4f(location, x, y, z, w)
    actual fun glUniformBlockBinding(program: Int, index: Int, binding: Int) = platform.gles3.glUniformBlockBinding(program.toUInt(), index.toUInt(), binding.toUInt())
    actual fun glUseProgram(program: Int) = platform.gles3.glUseProgram(program.toUInt())
    actual fun glViewport(x: Int, y: Int, width: Int, height: Int) = platform.gles3.glViewport(x, y, width, height)
    actual fun glDrawElements(mode: Int, count: Int, type: Int, offset: Int) = iosDrawElements(mode, count, type, offset)
    actual fun glGetString(name: Int): String? = iosGetString(name)

    actual fun glGenBuffers(n: Int, buffers: IntArray, offset: Int) {
        memScoped {
            val out = allocArray<UIntVar>(n)
            platform.gles3.glGenBuffers(n, out)
            for (i in 0 until n) buffers[offset + i] = out[i].toInt()
        }
    }

    actual fun glGenFramebuffers(n: Int, framebuffers: IntArray, offset: Int) {
        memScoped {
            val out = allocArray<UIntVar>(n)
            platform.gles3.glGenFramebuffers(n, out)
            for (i in 0 until n) framebuffers[offset + i] = out[i].toInt()
        }
    }

    actual fun glGenRenderbuffers(n: Int, renderbuffers: IntArray, offset: Int) {
        memScoped {
            val out = allocArray<UIntVar>(n)
            platform.gles3.glGenRenderbuffers(n, out)
            for (i in 0 until n) renderbuffers[offset + i] = out[i].toInt()
        }
    }

    actual fun glGenTextures(n: Int, textures: IntArray, offset: Int) {
        memScoped {
            val out = allocArray<UIntVar>(n)
            platform.gles3.glGenTextures(n, out)
            for (i in 0 until n) textures[offset + i] = out[i].toInt()
        }
    }

    actual fun glGenVertexArrays(n: Int, arrays: IntArray, offset: Int) {
        memScoped {
            val out = allocArray<UIntVar>(n)
            platform.gles3.glGenVertexArrays(n, out)
            for (i in 0 until n) arrays[offset + i] = out[i].toInt()
        }
    }

    actual fun glDeleteFramebuffers(n: Int, framebuffers: IntArray, offset: Int) {
        memScoped {
            val ids = allocArray<UIntVar>(n)
            for (i in 0 until n) ids[i] = framebuffers[offset + i].toUInt()
            platform.gles3.glDeleteFramebuffers(n, ids)
        }
    }

    actual fun glDeleteRenderbuffers(n: Int, renderbuffers: IntArray, offset: Int) {
        memScoped {
            val ids = allocArray<UIntVar>(n)
            for (i in 0 until n) ids[i] = renderbuffers[offset + i].toUInt()
            platform.gles3.glDeleteRenderbuffers(n, ids)
        }
    }

    actual fun glDeleteTextures(n: Int, textures: IntArray, offset: Int) {
        memScoped {
            val ids = allocArray<UIntVar>(n)
            for (i in 0 until n) ids[i] = textures[offset + i].toUInt()
            platform.gles3.glDeleteTextures(n, ids)
        }
    }

    actual fun glGetProgramiv(program: Int, pname: Int, params: IntArray, offset: Int) {
        memScoped {
            val out = alloc<IntVar>()
            platform.gles3.glGetProgramiv(program.toUInt(), pname.toUInt(), out.ptr)
            params[offset] = out.value
        }
    }

    actual fun glGetShaderiv(shader: Int, pname: Int, params: IntArray, offset: Int) {
        memScoped {
            val out = alloc<IntVar>()
            platform.gles3.glGetShaderiv(shader.toUInt(), pname.toUInt(), out.ptr)
            params[offset] = out.value
        }
    }

    actual fun glUniform1fv(location: Int, count: Int, v: FloatArray, offset: Int) {
        v.usePinned { pinned -> platform.gles3.glUniform1fv(location, count, pinned.addressOf(offset)) }
    }

    actual fun glUniform3fv(location: Int, count: Int, v: FloatArray, offset: Int) {
        v.usePinned { pinned -> platform.gles3.glUniform3fv(location, count, pinned.addressOf(offset)) }
    }

    actual fun glUniformMatrix4fv(location: Int, count: Int, transpose: Boolean, value: FloatArray, offset: Int) {
        value.usePinned { pinned ->
            platform.gles3.glUniformMatrix4fv(location, count, if (transpose) 1u else 0u, pinned.addressOf(offset))
        }
    }

    actual fun glBufferData(target: Int, size: Int, data: GlFloatBuffer?, usage: Int) {
        platform.gles3.glBufferData(target.toUInt(), size.convert(), data?.pointer, usage.toUInt())
    }

    actual fun glBufferSubData(target: Int, offset: Int, size: Int, data: GlFloatBuffer) {
        platform.gles3.glBufferSubData(target.toUInt(), offset.convert(), size.convert(), data.pointer)
    }

    actual fun glVertexAttribPointer(index: Int, size: Int, type: Int, normalized: Boolean, stride: Int, ptr: GlFloatBuffer) {
        platform.gles3.glVertexAttribPointer(index.toUInt(), size, type.toUInt(), if (normalized) 1u else 0u, stride, ptr.pointer)
    }

    actual fun glVertexAttribPointerOffset(index: Int, size: Int, type: Int, normalized: Boolean, stride: Int, offset: Int) {
        // A null client pointer means "read from the bound buffer at this byte offset".
        platform.gles3.glVertexAttribPointer(index.toUInt(), size, type.toUInt(), if (normalized) 1u else 0u, stride, offset.toLong().toCPointer<ByteVar>())
    }

    private fun iosShaderSource(shader: Int, source: String) = memScoped {
        val ptr = allocArray<CPointerVar<ByteVar>>(1)
        ptr[0] = source.cstr.ptr
        platform.gles3.glShaderSource(shader.toUInt(), 1, ptr, null)
    }

    private fun iosDrawElements(mode: Int, count: Int, type: Int, offset: Int) {
        platform.gles3.glDrawElements(mode.toUInt(), count, type.toUInt(), offset.toLong().toCPointer<ByteVar>())
    }

    private fun iosGetString(name: Int): String? =
        platform.gles3.glGetString(name.toUInt())?.reinterpret<ByteVar>()?.toKString()

    private fun iosInfoLog(obj: Int, program: Boolean): String = memScoped {
        val len = alloc<IntVar>()
        if (program) platform.gles3.glGetProgramiv(obj.toUInt(), platform.gles3.GL_INFO_LOG_LENGTH.toUInt(), len.ptr)
        else platform.gles3.glGetShaderiv(obj.toUInt(), platform.gles3.GL_INFO_LOG_LENGTH.toUInt(), len.ptr)
        if (len.value <= 0) return@memScoped ""
        val buf = allocArray<ByteVar>(len.value)
        if (program) platform.gles3.glGetProgramInfoLog(obj.toUInt(), len.value, null, buf)
        else platform.gles3.glGetShaderInfoLog(obj.toUInt(), len.value, null, buf)
        buf.toKString()
    }
    actual fun glTexImage2DRgba(width: Int, height: Int, pixels: ByteArray) {
        pixels.usePinned { pinned ->
            platform.gles3.glTexImage2D(
                platform.gles3.GL_TEXTURE_2D.toUInt(), 0,
                platform.gles3.GL_RGBA.toInt(), width, height, 0,
                platform.gles3.GL_RGBA.toUInt(), platform.gles3.GL_UNSIGNED_BYTE.toUInt(),
                pinned.addressOf(0),
            )
        }
    }

    actual fun glTexImage2DEmpty(internalFormat: Int, width: Int, height: Int, format: Int, type: Int) {
        platform.gles3.glTexImage2D(
            platform.gles3.GL_TEXTURE_2D.toUInt(), 0, internalFormat, width, height, 0,
            format.toUInt(), type.toUInt(), null,
        )
    }

    actual fun glBufferDataInts(target: Int, data: IntArray, usage: Int) {
        data.usePinned { pinned ->
            platform.gles3.glBufferData(
                target.toUInt(), (data.size * 4).convert(), pinned.addressOf(0), usage.toUInt(),
            )
        }
    }

}
