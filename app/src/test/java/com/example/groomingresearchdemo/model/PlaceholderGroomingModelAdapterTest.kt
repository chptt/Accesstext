package com.example.groomingresearchdemo.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Covers spec test item 18: the placeholder model must not fabricate anything. */
class PlaceholderGroomingModelAdapterTest {

    @Test
    fun reportsModelNotIntegrated() {
        val prediction = PlaceholderGroomingModelAdapter.predict("any preprocessed input")
        assertTrue(prediction is ModelPrediction.NotIntegrated)
        assertEquals("Model not integrated", (prediction as ModelPrediction.NotIntegrated).message)
    }

    @Test
    fun isNeverReadyUntilARealModelIsWiredIn() {
        assertFalse(PlaceholderGroomingModelAdapter.isReady)
    }

    @Test
    fun makesNoRandomOrConfidentPrediction() {
        // Calling repeatedly must be deterministic and must never produce a Scored prediction.
        val a = PlaceholderGroomingModelAdapter.predict("input A")
        val b = PlaceholderGroomingModelAdapter.predict("input A")
        assertEquals(a, b)
        assertFalse(a is ModelPrediction.Scored)
    }
}
