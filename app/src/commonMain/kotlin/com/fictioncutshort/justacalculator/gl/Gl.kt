package com.fictioncutshort.justacalculator.gl

/**
 * The OpenGL ES calls the renderers use, as one shared surface.
 *
 * Signatures mirror `android.opengl.GLES20`/`GLES30` so the existing call sites
 * port by changing their prefix and nothing else — including the `Int` types.
 * iOS uses `UInt` for GL enums and handles, so the conversion lives entirely in
 * the iOS actual rather than leaking into the renderers.
 */
expect object Gl {

    val GL_ARRAY_BUFFER: Int
    val GL_BACK: Int
    val GL_BLEND: Int
    val GL_CLAMP_TO_EDGE: Int
    val GL_COLOR_ATTACHMENT0: Int
    val GL_COLOR_BUFFER_BIT: Int
    val GL_COMPILE_STATUS: Int
    val GL_CULL_FACE: Int
    val GL_DEPTH_ATTACHMENT: Int
    val GL_DEPTH_BUFFER_BIT: Int
    val GL_DEPTH_COMPONENT16: Int
    val GL_DEPTH_TEST: Int
    val GL_DYNAMIC_DRAW: Int
    val GL_ELEMENT_ARRAY_BUFFER: Int
    val GL_EXTENSIONS: Int
    val GL_FLOAT: Int
    val GL_FRAGMENT_SHADER: Int
    val GL_FRAMEBUFFER: Int
    val GL_FRAMEBUFFER_COMPLETE: Int
    val GL_LEQUAL: Int
    val GL_LESS: Int
    val GL_LINEAR: Int
    val GL_LINES: Int
    val GL_LINK_STATUS: Int
    val GL_ONE: Int
    val GL_ONE_MINUS_SRC_ALPHA: Int
    val GL_RENDERBUFFER: Int
    val GL_RGB: Int
    val GL_RGBA: Int
    val GL_SCISSOR_TEST: Int
    val GL_SRC_ALPHA: Int
    val GL_STATIC_DRAW: Int
    val GL_TEXTURE0: Int
    val GL_TEXTURE_2D: Int
    val GL_TEXTURE_MAG_FILTER: Int
    val GL_TEXTURE_MIN_FILTER: Int
    val GL_TEXTURE_WRAP_S: Int
    val GL_TEXTURE_WRAP_T: Int
    val GL_TRIANGLES: Int
    val GL_TRIANGLE_FAN: Int
    val GL_UNIFORM_BUFFER: Int
    val GL_UNSIGNED_BYTE: Int
    val GL_UNSIGNED_INT: Int
    val GL_VERTEX_SHADER: Int

    fun glActiveTexture(texture: Int)
    fun glAttachShader(program: Int, shader: Int)
    fun glBindBuffer(target: Int, buffer: Int)
    fun glBindBufferBase(target: Int, index: Int, buffer: Int)
    fun glBindFramebuffer(target: Int, framebuffer: Int)
    fun glBindRenderbuffer(target: Int, renderbuffer: Int)
    fun glBindTexture(target: Int, texture: Int)
    fun glBindVertexArray(array: Int)
    fun glBlendFunc(sfactor: Int, dfactor: Int)
    fun glCheckFramebufferStatus(target: Int): Int
    fun glClear(mask: Int)
    fun glClearColor(red: Float, green: Float, blue: Float, alpha: Float)
    fun glCompileShader(shader: Int)
    fun glCreateProgram(): Int
    fun glCreateShader(type: Int): Int
    fun glCullFace(mode: Int)
    fun glDeleteProgram(program: Int)
    fun glDeleteShader(shader: Int)
    fun glDepthFunc(func: Int)
    fun glDepthMask(flag: Boolean)
    fun glDisable(cap: Int)
    fun glDisableVertexAttribArray(index: Int)
    fun glDrawArrays(mode: Int, first: Int, count: Int)
    fun glEnable(cap: Int)
    fun glEnableVertexAttribArray(index: Int)
    fun glFramebufferRenderbuffer(target: Int, attachment: Int, rbTarget: Int, renderbuffer: Int)
    fun glFramebufferTexture2D(target: Int, attachment: Int, texTarget: Int, texture: Int, level: Int)
    fun glGetAttribLocation(program: Int, name: String): Int
    fun glGetProgramInfoLog(program: Int): String
    fun glGetShaderInfoLog(shader: Int): String
    fun glGetUniformBlockIndex(program: Int, name: String): Int
    fun glGetUniformLocation(program: Int, name: String): Int
    fun glLinkProgram(program: Int)
    fun glRenderbufferStorage(target: Int, internalformat: Int, width: Int, height: Int)
    fun glScissor(x: Int, y: Int, width: Int, height: Int)
    fun glShaderSource(shader: Int, source: String)
    fun glTexParameteri(target: Int, pname: Int, param: Int)
    fun glUniform1f(location: Int, x: Float)
    fun glUniform1i(location: Int, x: Int)
    fun glUniform3f(location: Int, x: Float, y: Float, z: Float)
    fun glUniform4f(location: Int, x: Float, y: Float, z: Float, w: Float)
    fun glUniformBlockBinding(program: Int, index: Int, binding: Int)
    fun glUseProgram(program: Int)
    fun glViewport(x: Int, y: Int, width: Int, height: Int)
    fun glDrawElements(mode: Int, count: Int, type: Int, offset: Int)
    fun glGetString(name: Int): String?
    fun glGenBuffers(n: Int, buffers: IntArray, offset: Int)
    fun glGenFramebuffers(n: Int, framebuffers: IntArray, offset: Int)
    fun glGenRenderbuffers(n: Int, renderbuffers: IntArray, offset: Int)
    fun glGenTextures(n: Int, textures: IntArray, offset: Int)
    fun glGenVertexArrays(n: Int, arrays: IntArray, offset: Int)
    fun glDeleteFramebuffers(n: Int, framebuffers: IntArray, offset: Int)
    fun glDeleteRenderbuffers(n: Int, renderbuffers: IntArray, offset: Int)
    fun glDeleteTextures(n: Int, textures: IntArray, offset: Int)
    fun glGetProgramiv(program: Int, pname: Int, params: IntArray, offset: Int)
    fun glGetShaderiv(shader: Int, pname: Int, params: IntArray, offset: Int)
    fun glUniform1fv(location: Int, count: Int, v: FloatArray, offset: Int)
    fun glUniform3fv(location: Int, count: Int, v: FloatArray, offset: Int)
    fun glUniformMatrix4fv(location: Int, count: Int, transpose: Boolean, value: FloatArray, offset: Int)

    /** Uploads vertex data. Pass null for [data] to allocate without filling. */
    fun glBufferData(target: Int, size: Int, data: GlFloatBuffer?, usage: Int)
    fun glBufferSubData(target: Int, offset: Int, size: Int, data: GlFloatBuffer)
    fun glVertexAttribPointer(index: Int, size: Int, type: Int, normalized: Boolean, stride: Int, ptr: GlFloatBuffer)

    /** Vertex attributes sourced from a bound buffer object rather than client memory. */
    fun glVertexAttribPointerOffset(index: Int, size: Int, type: Int, normalized: Boolean, stride: Int, offset: Int)
}
