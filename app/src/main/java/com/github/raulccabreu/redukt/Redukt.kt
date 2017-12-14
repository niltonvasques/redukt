package com.github.raulccabreu.redukt

import com.github.raulccabreu.redukt.actions.Action
import com.github.raulccabreu.redukt.middlewares.Middleware
import com.github.raulccabreu.redukt.reducers.Reducer
import com.github.raulccabreu.redukt.states.StateListener
import kotlin.system.measureTimeMillis

class Redukt<T>(state: T) {
    var state = state
        private set
    val reducers = mutableSetOf<Reducer<T>>()
    val middlewares = mutableSetOf<Middleware<T>>()
    val listeners = mutableSetOf<StateListener<T>>()
    var traceActionProfile = false
    private val dispatcher = Dispatcher { reduce(it) }

    init { start() }

    fun dispatch(action: Action<*>, async: Boolean = true) {
        if (async) dispatcher.dispatch(action)
        else reduce(action)
    }

    private fun start() {
        dispatcher.start()
    }

    fun stop() {
        dispatcher.stop()
    }

    private fun reduce(action: Action<*>) {
        var elapsedBefore = 0L
        var elapsedAfter = 0L
        var elapsedListeners = 0L
        var elapsedReducer = 0L
        val elapsed = measureTimeMillis {
            val listeners = listeners.toSet() //to avoid concurrent modification exception
            val middlewares = middlewares.toSet()
            val oldState = state
            var tempState = state
            elapsedBefore = measureTimeMillis {
                middlewares.parallelFor { it.before(tempState, action) }
            }
            elapsedReducer = measureTimeMillis {
                reducers.forEach { tempState = it.reduce(tempState, action) }
                state = tempState
            }
            elapsedListeners = measureTimeMillis {
                listeners.parallelFor { notifyListeners(it, oldState) }
            }
            elapsedAfter = measureTimeMillis {
                middlewares.parallelFor { it.after(tempState, action) }
            }
        }
        val sum = elapsedBefore + elapsedReducer + elapsedListeners + elapsedAfter
        if (traceActionProfile) println("[Redukt] [$elapsed ms][${sum} ms]" +
                "[$elapsedBefore ms before][$elapsedReducer ms reducer]" +
                "[$elapsedListeners ms listeners][$elapsedAfter ms after] Action ${action.name}")
    }

    private fun notifyListeners(it: StateListener<T>, oldState: T) {
        if (it.hasChanged(state, oldState)) it.onChanged(state)
    }
}
