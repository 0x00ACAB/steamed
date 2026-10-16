package xyz.kkaii.steamed.lib

class Option<T> {
    private var inner: T? = null

    constructor(o: T?) {
        this.inner = o
    }

    constructor()

    fun some(some: T?) {
        this.inner = some
    }

    fun isSome(): Boolean {
        return this.inner != null
    }

    fun unwrap(): T? {
        if (this.inner == null) throw RuntimeException("unwrap called on a null value (was null expected <T>)")
        return this.inner
    }

    fun unwrapOr(`val`: T?): T? {
        return if (this.inner == null) `val` else this.inner
    }

    companion object {
        fun <T> some(some: T?): Option<T?> {
            return Option(some)
        }

        fun <T> none(): Option<T?> {
            return Option(null)
        }
    }
}
