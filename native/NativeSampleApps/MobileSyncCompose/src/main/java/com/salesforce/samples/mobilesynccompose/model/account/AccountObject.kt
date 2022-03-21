package com.salesforce.samples.mobilesynccompose.model.account

import com.salesforce.androidsdk.mobilesync.util.Constants
import com.salesforce.samples.mobilesynccompose.core.extensions.optStringOrNull
import com.salesforce.samples.mobilesynccompose.core.salesforceobject.*
import org.json.JSONObject

data class AccountObject(
    val name: String,
    private val coreSalesforceObject: CoreSalesforceObject,
) : ImmutableSo, CoreSalesforceObject by coreSalesforceObject {

    override fun buildSafeEltCopy(): JSONObject = coreSalesforceObject.buildSafeEltCopy().apply {
        put(Constants.NAME, name)
    }

    override val hasUnsavedChanges: Boolean by lazy {
        buildSafeEltCopy().optStringOrNull(Constants.NAME) != name
    }

    companion object : SalesforceObjectDeserializer<AccountObject> {
        const val OBJECT_TYPE = Constants.ACCOUNT

        @Throws(CoerceException::class)
        override fun coerceFromJsonOrThrow(json: JSONObject): AccountObject {
            val copier = JsonCopier(json)
            val safe = copier.buildCopy()

            val name = safe.getRequiredStringOrThrow(Constants.NAME)
            return AccountObject(
                name = name,
                coreSalesforceObject = CoreSalesforceObjectImpl(
                    copier,
                    objectType = OBJECT_TYPE
                )
            )
        }
    }
}
