package xyz.kkaii.steamed.lib

import kotlin.reflect.typeOf

class Option<T> constructor(
    private var inner: T?,
    private val expectedType: String?,
) {
    constructor(o: T?) : this(o, o?.let { it::class.qualifiedName })

    constructor() : this(null, null)

    fun some(some: T?) {
        this.inner = some
    }

    fun isSome(): Boolean {
        return this.inner != null
    }

    fun isNone(): Boolean {
        return !isSome()
    }

    fun unwrap(): T? {
        if (this.inner == null) {
            val type = expectedType ?: "the option's generic type"
            throw RuntimeException("unwrap called on a null value (expected $type)")
        }
        return this.inner
    }

    fun unwrapOr(`val`: T?): T? {
        return if (this.inner == null) `val` else this.inner
    }

    fun <R> map(transform: (T) -> R): Option<R> {
        val value = inner ?: return Option(null)
        return Option(transform(value))
    }

    fun <R> flatMap(transform: (T) -> Option<R>): Option<R> {
        val value = inner ?: return Option(null)
        return transform(value)
    }

    fun filter(predicate: (T) -> Boolean): Option<T> {
        val value = inner ?: return Option(null)
        return if (predicate(value)) this else Option(null)
    }

    fun ifSome(action: (T) -> Unit): Option<T> {
        inner?.let(action)
        return this
    }

    fun ifNone(action: () -> Unit): Option<T> {
        if (inner == null) action()
        return this
    }

    fun orElse(other: Option<T>): Option<T> {
        return if (isSome()) this else other
    }

    fun orElse(other: () -> Option<T>): Option<T> {
        return if (isSome()) this else other()
    }

    companion object {
        inline fun <reified T> some(some: T): Option<T> {
            return Option(some, typeOf<T>().toString())
        }

        inline fun <reified T> none(): Option<T> {
            return Option(null, typeOf<T>().toString())
        }
    }
}
