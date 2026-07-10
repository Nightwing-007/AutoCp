package com.github.pushpavel.autocp.submit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class CppHeaderExpanderTest {

    @TempDir
    lateinit var dir: Path

    @Test
    fun `inlines local includes and keeps system includes`() {
        Files.createDirectories(dir.resolve("lib"))
        Files.writeString(dir.resolve("lib/geo.hpp"), "#pragma once\nstruct Point {};\n")
        val main = dir.resolve("main.cpp")
        val source = "#include <vector>\n#include \"lib/geo.hpp\"\nint main() {}\n"

        val expanded = CppHeaderExpander.expandIfCpp(main, source)!!

        assertTrue(expanded.contains("#include <vector>"))
        assertTrue(expanded.contains("struct Point {};"))
        assertFalse(expanded.contains("#include \"lib/geo.hpp\""))
        assertFalse(expanded.contains("#pragma once"))
        assertTrue(expanded.contains("int main() {}"))
    }

    @Test
    fun `repeated and cyclic includes are expanded only once`() {
        Files.writeString(dir.resolve("a.hpp"), "#include \"b.hpp\"\nint a;\n")
        Files.writeString(dir.resolve("b.hpp"), "#include \"a.hpp\"\nint b;\n")
        val main = dir.resolve("main.cpp")
        val source = "#include \"a.hpp\"\n#include \"b.hpp\"\nint main() {}\n"

        val expanded = CppHeaderExpander.expandIfCpp(main, source)!!

        assertEquals(1, Regex("int a;").findAll(expanded).count())
        assertEquals(1, Regex("int b;").findAll(expanded).count())
    }

    @Test
    fun `missing local header keeps the include line`() {
        val main = dir.resolve("main.cpp")
        val source = "#include \"nope.hpp\"\nint main() {}\n"

        assertNull(CppHeaderExpander.expandIfCpp(main, source))
    }

    @Test
    fun `non cpp files are not expanded`() {
        Files.writeString(dir.resolve("mod.py"), "x = 1\n")
        val main = dir.resolve("main.py")

        assertNull(CppHeaderExpander.expandIfCpp(main, "#include \"mod.py\"\n"))
    }

    @Test
    fun `sources without local includes are left untouched`() {
        val main = dir.resolve("main.cpp")

        assertNull(CppHeaderExpander.expandIfCpp(main, "#include <bits/stdc++.h>\nint main() {}\n"))
    }
}
