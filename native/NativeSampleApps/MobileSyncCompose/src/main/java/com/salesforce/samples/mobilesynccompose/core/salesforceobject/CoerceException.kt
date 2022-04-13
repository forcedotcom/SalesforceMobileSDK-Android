package com.salesforce.samples.mobilesynccompose.core.salesforceobject

sealed class CoerceException(message: String?) : Exception(message) {
    abstract val offendingJsonString: String
}

data class IncorrectObjectType(
    val expectedObjectType: String,
    val foundObjectType: String,
    override val offendingJsonString: String,
) : CoerceException(
    buildString {
        appendLine("CoerceException - IncorrectObjectType")
        appendLine("This JSON had the incorrect object type. Expected $expectedObjectType but found $foundObjectType")
        appendLine("Offending JSON = '$offendingJsonString'")
    }
)

data class InvalidPropertyValue(
    val propertyKey: String,
    val allowedValuesDescription: String,
    override val offendingJsonString: String
) : CoerceException(
    buildString {
        appendLine("CoerceException - InvalidPropertyValue")
        appendLine("This JSON had an invalid value for key $propertyKey.")
        appendLine(allowedValuesDescription)
        appendLine("Offending JSON = '$offendingJsonString'")
    }
)

class MissingRequiredProperties(
    override val offendingJsonString: String,
    vararg val propertyKeys: String,
) : CoerceException(
    buildString {
        appendLine("CoerceException - MissingRequiredProperty")
        appendLine("This JSON was missing one or more of the required properties: $propertyKeys")
        appendLine("Offending JSON = '$offendingJsonString'")
    }
)
