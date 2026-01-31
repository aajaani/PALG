package com.palg.error


/**
 * transforms raw error messages to categories
 * having categories helps us compare and analyze errors, since
 * raw messages can have context sensitive info (like different variable names or whatever)
 * we just want error category
 */

object ErrorCategoryNormalizer {

    private val COMPILE_ERROR_PATTERNS: List<Pair<Regex, String>> = listOf(

        // student references something that doesnt exist
        Regex("""cannot find symbol.*variable""", RegexOption.IGNORE_CASE)
                to "cannot_find_symbol_variable",
        Regex("""cannot find symbol.*method""", RegexOption.IGNORE_CASE)
                to "cannot_find_symbol_method",
        Regex("""cannot find symbol.*class""", RegexOption.IGNORE_CASE)
                to "cannot_find_symbol_class",
        Regex("""cannot find symbol""", RegexOption.IGNORE_CASE)
                to "cannot_find_symbol",

        // missing semicolons, parentheses, bracess
        Regex("""';' expected""", RegexOption.IGNORE_CASE)
                to "semicolon_expected",
        Regex("""'\)' expected""", RegexOption.IGNORE_CASE)
                to "close_paren_expected",
        Regex("""'\(' expected""", RegexOption.IGNORE_CASE)
                to "open_paren_expected",
        Regex("""'\}' expected""", RegexOption.IGNORE_CASE)
                to "close_brace_expected",
        Regex("""'\{' expected""", RegexOption.IGNORE_CASE)
                to "open_brace_expected",
        Regex("""'\]' expected""", RegexOption.IGNORE_CASE)
                to "close_bracket_expected",
        Regex("""'\[' expected""", RegexOption.IGNORE_CASE)
                to "open_bracket_expected",
        Regex("""expected""", RegexOption.IGNORE_CASE)
                to "token_expected",

        // type errors
        Regex("""incompatible types""", RegexOption.IGNORE_CASE)
                to "incompatible_types",
        Regex("""cannot be converted to""", RegexOption.IGNORE_CASE)
                to "incompatible_types",
        Regex("""inconvertible types""", RegexOption.IGNORE_CASE)
                to "incompatible_types",
        Regex("""possible loss of precision""", RegexOption.IGNORE_CASE)
                to "precision_loss",
        Regex("""possible lossy conversion""", RegexOption.IGNORE_CASE)
                to "precision_loss",

        // expression errors
        Regex("""illegal start of expression""", RegexOption.IGNORE_CASE)
                to "illegal_start_of_expression",
        Regex("""illegal start of type""", RegexOption.IGNORE_CASE)
                to "illegal_start_of_type",
        Regex("""not a statement""", RegexOption.IGNORE_CASE)
                to "not_a_statement",

        // return errors
        Regex("""missing return statement""", RegexOption.IGNORE_CASE)
                to "missing_return",
        Regex("""missing return value""", RegexOption.IGNORE_CASE)
                to "missing_return",
        Regex("""cannot return a value from method whose result type is void""", RegexOption.IGNORE_CASE)
                to "void_return_value",
        Regex("""unreachable statement""", RegexOption.IGNORE_CASE)
                to "unreachable_statement",

        // class/interface errors
        Regex("""class.*is public.*should be declared in a file named""", RegexOption.IGNORE_CASE)
                to "class_file_name_mismatch",
        Regex("""class, interface, or enum expected""", RegexOption.IGNORE_CASE)
                to "class_interface_expected",
        Regex("""reached end of file while parsing""", RegexOption.IGNORE_CASE)
                to "unclosed_block",

        // identifier errors
        Regex("""<identifier> expected""", RegexOption.IGNORE_CASE)
                to "identifier_expected",
        Regex("""identifier expected""", RegexOption.IGNORE_CASE)
                to "identifier_expected",

        // if/else errors
        Regex("""'else' without 'if'""", RegexOption.IGNORE_CASE)
                to "else_without_if",
        Regex("""break outside switch or loop""", RegexOption.IGNORE_CASE)
                to "break_outside_loop",
        Regex("""continue outside of loop""", RegexOption.IGNORE_CASE)
                to "continue_outside_loop",

        // variable errors
        Regex("""variable.*might not have been initialized""", RegexOption.IGNORE_CASE)
                to "uninitialized_variable",
        Regex("""variable.*is already defined""", RegexOption.IGNORE_CASE)
                to "duplicate_variable",
        Regex(""".*is already defined in.*""", RegexOption.IGNORE_CASE)
                to "duplicate_definition",

        // method errors
        Regex("""method.*in class.*cannot be applied to given types""", RegexOption.IGNORE_CASE)
                to "method_arguments_mismatch",
        Regex("""cannot be applied to""", RegexOption.IGNORE_CASE)
                to "method_arguments_mismatch",
        Regex("""non-static method.*cannot be referenced from a static context""", RegexOption.IGNORE_CASE)
                to "static_context_error",
        Regex("""non-static variable.*cannot be referenced from a static context""", RegexOption.IGNORE_CASE)
                to "static_context_error",

        // access errors
        Regex(""".*has private access in.*""", RegexOption.IGNORE_CASE)
                to "private_access",
        Regex(""".*has protected access in.*""", RegexOption.IGNORE_CASE)
                to "protected_access",

        // operator errors
        Regex("""bad operand type.*for unary operator""", RegexOption.IGNORE_CASE)
                to "bad_operand_unary",
        Regex("""bad operand types for binary operator""", RegexOption.IGNORE_CASE)
                to "bad_operand_binary",

        // array errors
        Regex("""array required, but.*found""", RegexOption.IGNORE_CASE)
                to "array_required",

        // constructor errors
        Regex("""constructor.*in class.*cannot be applied to given types""", RegexOption.IGNORE_CASE)
                to "constructor_arguments_mismatch",
        Regex(""".*no suitable constructor found""", RegexOption.IGNORE_CASE)
                to "no_suitable_constructor",

        // exception handling error
        Regex("""exception.*is never thrown""", RegexOption.IGNORE_CASE)
                to "exception_never_thrown",
        Regex("""unreported exception.*must be caught or declared to be thrown""", RegexOption.IGNORE_CASE)
                to "unreported_exception"
    )

    // iterates through patterns, returns first pattern that matches to normalize it
    fun normalize(rawMessage: String): String {
        val trimmed = rawMessage.trim()

        for ((pattern, category) in COMPILE_ERROR_PATTERNS) {
            if (pattern.containsMatchIn(trimmed)) {
                return category
            }
        }

        return "other"
    }


    // for runtime errors, the exception class is a good category already, so we just keep that, for example
    // "java.lang.NullPointerException" become NullPointerException
    fun normalizeException(exceptionClass: String): String {
        val lastDot = exceptionClass.lastIndexOf('.')
        return if (lastDot >= 0) {
            exceptionClass.substring(lastDot + 1)
        } else {
            exceptionClass
        }
    }
}
