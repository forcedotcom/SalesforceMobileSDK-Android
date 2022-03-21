package com.salesforce.samples.mobilesynccompose.core.salesforceobject

import org.json.JSONObject

sealed class CoerceException(message: String?) : Exception(message) {
    abstract val offendingJson: JSONObject
}

data class IncorrectObjectType(
    val expectedObjectType: String,
    val foundObjectType: String,
    override val offendingJson: JSONObject,
) : CoerceException(
    buildString {
        appendLine("CoerceException - IncorrectObjectType")
        appendLine("This JSON had the incorrect object type. Expected $expectedObjectType but found $foundObjectType")
        appendLine("Offending JSON = '$offendingJson'")
    }
)

data class InvalidJsonString(
    override val offendingJson: JSONObject
) : CoerceException(
    buildString{
        appendLine("Coerce Exception - InvalidJsonString")
        appendLine("Supplied JSON was not valid.")
        appendLine("Offending JSON = '$offendingJson'")
    }
)

data class InvalidPropertyValue(
    val propertyKey: String,
    val allowedValuesDescription: String,
    override val offendingJson: JSONObject
) : CoerceException(
    buildString {
        appendLine("CoerceException - InvalidPropertyValue")
        appendLine("This JSON had an invalid value for key $propertyKey.")
        appendLine(allowedValuesDescription)
        appendLine("Offending JSON = '$offendingJson'")
    }
)

data class MissingRequiredProperty(
    val propertyKey: String,
    override val offendingJson: JSONObject,
) : CoerceException(
    buildString {
        appendLine("CoerceException - MissingRequiredProperty")
        appendLine("This JSON was missing the required property: '$propertyKey'")
        appendLine("Offending JSON = '$offendingJson'")
    }
)
