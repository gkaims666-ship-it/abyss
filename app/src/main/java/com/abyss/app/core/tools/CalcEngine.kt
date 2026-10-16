package com.abyss.app.core.tools

import kotlin.math.E
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * A small, dependency-free expression evaluator for the calculator.
 * Supports + - * / %, ^ (power), unary minus, parentheses, the functions
 * sin cos tan ln log sqrt abs, and the constants pi/π and e. Trig is in radians.
 */
object CalcEngine {

    class CalcError(message: String) : Exception(message)

    fun evaluate(input: String): Double {
        val p = Parser(input)
        val v = p.parseExpression()
        p.expectEnd()
        if (v.isNaN() || v.isInfinite()) throw CalcError("Math error")
        return v
    }

    /** Format a result without trailing ".0" and with reasonable precision. */
    fun format(value: Double): String {
        if (value == value.toLong().toDouble() && abs(value) < 1e15)
            return value.toLong().toString()
        return "%.10g".format(value).trimEnd('0').trimEnd('.')
    }

    private class Parser(text: String) {
        private val s = text.replace("×", "*").replace("÷", "/")
            .replace("−", "-").replace("π", "pi").replace("√", "sqrt")
        private var pos = 0

        private fun peek(): Char? = s.getOrNull(pos)
        private fun skipWs() { while (peek()?.isWhitespace() == true) pos++ }

        fun expectEnd() { skipWs(); if (pos != s.length) throw CalcError("Unexpected '${peek()}'") }

        fun parseExpression(): Double {
            var v = parseTerm()
            while (true) {
                skipWs()
                when (peek()) {
                    '+' -> { pos++; v += parseTerm() }
                    '-' -> { pos++; v -= parseTerm() }
                    else -> return v
                }
            }
        }

        private fun parseTerm(): Double {
            var v = parsePower()
            while (true) {
                skipWs()
                when (peek()) {
                    '*' -> { pos++; v *= parsePower() }
                    '/' -> { pos++; val d = parsePower(); if (d == 0.0) throw CalcError("Divide by zero"); v /= d }
                    '%' -> { pos++; v %= parsePower() }
                    else -> return v
                }
            }
        }

        private fun parsePower(): Double {
            val base = parseUnary()
            skipWs()
            return if (peek() == '^') { pos++; base.pow(parsePower()) } else base
        }

        private fun parseUnary(): Double {
            skipWs()
            return when (peek()) {
                '-' -> { pos++; -parseUnary() }
                '+' -> { pos++; parseUnary() }
                else -> parsePrimary()
            }
        }

        private fun parsePrimary(): Double {
            skipWs()
            val c = peek() ?: throw CalcError("Unexpected end")
            if (c == '(') { pos++; val v = parseExpression(); skipWs()
                if (peek() != ')') throw CalcError("Missing ')'"); pos++; return v }
            if (c.isDigit() || c == '.') return parseNumber()
            if (c.isLetter()) return parseIdentifier()
            throw CalcError("Unexpected '$c'")
        }

        private fun parseNumber(): Double {
            val start = pos
            while (peek()?.let { it.isDigit() || it == '.' } == true) pos++
            // exponent form 1e3
            if (peek() == 'e' || peek() == 'E') {
                val save = pos; pos++
                if (peek() == '+' || peek() == '-') pos++
                if (peek()?.isDigit() == true) { while (peek()?.isDigit() == true) pos++ }
                else pos = save
            }
            return s.substring(start, pos).toDoubleOrNull() ?: throw CalcError("Bad number")
        }

        private fun parseIdentifier(): Double {
            val start = pos
            while (peek()?.isLetter() == true) pos++
            val name = s.substring(start, pos).lowercase()
            when (name) {
                "pi" -> return PI
                "e" -> return E
            }
            // function call
            skipWs()
            if (peek() != '(') throw CalcError("Unknown '$name'")
            pos++
            val arg = parseExpression(); skipWs()
            if (peek() != ')') throw CalcError("Missing ')'"); pos++
            return when (name) {
                "sin" -> sin(arg); "cos" -> cos(arg); "tan" -> tan(arg)
                "ln" -> ln(arg); "log" -> log10(arg)
                "sqrt" -> sqrt(arg); "abs" -> abs(arg)
                else -> throw CalcError("Unknown function '$name'")
            }
        }
    }
}
