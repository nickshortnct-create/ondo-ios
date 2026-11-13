package android.database

// AGP's mockable android.jar removes the platform constructors' bodies. Room's
// Android upsert adapter needs the native SQLite error message to distinguish a
// uniqueness conflict from other failures. Supply the normal exception behavior
// only on the host-test classpath; this class is never packaged in the app.
open class SQLException : RuntimeException {
	constructor() : super()
	constructor(error: String?) : super(error)
	constructor(error: String?, cause: Throwable?) : super(error, cause)
}
