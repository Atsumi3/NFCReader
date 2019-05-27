@file:Suppress("UNCHECKED_CAST")

package info.nukoneko.android.nfcreader.model.event

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer

//https://medium.com/androiddevelopers/livedata-with-snackbar-navigation-and-other-events-the-singleliveevent-case-ac2622673150
// Event
class VMEvent<out T : Any?>(private val content: T = Any() as T) {
    var hasBeenHandled = false
        private set // Allow external read but not write

    /**
     * Returns the content and prevents its use again.
     */
    fun getContentIfNotHandled(): T? {
        return if (hasBeenHandled) {
            null
        } else {
            hasBeenHandled = true
            content
        }
    }

    /**
     * Returns the content, even if it's already been handled.
     */
    fun peekContent(): T = content
}

inline fun <T : LiveData<VMEvent<E>>?, E : Any?, R> T.safetyObserve(
        owner: LifecycleOwner,
        crossinline block: (E) -> R
) {
    try {
        this?.observe(owner, Observer<VMEvent<E>> { event ->
            event?.getContentIfNotHandled()?.let {
                block(it)
            }
        })
    } catch (e: Throwable) {
        e.printStackTrace()
    } finally {

    }
}

fun <T : MutableLiveData<VMEvent<E>>?, E : Any?> T.postValue(value: E) {
    try {
        this?.postValue(VMEvent(value))
    } catch (e: Throwable) {
        e.printStackTrace()
    } finally {

    }
}