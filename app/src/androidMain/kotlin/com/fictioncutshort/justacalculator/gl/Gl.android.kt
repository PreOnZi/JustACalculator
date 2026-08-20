package com.fictioncutshort.justacalculator.gl

import android.opengl.GLES30

/** Straight delegation; GLES30 exposes the GLES20 surface too. */
actual object Gl {

    actual val GL_ARRAY_BUFFER: Int = GLES30.GL_ARRAY_BUFFER
    actual val GL_BACK: Int = GLES30.GL_BACK
    actual val GL_BLEND: Int = GLES30.GL_BLEND
    actual val GL_CLAMP_TO_EDGE: Int = GLES30.GL_CLAMP_TO_EDGE
    actual val GL_COLOR_ATTACHMENT0: Int = GLES30.GL_COLOR_ATTACHMENT0
    actual val GL_COLOR_BUFFER_BIT: Int = GLES30.GL_COLOR_BUFFER_BIT
    actual val GL_COMPILE_STATUS: Int = GLES30.GL_COMPILE_STATUS
    actual val GL_CULL_FACE: Int = GLES30.GL_CULL_FACE
    actual val GL_DEPTH_ATTACHMENT: Int = GLES30.GL_DEPTH_ATTACHMENT
    actual val GL_DEPTH_BUFFER_BIT: Int = GLES30.GL_DEPTH_BUFFER_BIT
    actual val GL_DEPTH_COMPONENT16: Int = GLES30.GL_DEPTH_COMPONENT16
    actual val GL_DEPTH_TEST: Int = GLES30.GL_DEPTH_TEST
    actual val GL_DYNAMIC_DRAW: Int = GLES30.GL_DYNAMIC_DRAW
    actual val GL_ELEMENT_ARRAY_BUFFER: Int = GLES30.GL_ELEMENT_ARRAY_BUFFER
    actual val GL_EXTENSIONS: Int = GLES30.GL_EXTENSIONS
    actual val GL_FLOAT: Int = GLES30.GL_FLOAT
    actual val GL_FRAGMENT_SHADER: Int = GLES30.GL_FRAGMENT_SHADER
    actual val GL_FRAMEBUFFER: Int = GLES30.GL_FRAMEBUFFER
    actual val GL_FRAMEBUFFER_BINDING: Int = GLES30.GL_FRAMEBUFFER_BINDING
    actual val GL_FRAMEBUFFER_COMPLETE: Int = GLES30.GL_FRAMEBUFFER_COMPLETE
    actual val GL_LEQUAL: Int = GLES30.GL_LEQUAL
    actual val GL_LESS: Int = GLES30.GL_LESS
    actual val GL_LINEAR: Int = GLES30.GL_LINEAR
    actual val GL_LINES: Int = GLES30.GL_LINES
    actual val GL_LINK_STATUS: Int = GLES30.GL_LINK_STATUS
    actual val GL_ONE: Int = GLES30.GL_ONE
    actual val GL_ONE_MINUS_SRC_ALPHA: Int = GLES30.GL_ONE_MINUS_SRC_ALPHA
    actual val GL_RENDERBUFFER: Int = GLES30.GL_RENDERBUFFER
    actual val GL_RGBA8: Int = GLES30.GL_RGBA8
    actual val GL_RGB: Int = GLES30.GL_RGB
    actual val GL_RGBA: Int = GLES30.GL_RGBA
    actual val GL_SCISSOR_TEST: Int = GLES30.GL_SCISSOR_TEST
    actual val GL_SRC_ALPHA: Int = GLES30.GL_SRC_ALPHA
    actual val GL_STATIC_DRAW: Int = GLES30.GL_STATIC_DRAW
    actual val GL_TEXTURE0: Int = GLES30.GL_TEXTURE0
    actual val GL_TEXTURE_2D: Int = GLES30.GL_TEXTURE_2D
    actual val GL_TEXTURE_MAG_FILTER: Int = GLES30.GL_TEXTURE_MAG_FILTER
    actual val GL_TEXTURE_MIN_FILTER: Int = GLES30.GL_TEXTURE_MIN_FILTER
    actual val GL_TEXTURE_WRAP_S: Int = GLES30.GL_TEXTURE_WRAP_S
    actual val GL_TEXTURE_WRAP_T: Int = GLES30.GL_TEXTURE_WRAP_T
    actual val GL_TRIANGLES: Int = GLES30.GL_TRIANGLES
    actual val GL_TRIANGLE_FAN: Int = GLES30.GL_TRIANGLE_FAN
    actual val GL_UNIFORM_BUFFER: Int = GLES30.GL_UNIFORM_BUFFER
    actual val GL_UNSIGNED_BYTE: Int = GLES30.GL_UNSIGNED_BYTE
    actual val GL_UNSIGNED_INT: Int = GLES30.GL_UNSIGNED_INT
    actual val GL_VERTEX_SHADER: Int = GLES30.GL_VERTEX_SHADER

    actual fun glActiveTexture(texture: Int) = GLES30.glActiveTexture(texture)
    actual fun glAttachShader(program: Int, shader: Int) = GLES30.glAttachShader(program, shader)
    actual fun glBindBuffer(target: Int, buffer: Int) = GLES30.glBindBuffer(target, buffer)
    actual fun glBindBufferBase(target: Int, index: Int, buffer: Int) = GLES30.glBindBufferBase(target, index, buffer)
    actual fun glBindFramebuffer(target: Int, framebuffer: Int) = GLES30.glBindFramebuffer(target, framebuffer)
    actual fun glGetIntegerv(pname: Int, out: IntArray, offset: Int) = GLES30.glGetIntegerv(pname, out, offset)
    actual fun glBindRenderbuffer(target: Int, renderbuffer: Int) = GLES30.glBindRenderbuffer(target, renderbuffer)
    actual fun glBindTexture(target: Int, texture: Int) = GLES30.glBindTexture(target, texture)
    actual fun glBindVertexArray(array: Int) = GLES30.glBindVertexArray(array)
    actual fun glBlendFunc(sfactor: Int, dfactor: Int) = GLES30.glBlendFunc(sfactor, dfactor)
    actual fun glCheckFramebufferStatus(target: Int): Int = GLES30.glCheckFramebufferStatus(target)
    actual fun glClear(mask: Int) = GLES30.glClear(mask)
    actual fun glClearColor(red: Float, green: Float, blue: Float, alpha: Float) = GLES30.glClearColor(red, green, blue, alpha)
    actual fun glCompileShader(shader: Int) = GLES30.glCompileShader(shader)
    actual fun glCreateProgram(): Int = GLES30.glCreateProgram()
    actual fun glCreateShader(type: Int): Int = GLES30.glCreateShader(type)
    actual fun glCullFace(mode: Int) = GLES30.glCullFace(mode)
    actual fun glDeleteProgram(program: Int) = GLES30.glDeleteProgram(program)
    actual fun glDeleteShader(shader: Int) = GLES30.glDeleteShader(shader)
    actual fun glDepthFunc(func: Int) = GLES30.glDepthFunc(func)
    actual fun glDepthMask(flag: Boolean) = GLES30.glDepthMask(flag)
    actual fun glDisable(cap: Int) = GLES30.glDisable(cap)
    actual fun glDisableVertexAttribArray(index: Int) = GLES30.glDisableVertexAttribArray(index)
    actual fun glDrawArrays(mode: Int, first: Int, count: Int) = GLES30.glDrawArrays(mode, first, count)
    actual fun glEnable(cap: Int) = GLES30.glEnable(cap)
    actual fun glEnableVertexAttribArray(index: Int) = GLES30.glEnableVertexAttribArray(index)
    actual fun glFramebufferRenderbuffer(target: Int, attachment: Int, rbTarget: Int, renderbuffer: Int) = GLES30.glFramebufferRenderbuffer(target, attachment, rbTarget, renderbuffer)
    actual fun glFramebufferTexture2D(target: Int, attachment: Int, texTarget: Int, texture: Int, level: Int) = GLES30.glFramebufferTexture2D(target, attachment, texTarget, texture, level)
    actual fun glGetAttribLocation(program: Int, name: String): Int = GLES30.glGetAttribLocation(program, name)
    actual fun glGetProgramInfoLog(program: Int): String = GLES30.glGetProgramInfoLog(program)
    actual fun glGetShaderInfoLog(shader: Int): String = GLES30.glGetShaderInfoLog(shader)
    actual fun glGetUniformBlockIndex(program: Int, name: String): Int = GLES30.glGetUniformBlockIndex(program, name)
    actual fun glGetUniformLocation(program: Int, name: String): Int = GLES30.glGetUniformLocation(program, name)
    actual fun glLinkProgram(program: Int) = GLES30.glLinkProgram(program)
    actual fun glRenderbufferStorage(target: Int, internalformat: Int, width: Int, height: Int) = GLES30.glRenderbufferStorage(target, internalformat, width, height)
    actual fun glScissor(x: Int, y: Int, width: Int, height: Int) = GLES30.glScissor(x, y, width, height)
    actual fun glReadPixels(
        x: Int, y: Int, width: Int, height: Int, format: Int, type: Int, pixels: ByteArray,
    ) {
        val buffer = java.nio.ByteBuffer.allocateDirect(pixels.size)
            .order(java.nio.ByteOrder.nativeOrder())
        GLES30.glReadPixels(x, y, width, height, format, type, buffer)
        buffer.rewind()
        buffer.get(pixels)
    }

    actual fun glShaderSource(shader: Int, source: String) = GLES30.glShaderSource(shader, source)
    actual fun glTexParameteri(target: Int, pname: Int, param: Int) = GLES30.glTexParameteri(target, pname, param)
    actual fun glUniform1f(location: Int, x: Float) = GLES30.glUniform1f(location, x)
    actual fun glUniform1i(location: Int, x: Int) = GLES30.glUniform1i(location, x)
    actual fun glUniform3f(location: Int, x: Float, y: Float, z: Float) = GLES30.glUniform3f(location, x, y, z)
    actual fun glUniform4f(location: Int, x: Float, y: Float, z: Float, w: Float) = GLES30.glUniform4f(location, x, y, z, w)
    actual fun glUniformBlockBinding(program: Int, index: Int, binding: Int) = GLES30.glUniformBlockBinding(program, index, binding)
    actual fun glUseProgram(program: Int) = GLES30.glUseProgram(program)
    actual fun glViewport(x: Int, y: Int, width: Int, height: Int) = GLES30.glViewport(x, y, width, height)
    actual fun glDrawElements(mode: Int, count: Int, type: Int, offset: Int) = GLES30.glDrawElements(mode, count, type, offset)
    actual fun glGetString(name: Int): String? = GLES30.glGetString(name)
    actual fun glGenBuffers(n: Int, buffers: IntArray, offset: Int) = GLES30.glGenBuffers(n, buffers, offset)
    actual fun glGenFramebuffers(n: Int, framebuffers: IntArray, offset: Int) = GLES30.glGenFramebuffers(n, framebuffers, offset)
    actual fun glGenRenderbuffers(n: Int, renderbuffers: IntArray, offset: Int) = GLES30.glGenRenderbuffers(n, renderbuffers, offset)
    actual fun glGenTextures(n: Int, textures: IntArray, offset: Int) = GLES30.glGenTextures(n, textures, offset)
    actual fun glGenVertexArrays(n: Int, arrays: IntArray, offset: Int) = GLES30.glGenVertexArrays(n, arrays, offset)
    actual fun glDeleteBuffers(n: Int, buffers: IntArray, offset: Int) =
        GLES30.glDeleteBuffers(n, buffers, offset)

    actual fun glDeleteFramebuffers(n: Int, framebuffers: IntArray, offset: Int) = GLES30.glDeleteFramebuffers(n, framebuffers, offset)
    actual fun glDeleteRenderbuffers(n: Int, renderbuffers: IntArray, offset: Int) = GLES30.glDeleteRenderbuffers(n, renderbuffers, offset)
    actual fun glDeleteTextures(n: Int, textures: IntArray, offset: Int) = GLES30.glDeleteTextures(n, textures, offset)
    actual fun glGetProgramiv(program: Int, pname: Int, params: IntArray, offset: Int) = GLES30.glGetProgramiv(program, pname, params, offset)
    actual fun glGetShaderiv(shader: Int, pname: Int, params: IntArray, offset: Int) = GLES30.glGetShaderiv(shader, pname, params, offset)
    actual fun glUniform1fv(location: Int, count: Int, v: FloatArray, offset: Int) = GLES30.glUniform1fv(location, count, v, offset)
    actual fun glUniform3fv(location: Int, count: Int, v: FloatArray, offset: Int) = GLES30.glUniform3fv(location, count, v, offset)
    actual fun glUniformMatrix4fv(location: Int, count: Int, transpose: Boolean, value: FloatArray, offset: Int) = GLES30.glUniformMatrix4fv(location, count, transpose, value, offset)

    actual fun glBufferData(target: Int, size: Int, data: GlFloatBuffer?, usage: Int) =
        GLES30.glBufferData(target, size, data?.nio, usage)

    actual fun glBufferSubData(target: Int, offset: Int, size: Int, data: GlFloatBuffer) =
        GLES30.glBufferSubData(target, offset, size, data.nio)

    actual fun glVertexAttribPointer(index: Int, size: Int, type: Int, normalized: Boolean, stride: Int, ptr: GlFloatBuffer) =
        GLES30.glVertexAttribPointer(index, size, type, normalized, stride, ptr.nio)

    actual fun glVertexAttribPointerOffset(index: Int, size: Int, type: Int, normalized: Boolean, stride: Int, offset: Int) =
        GLES30.glVertexAttribPointer(index, size, type, normalized, stride, offset)
    actual fun glTexImage2DRgba(width: Int, height: Int, pixels: ByteArray) {
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, width, height, 0,
            GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE,
            java.nio.ByteBuffer.wrap(pixels),
        )
    }

    actual fun glTexImage2DEmpty(internalFormat: Int, width: Int, height: Int, format: Int, type: Int) {
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D, 0, internalFormat, width, height, 0, format, type, null,
        )
    }

    actual fun glBufferDataInts(target: Int, data: IntArray, usage: Int) {
        val buf = java.nio.ByteBuffer.allocateDirect(data.size * 4)
            .order(java.nio.ByteOrder.nativeOrder())
            .asIntBuffer()
            .apply { put(data); position(0) }
        GLES30.glBufferData(target, data.size * 4, buf, usage)
    }

}
